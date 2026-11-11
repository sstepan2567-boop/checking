"""Execute only explicitly selected x86-64 ranges in virtual memory."""
from dataclasses import dataclass
import struct
from typing import Callable

from unicorn import (
    Uc, UcError, UC_ARCH_X86, UC_MODE_64, UC_HOOK_CODE, UC_HOOK_INTR,
    UC_HOOK_INSN, UC_HOOK_MEM_INVALID, UC_PROT_READ, UC_PROT_WRITE, UC_PROT_EXEC,
)
from unicorn.x86_const import (
    UC_X86_REG_RAX, UC_X86_REG_RCX, UC_X86_REG_RDX, UC_X86_REG_R8,
    UC_X86_REG_R9, UC_X86_REG_RSP, UC_X86_REG_RIP, UC_X86_INS_SYSCALL,
    UC_X86_INS_SYSENTER,
)

PAGE = 0x1000
STACK = 0x700000000000
STACK_SIZE = 0x10000
STOP = 0x700000020000
DATA = 0x700000100000
DATA_SIZE = 0x400000


class EmulationError(RuntimeError):
    pass


@dataclass(frozen=True)
class CallResult:
    value: int
    instructions: int
    modeled_calls: int


class Machine:
    def __init__(self, base: int, image: bytes, ranges: list[tuple[int, int]]):
        self.cpu = Uc(UC_ARCH_X86, UC_MODE_64)
        self.ranges = tuple(ranges)
        self.models: dict[int, Callable] = {}
        self.error = None
        self.returned = False
        self.instructions = 0
        self.modeled_calls = 0
        size = (len(image) + PAGE - 1) & -PAGE
        self.cpu.mem_map(base, size, UC_PROT_READ)
        self.cpu.mem_write(base, image)
        for start, end in self.ranges:
            if not base <= start < end <= base + len(image):
                raise ValueError("approved code range outside image")
            page = start & -PAGE
            self.cpu.mem_protect(page, ((end + PAGE - 1) & -PAGE) - page, UC_PROT_READ | UC_PROT_EXEC)
        self.cpu.mem_map(STACK, STACK_SIZE, UC_PROT_READ | UC_PROT_WRITE)
        self.cpu.mem_map(DATA, DATA_SIZE, UC_PROT_READ | UC_PROT_WRITE)
        self.cpu.mem_map(STOP, PAGE, UC_PROT_READ | UC_PROT_EXEC)
        self.cpu.mem_write(STOP, b"\xc3")
        self.cpu.hook_add(UC_HOOK_CODE, self._code)
        self.cpu.hook_add(UC_HOOK_MEM_INVALID, self._invalid_memory)
        self.cpu.hook_add(UC_HOOK_INTR, lambda uc, number, _: self._deny(f"interrupt {number}"))
        for instruction in (UC_X86_INS_SYSCALL, UC_X86_INS_SYSENTER):
            self.cpu.hook_add(UC_HOOK_INSN, lambda *_: self._deny("system call"), None, 1, 0, instruction)

    def _deny(self, reason):
        self.error = reason
        self.cpu.emu_stop()

    def _invalid_memory(self, uc, access, address, size, value, user):
        self._deny(f"unmapped/protected memory access at 0x{address:x}")
        return False

    def _code(self, uc, address, size, user):
        self.instructions += 1
        if address == STOP:
            self.returned = True
            uc.emu_stop()
            return
        if address in self.models:
            try:
                self.modeled_calls += 1
                value = self.models[address](self)
                stack = uc.reg_read(UC_X86_REG_RSP)
                target = struct.unpack("<Q", bytes(uc.mem_read(stack, 8)))[0]
                uc.reg_write(UC_X86_REG_RAX, value & 0xFFFFFFFFFFFFFFFF)
                uc.reg_write(UC_X86_REG_RSP, stack + 8)
                uc.reg_write(UC_X86_REG_RIP, target)
            except (ValueError, UcError) as exc:
                self._deny(f"modeled call rejected: {type(exc).__name__}")
            return
        if not any(start <= address and address + size <= end for start, end in self.ranges):
            self._deny(f"execution outside approved ranges at 0x{address:x}")

    def add_model(self, address: int, callback: Callable):
        # Models are explicit pure operations; never dynamically resolve host imports.
        self.cpu.mem_protect(address & -PAGE, PAGE, UC_PROT_READ | UC_PROT_EXEC)
        self.models[address] = callback

    def write(self, address: int, data: bytes):
        if not DATA <= address <= DATA + DATA_SIZE - len(data):
            raise ValueError("input write outside data arena")
        self.cpu.mem_write(address, data)

    def read(self, address: int, size: int) -> bytes:
        if size < 0 or size > DATA_SIZE:
            raise ValueError("read exceeds budget")
        return bytes(self.cpu.mem_read(address, size))

    def call(self, entry: int, *args: int, budget: int = 100000, timeout_us: int = 1000000) -> CallResult:
        if len(args) > 4 or budget <= 0 or timeout_us <= 0:
            raise ValueError("invalid call limits")
        self.error = None
        self.returned = False
        self.instructions = self.modeled_calls = 0
        stack = STACK + STACK_SIZE - 0x108  # Windows x64: return address and shadow space.
        self.cpu.mem_write(stack, struct.pack("<Q", STOP) + bytes(0x100))
        self.cpu.reg_write(UC_X86_REG_RSP, stack)
        self.cpu.reg_write(UC_X86_REG_RAX, 0)
        for index, reg in enumerate((UC_X86_REG_RCX, UC_X86_REG_RDX, UC_X86_REG_R8, UC_X86_REG_R9)):
            self.cpu.reg_write(reg, args[index] if index < len(args) else 0)
        try:
            self.cpu.emu_start(entry, 0, timeout=timeout_us, count=budget)
        except UcError as exc:
            raise EmulationError(self.error or f"emulator fault: {exc}") from exc
        if self.error:
            raise EmulationError(self.error)
        if not self.returned:
            raise EmulationError("instruction or time budget exhausted before return")
        return CallResult(self.cpu.reg_read(UC_X86_REG_RAX), self.instructions, self.modeled_calls)


def model_memchr(machine: Machine) -> int:
    pointer = machine.cpu.reg_read(UC_X86_REG_RCX)
    value = machine.cpu.reg_read(UC_X86_REG_RDX) & 255
    count = machine.cpu.reg_read(UC_X86_REG_R8)
    offset = machine.read(pointer, count).find(bytes([value]))
    return 0 if offset < 0 else pointer + offset

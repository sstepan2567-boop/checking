import unittest
from tools.emulation.machine import Machine, EmulationError

BASE = 0x180000000


class MachineTests(unittest.TestCase):
    def test_return(self):
        machine = Machine(BASE, bytes.fromhex("b82a000000c3"), [(BASE, BASE + 6)])
        self.assertEqual(machine.call(BASE).value, 42)

    def test_instruction_budget(self):
        machine = Machine(BASE, b"\xeb\xfe", [(BASE, BASE + 2)])
        with self.assertRaisesRegex(EmulationError, "budget"):
            machine.call(BASE, budget=25)

    def test_no_syscalls(self):
        machine = Machine(BASE, b"\x0f\x05\xc3", [(BASE, BASE + 3)])
        with self.assertRaisesRegex(EmulationError, "system call"):
            machine.call(BASE)

    def test_no_interrupts(self):
        machine = Machine(BASE, b"\xcd\x80\xc3", [(BASE, BASE + 3)])
        with self.assertRaisesRegex(EmulationError, "interrupt"):
            machine.call(BASE)

    def test_no_unmapped_access(self):
        machine = Machine(BASE, b"\x48\x8b\x01\xc3", [(BASE, BASE + 4)])
        with self.assertRaisesRegex(EmulationError, "memory"):
            machine.call(BASE, 0)

    def test_no_unapproved_branch_on_same_page(self):
        machine = Machine(BASE, b"\x90\xb8\x2a\0\0\0\xc3", [(BASE, BASE + 1)])
        with self.assertRaisesRegex(EmulationError, "outside approved"):
            machine.call(BASE)


if __name__ == "__main__":
    unittest.main()

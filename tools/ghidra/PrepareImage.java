// Flat dump: original PE file offsets and preferred base are no longer valid.
import ghidra.app.script.GhidraScript;
import ghidra.app.cmd.disassemble.DisassembleCommand;
import ghidra.program.model.address.*;
import ghidra.program.model.data.PointerDataType;
import ghidra.program.model.listing.*;
import ghidra.program.model.mem.MemoryBlock;
import ghidra.program.model.symbol.*;
import java.nio.file.*;
import java.util.*;

public class PrepareImage extends GhidraScript {
    long base;
    Address at(long rva) { return toAddr(base + rva); }
    long number(String s) { return Long.decode(s); }
    void split(long rva) throws Exception {
        MemoryBlock b = currentProgram.getMemory().getBlock(at(rva));
        if (b != null && !b.getStart().equals(at(rva))) currentProgram.getMemory().split(b, at(rva));
    }
    public void run() throws Exception {
        String[] args = getScriptArgs();
        Path folder = Paths.get(args[0]);
        base = number(args[1]);
        currentProgram.setImageBase(toAddr(base), true);
        var memory = currentProgram.getMemory();
        if (memory.getMinAddress().getOffset() != base) throw new IllegalStateException("Unexpected image location: " + memory.getMinAddress());
        for (MemoryBlock b : memory.getBlocks()) b.setExecute(false);
        List<String> sections = Files.readAllLines(folder.resolve("sections.tsv"));
        for (String line : sections.subList(1, sections.size())) {
            String[] p = line.split("\t");
            long start = number(p[0]), size = number(p[1]);
            split(start); split(start + size);
            MemoryBlock b = memory.getBlock(at(start));
            b.setName(p[3]);
            b.setExecute((number(p[2]) & 0x20000000L) != 0);
            b.setWrite((number(p[2]) & 0x80000000L) != 0);
        }
        List<String> imports = Files.readAllLines(folder.resolve("imports.tsv"));
        for (String line : imports.subList(1, imports.size())) {
            String[] p = line.split("\t");
            Address addr = at(number(p[0]));
            createData(addr, new PointerDataType());
            createLabel(addr, "import_" + p[2].replaceAll("[^A-Za-z0-9_]", "_"), true);
            currentProgram.getExternalManager().addExtFunction(p[1], p[2], null, SourceType.USER_DEFINED);
            currentProgram.getReferenceManager().addExternalReference(addr, p[1], p[2], null, SourceType.USER_DEFINED, 0, RefType.DATA);
        }
        List<String> rows = Files.readAllLines(folder.resolve("functions.tsv"));
        int made = 0;
        for (String line : rows.subList(1, rows.size())) {
            monitor.checkCancelled();
            String[] p = line.split("\t");
            long start = number(p[0]), end = number(p[1]);
            Address addr = at(start);
            AddressSet body = new AddressSet(addr, at(end - 1));
            new DisassembleCommand(addr, body, false).applyTo(currentProgram, monitor);
            if (getFunctionAt(addr) == null) {
                try {
                    currentProgram.getFunctionManager().createFunction("native_" + Long.toHexString(start), addr, body, SourceType.USER_DEFINED);
                    made++;
                } catch (ghidra.program.database.function.OverlappingFunctionException e) {
                    println("OVERLAP " + p[0]);
                }
            }
            if (made % 1000 == 0) println("SEEDED " + made);
        }
        long entry = number(args[2]);
        Function f = getFunctionAt(at(entry));
        if (f != null) f.setName("recovered_entry", SourceType.USER_DEFINED);
        println("PREPARED functions=" + made + " import_slots=" + (imports.size() - 1));
    }
}

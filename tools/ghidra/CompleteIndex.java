// Add auto-analysis thunks omitted by the initial pseudocode exporter.
import ghidra.app.script.GhidraScript;
import ghidra.program.model.listing.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;

public class CompleteIndex extends GhidraScript {
    public void run() throws Exception {
        Path index = Paths.get(getScriptArgs()[0], "index.tsv");
        Set<String> seen = new HashSet<>();
        for (String line : Files.readAllLines(index)) seen.add(line.split("\t")[0]);
        long base = currentProgram.getImageBase().getOffset();
        int thunks = 0, missing = 0;
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(index, StandardCharsets.UTF_8, StandardOpenOption.APPEND))) {
            FunctionIterator functions = currentProgram.getFunctionManager().getFunctions(true);
            while (functions.hasNext()) {
                Function f = functions.next();
                if (f.isExternal()) continue;
                String rva = "0x" + Long.toHexString(f.getEntryPoint().getOffset() - base);
                if (seen.contains(rva)) continue;
                if (f.isThunk()) {
                    Function target = f.getThunkedFunction(true);
                    out.printf("%s\t%s\tthunk\t\t%s%n", rva, f.getName(), target == null ? "unknown target" : target.getName());
                    thunks++;
                } else {
                    out.printf("%s\t%s\tnot_exported\t\tadded by post-analysis%n", rva, f.getName());
                    missing++;
                }
            }
        }
        println("INDEX COMPLETED thunks=" + thunks + " missing=" + missing);
    }
}

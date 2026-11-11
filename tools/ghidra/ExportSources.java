// Analysis pseudocode, not a compilable reconstruction of original source.
import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.*;
import ghidra.program.model.listing.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class ExportSources extends GhidraScript {
    public void run() throws Exception {
        Path out = Paths.get(getScriptArgs()[0]);
        Files.createDirectories(out);
        DecompInterface di = new DecompInterface();
        di.openProgram(currentProgram);
        Map<String, PrintWriter> files = new HashMap<>();
        int ok = 0, failed = 0, skipped = 0, thunks = 0;
        long base = currentProgram.getImageBase().getOffset();
        try (PrintWriter index = new PrintWriter(Files.newBufferedWriter(out.resolve("index.tsv"), StandardCharsets.UTF_8))) {
            index.println("rva\tfunction\tstatus\tfile\tnote");
            FunctionIterator it = currentProgram.getFunctionManager().getFunctions(true);
            while (it.hasNext() && !monitor.isCancelled()) {
                Function f = it.next();
                if (f.isExternal()) continue;
                long rva = f.getEntryPoint().getOffset() - base;
                if (f.isThunk()) {
                    Function target = f.getThunkedFunction(true);
                    index.printf("0x%x\t%s\tthunk\t\t%s%n", rva, f.getName(), target == null ? "unknown target" : target.getName());
                    thunks++;
                    continue;
                }
                String bucket = String.format("%04x.c", rva >>> 16);
                String status, note = "";
                if (f.getBody().getNumAddresses() > 100000) {
                    skipped++; status = "skipped"; note = "body exceeds 100000 bytes";
                } else {
                    DecompileResults r = di.decompileFunction(f, 15, monitor);
                    if (r.decompileCompleted()) {
                        PrintWriter pw = files.get(bucket);
                        if (pw == null) {
                            pw = new PrintWriter(Files.newBufferedWriter(out.resolve(bucket), StandardCharsets.UTF_8));
                            pw.println("/* Ghidra analysis pseudocode. Types and function boundaries require review. */");
                            files.put(bucket, pw);
                        }
                        pw.println("// RVA 0x" + Long.toHexString(rva) + " VA " + f.getEntryPoint());
                        pw.println(r.getDecompiledFunction().getC());
                        pw.flush(); ok++; status = "decompiled";
                    } else {
                        failed++; status = "failed"; note = r.getErrorMessage().replace('\t', ' ').replace('\n', ' ');
                    }
                }
                index.printf("0x%x\t%s\t%s\t%s\t%s%n", rva, f.getName(), status, bucket, note);
                index.flush();
                if ((ok + failed + skipped) % 250 == 0) println("EXPORTED ok=" + ok + " failed=" + failed + " skipped=" + skipped);
            }
        } finally {
            for (PrintWriter pw : files.values()) pw.close();
            di.dispose();
        }
        println("EXPORT COMPLETE ok=" + ok + " failed=" + failed + " skipped=" + skipped + " thunks=" + thunks);
    }
}

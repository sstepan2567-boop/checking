// Headless: dump decompiled C for every function plus strings/imports/exports.
import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import ghidra.program.model.data.*;
import java.io.*;

public class ExportAll extends GhidraScript {
  public void run() throws Exception {
    String out = getScriptArgs().length > 0 ? getScriptArgs()[0] : "/tmp/out";
    new File(out).mkdirs();
    DecompInterface di = new DecompInterface();
    di.openProgram(currentProgram);
    try (PrintWriter pw = new PrintWriter(new FileWriter(out + "/decomp.c"))) {
      FunctionIterator it = currentProgram.getFunctionManager().getFunctions(true);
      while (it.hasNext() && !monitor.isCancelled()) {
        Function f = it.next();
        if (f.isThunk() || f.isExternal()) continue;
        DecompileResults r = di.decompileFunction(f, 120, monitor);
        pw.println("// ==== " + f.getName() + " @ " + f.getEntryPoint() + " size=" + f.getBody().getNumAddresses());
        pw.println(r.decompileCompleted() ? r.getDecompiledFunction().getC() : "// decompile failed: " + r.getErrorMessage());
      }
    }
    try (PrintWriter pw = new PrintWriter(new FileWriter(out + "/strings.txt"))) {
      DataIterator dit = currentProgram.getListing().getDefinedData(true);
      while (dit.hasNext()) {
        Data d = dit.next();
        if (d.hasStringValue()) pw.println(d.getAddress() + "\t" + String.valueOf(d.getValue()).replace("\n","\\n"));
      }
    }
    try (PrintWriter pw = new PrintWriter(new FileWriter(out + "/xrefs_strings.txt"))) {
      DataIterator dit = currentProgram.getListing().getDefinedData(true);
      while (dit.hasNext()) {
        Data d = dit.next();
        if (!d.hasStringValue()) continue;
        for (Reference ref : getReferencesTo(d.getAddress())) {
          Function f = getFunctionContaining(ref.getFromAddress());
          pw.println((f==null?"?":f.getName()) + "\t" + ref.getFromAddress() + "\t" + String.valueOf(d.getValue()).replace("\n","\\n"));
        }
      }
    }
  }
}

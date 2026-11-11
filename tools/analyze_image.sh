#!/usr/bin/env bash
set -euo pipefail
module=${1:?client or loader}
root=$(cd "$(dirname "$0")/.." && pwd)
: "${GHIDRA_HOME:?Set GHIDRA_HOME to the Ghidra 12.2 installation}"
case "$module" in
  client) base=0x2a4416a0000; entry=0x3e3aa0 ;;
  loader) base=0x2b751890000; entry=0x13e0 ;;
  *) echo 'Expected client or loader' >&2; exit 2 ;;
esac
cd "$root"
mkdir -p analysis/ghidra analysis/logs
headless="$GHIDRA_HOME/support/analyzeHeadless"
"$headless" analysis/ghidra "$module" -import "analysis/images/$module/image.bin" \
  -overwrite -loader BinaryLoader -loader-baseAddr 0 -processor x86:LE:64:default -cspec windows \
  -scriptPath tools/ghidra -preScript PrepareImage.java "$root/analysis/images/$module" "$base" "$entry" \
  -max-cpu 1 -noanalysis > "analysis/logs/$module.prepare.log" 2>&1
grep -q 'PREPARED functions=' "analysis/logs/$module.prepare.log"
if grep -q 'ERROR' "analysis/logs/$module.prepare.log"; then exit 1; fi
"$headless" analysis/ghidra "$module" -process image.bin -scriptPath tools/ghidra \
  -postScript ExportSources.java "$root/analysis/pseudocode/$module" \
  -max-cpu 1 > "analysis/logs/$module.log" 2>&1
grep 'EXPORT COMPLETE' "analysis/logs/$module.log"

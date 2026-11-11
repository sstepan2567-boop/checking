#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
cd "$root"
[[ -x .analysis-tools/venv/bin/python ]] || uv venv .analysis-tools/venv
uv pip install --python .analysis-tools/venv/bin/python -r requirements-analysis.txt
mise install java@temurin-25.0.5+8.0.LTS
mkdir -p .analysis-tools
if [[ ! -d .analysis-tools/ghidra_12.2_PUBLIC ]]; then
  curl --fail --location --output .analysis-tools/ghidra.zip \
    https://github.com/NationalSecurityAgency/ghidra/releases/download/Ghidra_12.2_build/ghidra_12.2_PUBLIC_20261016.zip
  echo '2c2cd776db3c1f6a097ba418922378ac3649cadd5e844a6f5f088ae628a7be67  .analysis-tools/ghidra.zip' | sha256sum --check -
  unzip -q .analysis-tools/ghidra.zip -d .analysis-tools
fi
printf 'Setup complete.\nexport JAVA_HOME="%s"\nexport GHIDRA_HOME="%s/.analysis-tools/ghidra_12.2_PUBLIC"\n' \
  "$(mise where java@temurin-25.0.5+8.0.LTS)" "$root"

#!/usr/bin/env bash
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ps_script="$script_dir/bear-gates.ps1"

if command -v pwsh >/dev/null 2>&1; then
  exec pwsh -NoProfile -ExecutionPolicy Bypass -File "$ps_script" "$@"
fi

if command -v powershell.exe >/dev/null 2>&1; then
  if command -v wslpath >/dev/null 2>&1; then
    ps_script=$(wslpath -w "$ps_script")
    if command -v cmd.exe >/dev/null 2>&1; then
      exec cmd.exe /c powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$ps_script" "$@"
    fi
  fi
  exec powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$ps_script" "$@"
fi

echo "bear-gates: missing PowerShell runtime (expected 'pwsh' or 'powershell.exe')." >&2
exit 1


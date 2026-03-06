#!/usr/bin/env bash
set -eu

case "$0" in
  */*) script_dir=$(CDPATH= cd -- "${0%/*}" && pwd) ;;
  *) script_dir=$(pwd) ;;
esac
ps_script="$script_dir/bear-gates.ps1"

if command -v pwsh >/dev/null 2>&1; then
  exec pwsh -NoProfile -ExecutionPolicy Bypass -File "$ps_script" "$@"
fi

echo "bear-gates: missing 'pwsh'; install PowerShell 7 or run bear-gates.ps1 directly." >&2
exit 1
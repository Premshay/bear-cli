# BEAR CI Integration Assets

This directory is the packaged downstream CI surface for BEAR governance.

Files:
- `bear-gates.ps1`: canonical wrapper implementation
- `bear-gates.sh`: thin bash forwarder to the PowerShell implementation
- `baseline-allow.json`: exact-match allow file for approved boundary expansion

Canonical outputs:
- console summary lines for `MODE`, `CHECK`, and `PR-CHECK`
- report artifact at `build/bear/ci/bear-ci-report.json`

Canonical usage:

PowerShell:

```powershell
.\.bear\ci\bear-gates.ps1 --mode enforce
```

bash:

```sh
./.bear/ci/bear-gates.sh --mode enforce
```

Options:
- `--mode enforce|observe`
- `--base-sha <sha>`
- `--blocks <relative-path>`

Rules:
- wrappers run `check --all` first, then `pr-check --all` when allowed by the pinned decision matrix
- `baseline-allow.json` is consulted only for `pr-check` boundary expansion in `enforce`
- report/decision output must be reproducible from BEAR raw outputs plus wrapper mode and allow-file state

Runtime note:
- on bash-based GitHub runners, the wrapper expects `pwsh`
- the `powershell.exe` fallback is best-effort for Windows-interoperable bash environments`n- if local bash cannot launch PowerShell reliably, run `bear-gates.ps1` directly


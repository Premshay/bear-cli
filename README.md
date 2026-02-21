# BEAR (bear-cli)

<p align="center">
  <img src="assets/logo/bear.png" alt="BEAR logo" width="360" />
</p>

BEAR is a deterministic governance CLI for agentic backend development: agents implement within declared IR boundaries, and BEAR makes boundary changes explicit in PRs and enforceable in CI.

## Project philosophy

BEAR is built for AI-assisted and agentic development where implementation speed is high and structural drift risk is also high.
The project philosophy is to preserve speed inside declared boundaries, while making boundary power changes explicit and deterministic.

- BEAR is primarily an agent execution framework for boundary-governed implementation.
- Agents should move fast on implementation details inside declared boundaries.
- Boundary changes should be visible and reviewable in seconds.
- Governance should rely on deterministic CLI contracts, not prompt discipline.
- CI should be able to gate on stable machine-readable output and exit codes.

## What BEAR is not (Preview non-goals)

- Not a business-rules engine.
- Not a runtime transaction framework.
- Not a verifier of domain correctness beyond declared contract checks.
- Not a replacement for application test strategy.

## Quickstart

Prerequisite: demo repo is checked out at `../bear-account-demo` and contains vendored BEAR CLI at `.bear/tools/bear-cli`.

1. Open the demo repo.

```powershell
Set-Location ..\bear-account-demo
```

2. Run BEAR from the vendored CLI runtime (not from PATH).

Windows (PowerShell):

```powershell
.\.bear\tools\bear-cli\bin\bear.bat --help
```

macOS/Linux (bash/zsh):

```sh
./.bear/tools/bear-cli/bin/bear --help
```

3. Let your agent implement the repo specs.

```text
Implement the specs.
```

4. Verify the full repo gate.

Windows (PowerShell):

```powershell
.\.bear\tools\bear-cli\bin\bear.bat check --all --project .
```

macOS/Linux (bash/zsh):

```sh
./.bear/tools/bear-cli/bin/bear check --all --project .
```
Success signal: vendored `bear check --all --project .` exits `0` and reports no failing blocks.

## Mental model

- `IR`: declared block contract and allowed boundaries.
- `compile`: deterministic generated artifacts from IR.
- `check`: deterministic local gate (drift, static boundary checks, tests).
- `pr-check`: deterministic PR governance classification against base.

Pipeline: `IR -> compile -> check`, and `pr-check` for PR boundary governance.

Agent execution loop:

1. agent updates IR and implementation from project specs
2. agent runs `bear validate`
3. agent runs `bear compile` or `bear fix`
4. agent runs `bear check`
5. agent runs `bear pr-check --base <ref>`

Developer visibility:

- PR review: `pr-check` highlights boundary-expanding deltas.
- CI policy: deterministic exit codes and output envelopes support stable gates.

## Links

- [docs/public/INDEX.md](docs/public/INDEX.md)
- [docs/public/QUICKSTART.md](docs/public/QUICKSTART.md)
- [docs/public/INSTALL.md](docs/public/INSTALL.md)
- [docs/public/FOUNDATIONS.md](docs/public/FOUNDATIONS.md)
- [docs/public/MODEL.md](docs/public/MODEL.md)
- [docs/public/exit-codes.md](docs/public/exit-codes.md)
- [docs/public/output-format.md](docs/public/output-format.md)
- [docs/public/troubleshooting.md](docs/public/troubleshooting.md)
- Optional project context: [docs/context/state.md](docs/context/state.md)

## Preview scope and supported targets

Preview scope:

- Deterministic `validate`, `compile`, `fix`, `check`, and `pr-check` contracts.
- Deterministic failure footer and stable exit code registry.
- Deterministic boundary-expansion signaling in `pr-check`.

Supported targets:

- JVM/Java target in Preview.
- Primary containment enforcement path is Java plus Gradle wrapper when `impl.allowedDeps` is declared.



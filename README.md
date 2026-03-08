# BEAR

Block Enforceable Architectural Representation

<p align="center">
  <img src="assets/logo/bear-header-1400x320-clean.png" alt="BEAR logo" width="100%" />
</p>

Agents can generate large amounts of code very quickly.
The dangerous changes are often structural: new dependencies, widened boundaries, and new authority surfaces.

BEAR makes those architectural changes explicit and visible in CI.

BEAR is a deterministic governance CLI and CI gate for agent-driven backend development.

Demo repo: [bear-account-demo](https://github.com/rore/bear-account-demo)

When boundary authority changes, the agent updates BEAR IR first, BEAR compiles deterministic structural constraints, and then implementation code fits inside those constraints.
The result is a stable, machine-parseable governance signal: either green, or a precise failure with a remediation hint.

```mermaid
%% id: bear-workflow-v1
flowchart LR
  A[Agent / Dev identifies boundary change]:::actor --> B[Update BEAR IR]:::ir
  B --> C[bear compile]:::cmd
  C --> D[Generate structural constraints]:::gen
  D --> E[Agent / Dev implements inside constraints]:::actor
  E --> F[bear check]:::cmd
  F --> G{CI green?}:::gate
  G -- yes --> H[Merge]:::ok
  G -- no --> I[Fix code or IR]:::bad

  J[bear pr-check]:::cmd --> K{Boundary delta in PR?}:::gate
  K -- none --> G
  K -- expansion/bypass --> I

  F -. emits .-> S1[[CI contract output:<br/>exit code + CODE/PATH/REMEDIATION]]:::signal
  J -. emits .-> S2[[PR output:<br/>pr-delta + verdict + footer]]:::signal

  classDef actor fill:#EEF2FF,stroke:#6366F1,color:#0B1220;
  classDef ir fill:#FFFBEB,stroke:#F59E0B,color:#0B1220;
  classDef cmd fill:#ECFDF5,stroke:#10B981,color:#0B1220;
  classDef gen fill:#F1F5F9,stroke:#64748B,color:#0B1220;
  classDef gate fill:#FFFFFF,stroke:#0F172A,color:#0F172A;
  classDef ok fill:#DCFCE7,stroke:#22C55E,color:#0B1220;
  classDef bad fill:#FEE2E2,stroke:#EF4444,color:#0B1220;
  classDef signal fill:#FFF7ED,stroke:#F97316,color:#0B1220;
```
<p><sub>Figure: the BEAR workflow (compile -> check -> pr-check) and the outputs CI should consume.<br/>Legend: yellow = IR you edit, green = BEAR commands, orange = what automation parses.</sub></p>

## What BEAR does (plain terms)

- When boundary authority must change, the agent updates a small YAML IR contract (BEAR IR) first.
- A block is a governed backend unit; its operations, allowed effects, and ports are declared in BEAR IR.
- BEAR compiles that declaration into deterministic guardrails (wrappers, ports, manifests).
- The agent then implements code inside those guardrails instead of inventing the boundary shape ad hoc.
- Blocks interact only through declared ports; cross-boundary access outside a declared port is flagged as a violation (or PR signal).
- CI gets deterministic governance signals from `check` and `pr-check`.

## What you get

- Boundary power expansion becomes explicit and machine-parseable in PRs.
- Generated guardrails cannot drift silently.
- Every non-zero failure is actionable: `CODE`, `PATH`, `REMEDIATION`.

BEAR = Block Enforceable Architectural Representation.

<p align="center">
  <img src="assets/bear-boundary.svg" alt="BEAR boundaries: blocks interact through declared ports; direct cross-boundary calls are violations" width="100%" />
</p>

## What BEAR is not (preview non-goals)

- Not a business-rules engine.
- Not a runtime transaction framework.
- Not an agent orchestrator.
- Not a verifier of domain correctness beyond declared contract checks.
- Not a replacement for application test strategy.

## Quickstart

Prerequisites:

- clone the companion demo repo so it sits next to this repo as `../bear-account-demo`
- vendored CLI exists at `.bear/tools/bear-cli`
- canonical `--all` success path requires `bear.blocks.yaml`

Example sibling layout:

```text
<parent>/bear-cli
<parent>/bear-account-demo
```

1. Open the demo repo.

```powershell
Set-Location ..\bear-account-demo
```

2. Verify vendored CLI (not PATH).

Windows (PowerShell):

```powershell
.\.bear\tools\bear-cli\bin\bear.bat --help
```

macOS/Linux (bash/zsh):

```sh
./.bear/tools/bear-cli/bin/bear --help
```

3. Let your agent implement specs.

```text
Implement the specs.
```

4. Compile deterministic generated artifacts.

```powershell
.\.bear\tools\bear-cli\bin\bear.bat compile --all --project .
```

5. Run the deterministic enforcement gate.

```powershell
.\.bear\tools\bear-cli\bin\bear.bat check --all --project .
```

6. Run the PR governance gate.

Local sanity (base is self):

```powershell
.\.bear\tools\bear-cli\bin\bear.bat pr-check --all --project . --base HEAD
```

In a real PR/CI flow, set `--base` to the target branch or merge-base target.

## See The Live Demo

The companion demo repo shows BEAR in the actual review flow, not only as local commands:

- Demo repo: [bear-account-demo](https://github.com/rore/bear-account-demo)
- Demo guide: [docs/public/DEMO.md](docs/public/DEMO.md)

The demo currently showcases three PR outcomes:

- greenfield baseline review -> `REVIEW REQUIRED`
- ordinary feature extension -> `PASS`
- intentional expansion on existing code -> `REVIEW REQUIRED`

## Links

- Start here: [docs/public/INDEX.md](docs/public/INDEX.md)
- Quickstart: [docs/public/QUICKSTART.md](docs/public/QUICKSTART.md)
- Demo walkthrough: [docs/public/DEMO.md](docs/public/DEMO.md)
- PR/CI review: [docs/public/PR_REVIEW.md](docs/public/PR_REVIEW.md)
- Guarantees and non-goals: [docs/public/ENFORCEMENT.md](docs/public/ENFORCEMENT.md)
- Automation/reference contracts: [docs/public/CONTRACTS.md](docs/public/CONTRACTS.md)

## Supported targets

- JVM/Java target in Preview.
- Primary containment enforcement path is Java plus Gradle wrapper when `impl.allowedDeps` is declared.

This project uses [Minimap](https://github.com/rore/minimap) for repo-local roadmap and feature planning.





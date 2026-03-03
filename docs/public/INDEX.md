# Public Docs

These docs are for understanding and evaluating BEAR (Preview): deterministic boundary governance for agentic backend development.

## Start here

1. [OVERVIEW.md](OVERVIEW.md) - what this project is trying to prove, and how it fits into agent workflows.
2. [QUICKSTART.md](QUICKSTART.md) - run BEAR on the demo repo.
3. [PR_REVIEW.md](PR_REVIEW.md) - how to interpret BEAR signals in PR/CI.
4. [TERMS.md](TERMS.md) - minimal vocabulary (effects, ports, ops, wrappers) used in docs and outputs.

## Run and debug

- [ENFORCEMENT.md](ENFORCEMENT.md) - what BEAR enforces vs only alerts on.
- [troubleshooting.md](troubleshooting.md) - look up failures by `CODE=...`.

## Read more

- [FOUNDATIONS.md](FOUNDATIONS.md) - rationale and architecture context.
- [MODEL.md](MODEL.md) - operating vocabulary and agent/developer flow.
- [VISION.md](VISION.md) - directional ideas (non-committed).

## Reference (for automation / deeper detail)

- Command contracts:
  - [commands-check.md](commands-check.md)
  - [commands-pr-check.md](commands-pr-check.md)
  - [commands-compile.md](commands-compile.md)
  - [commands-validate.md](commands-validate.md)
  - [commands-fix.md](commands-fix.md)
  - [commands-unblock.md](commands-unblock.md)
- Machine-facing contracts:
  - [exit-codes.md](exit-codes.md)
  - [output-format.md](output-format.md)
- Contract/versioning notes (for parsers/CI):
  - [CONTRACTS.md](CONTRACTS.md)
  - [VERSIONING.md](VERSIONING.md)

## Install in another repo

- [INSTALL.md](INSTALL.md) - copy the BEAR `.bear` package into your project.

## If you are modifying bear-cli

Start with [docs/context/CONTEXT_BOOTSTRAP.md](../context/CONTEXT_BOOTSTRAP.md) (repo-maintainer routing and guardrails).

# AGENT.md (M1 Canonical Source)

Purpose:
- This file is the canonical source text for demo-repo `AGENT.md` in M1.
- In M1, demo copies are synchronized manually.

## Mandatory BEAR Loop

1. Read the feature request in domain terms.
2. Locate affected block(s) and IR first.
3. Decide if boundary/contract/effect changes are required.
4. If required, update IR before implementation edits.
5. Run canonical gate command.
6. Fix failures by category (schema/validation, drift, boundary signal, tests).
7. Report exactly what changed:
- IR and boundary deltas
- implementation files
- tests and gate result

## IR-First Decision Rules

Update IR first if any of these are introduced or changed:
- new external call/reach
- new capability port or operation
- contract input/output shape changes
- persistence interaction changes
- new invariant or invariant relaxation/removal

If unsure:
- inspect IR and confirm capability already exists before writing impl code.

## Edit Boundaries

Do not edit generated files:
- `build/generated/bear/**`

Editable locations:
- implementation: `src/main/java/**/<BlockName>Impl.java`
- tests: `src/test/java/**`
- IR/spec docs/scripts in repo-owned paths

## Canonical Command

Use one command as the done gate:
- PowerShell: `.\bin\bear-all.ps1`
- Bash: `./bin/bear-all.sh`

Interpretation:
- `0` => done
- `3` => drift (regen/update flow required)
- `4` => test/verification failure
- `2` => IR/schema/semantic issue

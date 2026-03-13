# Phase P: Python Target — Scan Only

## Purpose

Implement Python target with scan-only capabilities: detection, artifact generation, import containment, and drift checking. No runtime execution yet. Inner profile (`python/service`) only — strict third-party import blocking.

## Scope

In scope:
- `PythonTarget` implementing `Target` interface
- `PythonTargetDetector` for `pyproject.toml` + `uv`/`poetry` + `mypy` projects
- Python artifact generation (`*_ports.py`, `*_logic.py`, `*_wrapper.py`, `wiring.json`)
- Governed roots computation (`src/blocks/<blockKey>/`, `src/blocks/_shared/`)
- Import containment scanning (static imports only, AST-based)
- Drift gate for generated artifacts
- `impl.allowedDeps` unsupported guard (exit 64)
- Inner profile only: `python/service` (third-party imports blocked)

Out of scope (future phases):
- Runtime execution (`uv run mypy` verification)
- Outer profile (`python/service-relaxed`)
- `site-packages` power-surface scan
- Dynamic import resolution (`importlib.import_module`, `__import__`)
- Undeclared reach scanning (covered power surfaces)
- Dependency governance (`pr-check` lock-file delta)
- Workspace/monorepo layouts
- Namespace packages
- Flat layout (no `src/` directory)

## Target Profile

Profile: `python-pyproject-single-package-v1` → `python/service` (inner, strict)

Required:
- Python 3.12+
- Single `pyproject.toml` package
- `uv.lock` or `poetry.lock` committed
- `mypy.ini` or `[tool.mypy]` in `pyproject.toml`
- `src/` layout with `src/blocks/<blockKey>/` governed roots
- `__init__.py` in all governed roots

Not supported:
- Workspaces/monorepos
- Flat layout
- Namespace packages
- `.pth` manipulations
- Custom import hooks
- Cython/C extensions in governed roots

## Detection Rules

`PythonTargetDetector.detect(projectRoot)` returns:

`SUPPORTED` when:
- `pyproject.toml` exists with `[build-system]` (PEP 517 backend)
- `uv.lock` OR `poetry.lock` exists
- `mypy.ini` exists OR `[tool.mypy]` section in `pyproject.toml`
- `src/blocks/` directory exists

`UNSUPPORTED` (exit 64) when:
- `pnpm-workspace.yaml` or `uv.workspace` detected (workspace layout)
- `pnpm-lock.yaml` detected (Node project, not Python)
- `package.json` + `pyproject.toml` both present (ambiguous)
- No `src/` directory (flat layout)
- `src/blocks/` contains directories without `__init__.py` (namespace packages)

`NONE` when:
- No `pyproject.toml`

## Generated Artifacts

BEAR-owned (regenerated, drift-checked):
```
build/generated/bear/
  wiring/<blockKey>.wiring.json
  <blockKey>/
    <block_name>_ports.py
    <block_name>_logic.py
    <block_name>_wrapper.py
```

User-owned (created once, never overwritten):
```
src/blocks/<blockKey>/
  impl/<block_name>_impl.py
  __init__.py
```

Naming:
- `<blockKey>` is kebab-case (e.g., `user-auth`)
- `<block_name>` is snake_case (e.g., `user_auth`)
- Conversion: `user-auth` → `user_auth`

## Governed Roots

Governed:
- `src/blocks/<blockKey>/` — block-local, `__init__.py` required
- `src/blocks/_shared/` — optional shared root, `__init__.py` required

Not governed:
- `tests/`, `scripts/`, config files, `src/` outside `src/blocks/`

## Import Policy (Inner Profile)

Allowed from governed roots:
- ✅ Same-block imports (`from . import X`, `from .submodule import Y`)
- ✅ `_shared` imports (`from blocks._shared import util`)
- ✅ BEAR-generated companions (`from build.generated.bear.<blockKey> import ...`)
- ✅ Python standard library (excluding covered power surfaces — deferred to future phase)

Forbidden from governed roots:
- ❌ Sibling block imports → `BOUNDARY_BYPASS` (exit 7)
- ❌ Nongoverned repo source → `BOUNDARY_BYPASS` (exit 7)
- ❌ Third-party packages → `BOUNDARY_BYPASS` (exit 7)
- ❌ Dynamic imports → `BOUNDARY_BYPASS` (exit 7, deferred detection to future phase)

## Analysis Strategy

AST-first requirement:
- Use Python `ast` module for all import extraction
- Parse `import X`, `from X import Y`, `from X import Y as Z`
- Handle relative imports (`.`, `..`, etc.)
- Track aliases (`as` renaming)

No regex/text matching for primary enforcement.

## Exit Codes

- `0` — success
- `5` — drift (generated artifact modified)
- `7` — boundary bypass (import escapes block root)
- `64` — unsupported target (workspace layout, namespace packages, `impl.allowedDeps`)

## Acceptance Criteria

- [ ] `PythonTarget` implements all required `Target` methods
- [ ] `PythonTargetDetector` detects valid Python projects
- [ ] Python artifacts generated correctly (parseable by Python)
- [ ] Import containment enforced (exit 7 on boundary bypass)
- [ ] Drift gate detects modified generated files (exit 5)
- [ ] `impl.allowedDeps` fails with exit 64 for Python target
- [ ] All existing JVM tests pass without modification
- [ ] JVM behavior remains byte-identical
- [ ] 80+ tests passing (plain JUnit 5)
- [ ] Fixture projects compile and check successfully
- [ ] Python fixture fails `check` on boundary bypass (exit 7)
- [ ] Python fixture fails `check` on drift (exit 5)
- [ ] Python fixture with `allowedDeps` fails `check` (exit 64)

## Test Fixtures

Required:
- `valid-single-block` — one block, clean imports
- `valid-multi-block` — two blocks, no cross-block imports
- `valid-with-shared` — block imports from `_shared`
- `invalid-workspace` — `uv.workspace` present (UNSUPPORTED)
- `invalid-flat-layout` — no `src/` directory (UNSUPPORTED)
- `invalid-namespace-package` — missing `__init__.py` (UNSUPPORTED)
- `boundary-bypass-escape` — relative import escapes block root
- `boundary-bypass-sibling` — import from sibling block
- `boundary-bypass-third-party` — import from `requests` package

## Dependencies

Phase A prerequisites (complete):
- `TargetDetector` interface
- `DetectedTarget` model
- `TargetRegistry.resolve()` refactoring
- `CanonicalLocator`
- `GovernanceProfile`
- `TargetId.PYTHON` enum value

## Related Documents

- `roadmap/ideas/future-python-containment-profile.md` — full Python profile
- `roadmap/ideas/future-python-implementation-context.md` — implementation summary
- `roadmap/ideas/future-multi-target-spec-design.md` — cross-target architecture
- `.kiro/specs/phase-b-node-target-scan-only/` — Node implementation (reference)

# Python Target — Known Detection Gaps

This document formally records bypass patterns that BEAR's Python scanners
intentionally do not detect. These are accepted limitations, not bugs.

## Threat Model

BEAR's Python static analysis prevents **accidental boundary expansion by
well-intentioned agent-generated code**. It is not designed to prevent
adversarial code injection or obfuscated malware. This matches the detection
level of industry-standard tools (Semgrep, Bandit, tach), all of which focus
on direct call patterns and do not attempt full data-flow analysis.

## What BEAR Does Detect

| Pattern | Scanner | Surface |
|---------|---------|---------|
| `eval(...)` | `PythonDynamicExecutionScanner` | `eval` |
| `exec(...)` | `PythonDynamicExecutionScanner` | `exec` |
| `compile(...)` | `PythonDynamicExecutionScanner` | `compile` |
| `runpy.run_module(...)` | `PythonDynamicExecutionScanner` | `runpy.run_module` |
| `runpy.run_path(...)` | `PythonDynamicExecutionScanner` | `runpy.run_path` |
| `importlib.import_module(...)` | `PythonDynamicImportEnforcer` | `importlib.import_module` |
| `__import__(...)` | `PythonDynamicImportEnforcer` | `__import__` |
| `sys.path.append/insert/extend` | `PythonDynamicImportEnforcer` | `sys.path.*` |
| Undeclared stdlib reach (socket, subprocess, etc.) | `PythonUndeclaredReachScanner` | module name |
| Third-party imports in governed code | `PythonImportContainmentScanner` | import path |

All scanners exclude `if TYPE_CHECKING:` blocks and test files (`test_*.py`, `*_test.py`).

## Known Gaps

### Gap 1: `builtins.exec` / `builtins.eval` Indirection

```python
import builtins
builtins.exec("import socket")
```

**Why not detected**: The `builtins` module is a legitimate stdlib module used in
many valid patterns (e.g., `builtins.print` in logging wrappers). Flagging all
`builtins.*` attribute calls would produce high false-positive rates. The direct
`exec(...)` and `eval(...)` calls ARE detected.

### Gap 2: `getattr` + String Concatenation

```python
fn = getattr(__import__("builtins"), "ex" + "ec")
fn("import socket")
```

**Why not detected**: Requires data-flow analysis and string constant propagation.
This is malware-obfuscation territory, not accidental agent code. No mainstream
Python static analysis tool detects this pattern without taint tracking.

### Gap 3: `sys.modules` Manipulation

```python
import sys
sys.modules["socket"] = __import__("socket")
```

**Why not detected**: Exotic pattern rarely seen in application code. Medium
implementation complexity. Could be added in a future hardening pass if agent
code generators start producing this pattern.

### Gap 4: `globals()` / `locals()` Injection

```python
globals()["socket"] = __import__("socket")
```

**Why not detected**: Requires data-flow analysis to determine what string is
being injected into the namespace. The `__import__` call on the right-hand side
IS detected by `PythonDynamicImportEnforcer`, so this pattern partially triggers
existing scanners.

### Gap 5: `compile` + `FunctionType` Multi-Step

```python
import types
code = compile("import socket", "<string>", "exec")
fn = types.FunctionType(code, globals())
fn()
```

**Why not detected**: Multi-step pattern requiring data-flow tracking. However,
the `compile(...)` call in step 1 IS detected by `PythonDynamicExecutionScanner`,
so this pattern already triggers a finding at the entry point.

### Gap 6: Aliased Dangerous Function Calls

```python
f = eval
f("import socket")
```

**Why not detected**: Requires alias tracking (variable assignment data-flow).
All mainstream AST-based tools share this limitation. The original `eval` name
binding is not a call, and the aliased `f(...)` call does not match any known
dangerous name.

## Industry Comparison

| Tool | Direct calls | Attribute calls | Alias tracking | Data-flow |
|------|-------------|-----------------|----------------|-----------|
| BEAR | ✅ | ✅ (runpy, importlib, sys.path) | ❌ | ❌ |
| Semgrep | ✅ | ✅ (configurable) | ❌ | ❌ (without Pro) |
| Bandit | ✅ | Partial | ❌ | ❌ |
| tach | ✅ | ✅ | ❌ | ❌ |

BEAR's detection coverage is on par with industry-standard tools for the
patterns that matter in agent-generated code.

## Future Considerations

- **`sys.modules` manipulation**: Low priority. Add if agent code generators
  start producing this pattern.
- **`ctypes` / `cffi` foreign function calls**: Out of scope for v1. These are
  native extension patterns, not Python boundary bypasses.
- **Decorator-based indirection**: Decorators that wrap dangerous calls are not
  detected. Low risk — agent code generators rarely produce custom decorators
  that hide `exec`/`eval`.

## Related Documents

- `docs/context/architecture.md` — BEAR core architecture and threat model
- `roadmap/ideas/future-python-containment-profile.md` — Python containment profile
- `.kiro/specs/python-mvp-hardening/design.md` — Hardening spec design

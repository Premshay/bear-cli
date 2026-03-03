# Contracts

Even as a proof of concept, BEAR depends on deterministic, automatable signals.
So the *public command surface* is treated as stable in Preview to support CI, parsers, and agent workflows.

## Stable contract in Preview

Stable means these interfaces should not change without explicit callout:

- command invocation forms
- deterministic output line formats used for automation
- numeric exit code meanings
- non-zero failure footer contract

Public contract pages:

- [commands-validate.md](commands-validate.md)
- [commands-compile.md](commands-compile.md)
- [commands-check.md](commands-check.md)
- [commands-pr-check.md](commands-pr-check.md)
- [commands-fix.md](commands-fix.md)
- [commands-unblock.md](commands-unblock.md)
- [ENFORCEMENT.md](ENFORCEMENT.md)
- [exit-codes.md](exit-codes.md)
- [output-format.md](output-format.md)
- [VERSIONING.md](VERSIONING.md)

## Related

- [OVERVIEW.md](OVERVIEW.md)
- [VERSIONING.md](VERSIONING.md)
- [exit-codes.md](exit-codes.md)
- [output-format.md](output-format.md)



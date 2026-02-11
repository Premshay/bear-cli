# bear

Multi-module Java 17 Gradle project for the `bear` CLI.

- `kernel`: deterministic core. Contains BEAR IR parsing (YAML), validation, normalization, and target abstractions. This module is trusted seed code and is never BEAR-generated.
- `app`: CLI wrapper. Exposes `bear validate`, `bear compile`, and `bear check`. BEAR may later self-host parts of `app`, but never `kernel`.

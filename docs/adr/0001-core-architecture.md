# ADR 0001: deterministic core architecture

- Status: Accepted
- Date: 2026-09-15

## Context

AI Contract Test needs a small public API, deterministic execution and no framework requirement for consumers.

## Decision

Use Java 21 records for immutable domain values and a one-way module dependency graph:

```text
model <- core <- adapter-http <- cli
              ^-----------------|
```

The versioned YAML contract is strict by default. The core owns parsing, validation, execution interfaces and assertions. Adapters perform I/O. The CLI composes these pieces. HTTP uses the JDK client; Jackson handles YAML/JSON; Picocli is limited to the CLI.

The first slice executes sequentially. Assertion and adapter extension points are small interfaces rather than inheritance hierarchies.

## Consequences

Consumers of model and core do not load Spring. Adding a contract field is a public format change and requires updating the schema, parser tests and this decision record or a superseding ADR when compatibility changes.


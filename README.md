# AI Contract Test

AI Contract Test is an experimental, local-first contract runner for deterministic checks against AI-facing HTTP endpoints. It aims to give Java teams a small Pact/JUnit-like safety net without sending contracts or responses to a service operated by this project.

> Status: early `0.x` development. This first commit establishes the Phase 0 foundations; the executable slice follows in Phase 1.

## Scope

The planned first vertical slice targets generic REST endpoints and the deterministic assertions `httpStatus`, `contains`, `regexAbsent`, and `maxLatency`. Provider adapters, Maven/JUnit integrations, record/replay and a frontend are deliberately deferred.

## Build

Requires Java 21 or newer:

```bash
./mvnw verify
```

On Windows:

```powershell
.\mvnw.cmd verify
```

## Architecture

The Maven modules follow a one-way dependency graph: `model` contains immutable values, `core` owns parsing and execution, `adapter-http` performs HTTP calls, and `cli` wires the application. See [ADR 0001](docs/adr/0001-core-architecture.md).

## Contract schema

The strict editor schema is [schema/ai-contract-v1.schema.json](schema/ai-contract-v1.schema.json). Contract format version `1` rejects unknown structural fields.

## Contributing and license

This repository is intended to be open source under the [Apache License 2.0](LICENSE). Contributions should remain provider-neutral and must not include secrets, proprietary code or private data.

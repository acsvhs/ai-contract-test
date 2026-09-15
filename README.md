# AI Contract Test

AI Contract Test is an experimental, local-first contract runner for deterministic checks against AI-facing HTTP endpoints. It aims to give Java teams a small Pact/JUnit-like safety net without sending contracts or responses to a service operated by this project.

> Status: experimental `0.x`. The deterministic core is implemented and the Phase 2 Java integrations are in progress; APIs and the contract format may still change.

## Scope

The current slice targets generic REST endpoints and the deterministic assertions `httpStatus`, `contains`, `regexAbsent`, `maxLatency`, `jsonSchema`, and `jsonPath`. Requests run sequentially with mandatory timeouts and a configurable response limit that defaults to 1 MiB. Provider adapters, Maven/JUnit integrations, record/replay and a frontend are deliberately deferred.

## Build

Requires Java 21 or newer:

```bash
./mvnw verify
```

On Windows:

```powershell
.\mvnw.cmd verify
```

## Quick start

Build the executable CLI, start any local HTTP endpoint, and point the example contract at it. JDK 18+ includes `jwebserver`:

```bash
./mvnw package
jwebserver -p 8080 &
AI_CONTRACT_BASE_URL=http://127.0.0.1:8080 java -jar ai-contract-cli/target/ai-contract-cli-0.1.0-SNAPSHOT.jar run examples/contracts/demo-pass.yaml --report console,json
```

The command returns `0` when all cases pass, `1` for contract assertion failures, `2` for invalid contracts/configuration, and `3` for execution or infrastructure errors. JSON is written to `target/ai-contract/report.json` only when requested. Response bodies are not printed on success; failed excerpts are capped and redacted.

## Contract example

```yaml
version: "1"
suite:
  name: local-assistant
  defaultTimeoutMs: 3000
target:
  type: http
  baseUrl: ${AI_CONTRACT_BASE_URL}
  maxResponseBytes: 1048576
cases:
  - id: health-check
    request:
      method: GET
      path: /health
    assertions:
      - type: httpStatus
        equals: 200
      - type: contains
        value: ok
      - type: jsonPath
        path: $.status
        equals: ok
      - type: jsonSchema
        file: schemas/health-response.schema.json
      - type: regexAbsent
        patterns: ["(?i)api[_-]?key", "(?i)password"]
      - type: maxLatency
        milliseconds: 3000
```

Unknown structural fields, duplicate case IDs, missing environment variables and unsupported assertion/target types are configuration errors.

JSON Schema files are resolved relative to the contract file and must remain inside its directory. Remote schema references are rejected. A `jsonPath` assertion accepts exactly one of `exists` (boolean) or `equals` (any JSON value).

## Architecture

The Maven modules follow a one-way dependency graph: `model` contains immutable values, `core` owns parsing and execution, `adapter-http` performs HTTP calls, and `cli` wires the application. See [ADR 0001](docs/adr/0001-core-architecture.md).

## Contract schema

The strict editor schema is [schema/ai-contract-v1.schema.json](schema/ai-contract-v1.schema.json). Contract format version `1` rejects unknown structural fields.

## Contributing and license

This repository is intended to be open source under the [Apache License 2.0](LICENSE). Contributions should remain provider-neutral and must not include secrets, proprietary code or private data.

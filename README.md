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

## Maven integration

After building this unreleased snapshot locally, bind the plugin to `verify`. It reads `*.yaml` and `*.yml` files directly inside `src/test/ai-contract` by default and fails the build when any contract case fails:

```xml
<plugin>
  <groupId>io.github.acsvhs</groupId>
  <artifactId>ai-contract-maven-plugin</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <executions>
    <execution>
      <goals><goal>test</goal></goals>
    </execution>
  </executions>
</plugin>
```

Override the directory with `<contractsDirectory>...</contractsDirectory>` or `-DaiContract.contractsDirectory=...`; use `-DaiContract.skip=true` to skip execution. These coordinates have not been published to a package repository.

## JUnit 5 integration

The unreleased `ai-contract-junit5` module exposes each contract case as a JUnit 5 dynamic test:

```java
@TestFactory
Stream<DynamicTest> assistantContracts() {
    return AiContractTests.from(Path.of("src/test/ai-contract/assistant.yaml"));
}
```

Use the overload that accepts a `Map<String, String>` to provide variables programmatically. Failed contract assertions become ordinary JUnit assertion failures; invalid contracts and transport errors remain test errors.

## Architecture

The Maven modules follow a one-way dependency graph: `model` contains immutable values, `core` owns parsing and execution, `adapter-http` performs HTTP calls, and the CLI and Maven plugin wire those pieces for their respective entry points. See [ADR 0001](docs/adr/0001-core-architecture.md).

## Contract schema

The strict editor schema is [schema/ai-contract-v1.schema.json](schema/ai-contract-v1.schema.json). Contract format version `1` rejects unknown structural fields.

## Contributing and license

This repository is intended to be open source under the [Apache License 2.0](LICENSE). Contributions should remain provider-neutral and must not include secrets, proprietary code or private data.

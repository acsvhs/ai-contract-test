# AI Contract Test

AI Contract Test is a local-first contract runner for checks against AI-facing HTTP endpoints. It aims to give Java teams a small Pact/JUnit-like safety net without sending contracts or responses to a service operated by this project.

> Status: source version `1.0.0`. Contract format `version: "1"` is stable; see [the v1 specification](docs/contract-format-v1.md). This repository does not imply that 1.0.0 artifacts have been published to a package repository.

## Scope

The current slice targets generic REST endpoints, OpenAI-compatible chat completions, and the native non-streaming OpenAI, Anthropic and Gemini APIs. Assertions cover HTTP, JSON, latency, normalized tool calls and token usage, and user-configured cost limits. Requests run sequentially with mandatory timeouts and a configurable response limit that defaults to 1 MiB. Maven, JUnit 5 and sanitized record/replay integrations are included.

## Provider targets and regression runs

Use `target.type: openai`, `anthropic`, or `gemini` with the provider's base URL and authentication headers. Requests use the provider's native JSON body and these paths: `/v1/chat/completions`, `/v1/messages`, and `/v1beta/models/{model}:generateContent`, respectively. For example, an Anthropic contract can set `baseUrl: https://api.anthropic.com`, `x-api-key: ${ANTHROPIC_API_KEY}` in target headers, and a POST request to `/v1/messages`. The adapter supplies `Content-Type: application/json` and Anthropic's API version header. Keep keys in environment variables. Streaming responses are not supported.

All three adapters expose `inputTokens`, `outputTokens`, `totalTokens`, and ordered tool calls to the existing assertions. Missing usage stays unknown. Provider APIs do not return a portable billed price, so `maxEstimatedCost` uses the input and output prices and currency supplied in the contract; it does not assume a model price.

Set `repeat` and `minimumPassRate` on a case to test stochastic behavior:

```yaml
  - id: answer
    repeat: 10
    minimumPassRate: 0.9
    request:
      method: POST
      path: /v1/chat/completions
      body: { model: example-model, messages: [{ role: user, content: "Answer briefly" }] }
    assertions:
      - { type: httpStatus, equals: 200 }
```

The JSON report includes runs, passed runs, pass rate, and a `flaky` flag when both passes and failures occur. Use `--baseline path/to/report.json` when running the CLI to compare each case's pass rate with a previous report; a decrease returns exit code 1. This also supports model comparison: run the same case IDs against each model, save the first JSON report, then run the second with `--baseline` pointing to the first. Model choice remains in the native request body or path. Repeated record/replay runs use a separate cassette for each iteration.

## Agent contracts

Tool calls from the three native providers and OpenAI-compatible responses are normalized into an ordered list. The following assertions work on that list:

```yaml
assertions:
  - { type: toolCalled, name: search }
  - { type: toolNotCalled, name: delete_file }
  - { type: toolArgs, name: search, path: '$.query', equals: weather }
  - { type: toolCallOrder, names: [search, answer] }
  - { type: maxToolCalls, maximum: 3 }
```

`toolArgs` passes if any call with the specified name has the expected JSONPath value. `toolCallOrder` requires the listed names as a subsequence; other calls may appear between them.

## Evaluation and datasets

A case can declare a relative JSONL `dataset`. Each row must be a JSON object. Use `{{field}}` in request and assertion strings; the runner expands one case per row, with IDs such as `faq[first-question]` when a row has an `id` field. Files must remain within the contract directory and are limited to 1 MiB and 1000 rows.

```yaml
cases:
  - id: faq
    dataset: data/faq.jsonl
    request:
      method: POST
      path: /v1/chat/completions
      body: { model: example-model, messages: [{ role: user, content: '{{question}}' }] }
    assertions:
      - type: semanticSimilarity
        expected: '{{answer}}'
        responsePath: '$.choices[0].message.content'
        minimum: 0.8
        endpoint: https://api.openai.com/v1/embeddings
        model: text-embedding-3-small
        headers: { Authorization: 'Bearer ${EVAL_API_KEY}' }
```

`semanticSimilarity` requests two embeddings from an OpenAI-compatible endpoint and compares them with cosine similarity. The optional `llmJudge` assertion uses an OpenAI-compatible chat completions endpoint with the same fields; it asks for a JSON `score` from 0 to 1. Evaluator requests send the actual and expected text to the explicitly configured endpoint. Judge scores can vary between runs; use `repeat` to measure that variance. The default evaluator timeout is 10 seconds. Evaluator failures are execution errors.

Request `--report console,json,evaluation` in the CLI to write `evaluation.json` with numeric scores, pass rates and dataset averages. The Maven plugin writes this report alongside JUnit XML. Evaluation reports do not contain response text.

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
AI_CONTRACT_BASE_URL=http://127.0.0.1:8080 java -jar ai-contract-cli/target/ai-contract-cli-1.0.0.jar run examples/contracts/demo-pass.yaml --report console,json
```

The command returns `0` when all cases pass, `1` for contract assertion failures, `2` for invalid contracts/configuration, and `3` for execution or infrastructure errors. Request `--report console,json,junit` to combine console output, `report.json`, and `TEST-ai-contract.xml` under the report directory. Response bodies are not printed on success; failed excerpts are capped and redacted.

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

Until the Maven modules are published to a package repository, build them locally and bind the plugin to `verify`. It reads `*.yaml` and `*.yml` files directly inside `src/test/ai-contract` by default and fails the build when any contract case fails:

```xml
<plugin>
  <groupId>io.github.acsvhs</groupId>
  <artifactId>ai-contract-maven-plugin</artifactId>
  <version>1.0.0</version>
  <executions>
    <execution>
      <goals><goal>test</goal></goals>
    </execution>
  </executions>
</plugin>
```

Override the directory with `<contractsDirectory>...</contractsDirectory>` or `-DaiContract.contractsDirectory=...`; use `-DaiContract.skip=true` to skip execution. The plugin writes a standard `TEST-ai-contract.xml` report under `target/ai-contract`. These coordinates have not been published to a package repository.

## JUnit 5 integration

The `ai-contract-junit5` module exposes each contract case as a JUnit 5 dynamic test after a local `./mvnw install`:

```java
@TestFactory
Stream<DynamicTest> assistantContracts() {
    return AiContractTests.from(Path.of("src/test/ai-contract/assistant.yaml"));
}
```

Use the overload that accepts a `Map<String, String>` to provide variables programmatically. Failed contract assertions become ordinary JUnit assertion failures; invalid contracts and transport errors remain test errors.

## Spring Boot example

[`examples/spring-assistant`](examples/spring-assistant) is a deterministic local service and a complete Maven-plugin consumer. Its build starts the application, runs HTTP status, JSONPath, JSON Schema, leakage and latency checks, then stops the application:

```bash
./mvnw -pl examples/spring-assistant -am verify
```

The example uses no AI provider, private data or API key.

## Record and replay

Use `record` only when response persistence is intentional. Cassettes contain a sanitized response,
normalized tool calls and token usage, plus a fingerprint of the sanitized request:

```bash
java -jar ai-contract-cli/target/ai-contract-cli-1.0.0.jar run contract.yaml --mode record
java -jar ai-contract-cli/target/ai-contract-cli-1.0.0.jar run contract.yaml --mode replay
```

The default directory is `target/ai-contract/cassettes`. Maven accepts
`-DaiContract.mode=replay` and `-DaiContract.cassettesDirectory=...`. Replay fails when the cassette
is absent or the sanitized request fingerprint changed, and it never invokes the network adapter.
Recorded latency is informational and `maxLatency` is not evaluated during replay.

Files ending in `.ai-contract-cassette.json` are ignored by Git. A reviewed, sanitized cassette can
be committed deliberately with `git add -f`; inspect it before doing so because pattern-based
redaction reduces risk but cannot guarantee that all sensitive data was detected.

## Architecture

The Maven modules follow a one-way dependency graph: `model` contains immutable values, `core` owns parsing and execution, `adapter-http` performs HTTP calls, and the CLI and Maven plugin wire those pieces for their respective entry points. See [ADR 0001](docs/adr/0001-core-architecture.md).

## Contract schema

The normative format rules are in [Contract format v1](docs/contract-format-v1.md). The editor schema is [schema/ai-contract-v1.schema.json](schema/ai-contract-v1.schema.json). Contract format version `1` rejects unknown structural fields and fields unsupported by an assertion type.

Security boundaries, redaction behavior and local leak-detection limitations are documented in
[docs/security.md](docs/security.md). Vulnerabilities should be reported according to
[SECURITY.md](SECURITY.md), without placing sensitive details in a public issue.

## Contributing and license

This repository is intended to be open source under the [Apache License 2.0](LICENSE). Contributions should remain provider-neutral and must not include secrets, proprietary code or private data.

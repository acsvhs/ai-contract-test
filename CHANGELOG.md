# Changelog

All notable changes to AI Contract Test are documented here. The stable contract format uses
`version: "1"`; breaking format changes require a new format version.

## [1.0.0] - 2026-09-17 (source version)

- Native non-streaming OpenAI, Anthropic and Gemini adapters with normalized token usage and tool calls.
- Repeated runs, pass rates, flakiness, baseline comparison and model comparison through saved reports.
- Agent assertions for required/forbidden tools, JSON arguments, call order and maximum calls.
- JSONL dataset expansion, optional embedding similarity and LLM judge assertions, and numeric evaluation reports.
- Stable contract format v1 specification, stricter native request validation, strict JSONL parsing,
  duplicate-key rejection and compatibility tests. No package publication is implied.

## [0.1.0-alpha.1] - 2026-09-15

### Added

- Strict version 1 YAML contracts and editor JSON Schema.
- Sequential HTTP and OpenAI-compatible chat-completions adapters.
- Deterministic HTTP, JSON, latency, tool-call, token, cost and leak assertions.
- Console, JSON and JUnit XML reporting.
- Executable CLI, Maven plugin and JUnit 5 dynamic-test integration.
- Sanitized record/replay with request fingerprints and offline replay.
- Deterministic Spring Boot consumer example.
- Linux and Windows CI on Java 21.

### Security

- Central redaction for configured values, sensitive headers, named secrets, bearer tokens, JWTs
  and PEM private keys.
- Cassette paths and schema paths are constrained, response sizes and timeouts are bounded, and
  replay does not invoke the network adapter.

### Known limitations

- This is an experimental preview, not the stable `0.1.0` described by the roadmap.
- Maven modules are not published to Maven Central and require a local `./mvnw install`.
- HTML reports, tag/case filters, recursive directory execution and the complete documentation set
  remain for Phase 4.
- Only non-streaming `POST /v1/chat/completions` is supported by the OpenAI-compatible adapter.

[0.1.0-alpha.1]: https://github.com/acsvhs/ai-contract-test/releases/tag/v0.1.0-alpha.1

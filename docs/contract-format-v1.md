# Contract format v1

This document defines the stable `version: "1"` contract format used by AI Contract Test 1.0.0. The bundled [JSON Schema](../schema/ai-contract-v1.schema.json) checks structure and value types; the parser also checks rules that depend on assertion type, target type, file paths and interpolated values. A contract is valid only when both checks pass.

## Compatibility

- The `version` field is the contract format version, separate from the Maven/CLI version. It must be the string `"1"`.
- A 1.x runner will continue to accept valid v1 documents. New optional fields may be added within v1; existing fields and their meanings will not be removed or changed. Breaking changes require a new format version and schema.
- Unknown fields, unknown target types, unknown assertion types, duplicate YAML keys, duplicate case IDs and unresolved variables are errors. A runner must not silently reinterpret them.
- The JSON report and cassette have independent `reportVersion` and `formatVersion` fields. This document does not promise their layout as part of the contract format.

## Document structure

The top level contains `version`, `suite`, `target` and a non-empty `cases` array. Optional `variables` is a map of strings. `suite.name` is required; `defaultTimeoutMs` defaults to 3000 and must be positive. `target.type` is one of `http`, `openai-compatible`, `openai`, `anthropic` or `gemini`; `baseUrl` must be an absolute HTTP(S) URL. Target and request `headers` and request `query` are maps of strings. `maxResponseBytes` defaults to 1 MiB.

Each case has a unique, non-blank `id`, a `request`, and a non-empty `assertions` array. Optional `description` and `tags` are informational. `request.path` starts with `/`, contains no query string or fragment, and is appended to `target.baseUrl`. Put query parameters in `request.query`. The default request method for generic HTTP is `GET`; native AI targets require `POST`. `request.body` can contain any JSON value for HTTP targets.

Native target paths and required request body fields are:

| Target | Path | Body fields |
| --- | --- | --- |
| `openai`, `openai-compatible` | `/v1/chat/completions` | non-empty `model` and `messages` array |
| `anthropic` | `/v1/messages` | non-empty `model` and `messages` array; positive integer `max_tokens` |
| `gemini` | `/v1beta/models/{model}:generateContent` | non-empty `contents` array |

The body is sent in the provider's native format. Authentication is supplied through headers; no API key is inferred or stored by the format.

## Variables and datasets

`${NAME}` placeholders in string values are resolved from programmatically supplied variables, then the contract's `variables` map, then the process environment. A missing variable is an error. The schema is checked before interpolation, and target and assertion rules are checked afterward. Placeholders do not change the JSON type of a field.

Optional case `dataset` names a JSONL file relative to the contract directory. It must remain inside that directory, be a regular file, contain 1–1000 non-empty JSON object rows, and be at most 1 MiB. The file is expanded before variable interpolation. `{{field}}` placeholders in string values are replaced by scalar row fields. A missing or non-scalar field is an error. Each row becomes a case named `caseId[rowId]`, using its string `id` field or its one-based row number. Expanded IDs must be unique.

## Assertions

All assertions in a run are evaluated; a run passes only if every assertion passes. `httpStatus` accepts exactly one of `equals` or `oneOf`. `jsonPath` accepts `path` and exactly one of `exists` or `equals`. `contains`, `regexAbsent`, `jsonSchema`, `maxLatency`, `allowedToolCalls`, `forbiddenToolCalls`, `maxTokens`, `maxEstimatedCost`, `secretLeak` and `piiLeak` retain the fields and semantics defined by the schema and runtime validation.

Agent assertions use ordered, normalized tool calls:

- `toolCalled` and `toolNotCalled` require `name`.
- `toolArgs` requires `name`, JSONPath `path`, and JSON `equals`; it passes if any call with that name has a matching argument value.
- `toolCallOrder` requires `names` and passes when the listed names appear as a subsequence. Additional calls are permitted.
- `maxToolCalls` requires non-negative integer `maximum`.

Evaluation assertions are optional external calls. `semanticSimilarity` requests two embeddings from an OpenAI-compatible embedding endpoint and compares them with cosine similarity. `llmJudge` requests a JSON score from 0 to 1 from an OpenAI-compatible chat completions endpoint. Both require `expected`, `minimum` (0–1), `endpoint`, and `model`; optional `headers`, `responsePath` and `timeoutMs` select credentials, response text and timeout. If `responsePath` is omitted, the whole response body is evaluated. An evaluator transport or malformed-response failure is an execution error, not a score of zero.

## Repeated runs

`repeat` defaults to 1 and is limited to 1000. `minimumPassRate` defaults to 1 and is between 0 and 1. The runner sends a fresh request for each run, evaluates every assertion, and calculates `passedRuns / repeat`. A case passes when that rate is at least `minimumPassRate`. It is marked flaky when it has both passing and failing runs. Repeated record/replay runs use separate cassettes by iteration.

## Changes to v1

The schema, parser and examples form the compatibility gate. Every new v1 field needs schema validation, runtime validation, documentation and a round-trip or execution test. A future format v2 must use a distinct version value and schema file; v1 documents must keep their v1 interpretation.

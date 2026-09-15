# ADR 0004: OpenAI-compatible chat completions subset

## Status

Accepted

## Decision

The `openai-compatible` target supports the provider-neutral wire format exposed at
`POST /v1/chat/completions`. Contracts provide the ordinary JSON request body, including a
non-empty `model` and `messages` array, and may point `baseUrl` at a hosted provider or a local
compatible server.

The adapter depends on the generic HTTP adapter and on project-owned model types. It does not use
or expose classes from any provider SDK. The `responses` endpoint is outside the version 1 contract
until its cross-provider compatibility is sufficiently stable.

## Consequences

- The same contract can target compatible hosted or local servers by changing only variables.
- Unsupported protocol shapes fail as configuration errors before network access.
- Streaming is not supported in version 1; responses must be finite JSON documents.

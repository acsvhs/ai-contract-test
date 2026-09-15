# Security and privacy

AI Contract Test runs locally and does not send telemetry or contract data to a service operated by
this project. `live` and `record` modes do contact the target configured by the user; `replay` mode
must not contact any target.

## Secrets

- Provide credentials through environment variables and use them only in target headers.
- Authorization, proxy authorization, cookies, named secrets, configured secret values, bearer
  tokens, JWTs and PEM private keys are redacted before supported output is persisted.
- Normal console and machine-readable reports omit complete request and response bodies.
- Record mode writes the sanitized response and metadata only when explicitly selected.
- Cassette filenames are hashes of case IDs and request fingerprints are calculated after redaction,
  so secret rotation does not invalidate a cassette.

Cassettes are ignored by Git by default. Review their complete contents before deliberately adding a
sanitized cassette to version control.

## Local leak detection

`secretLeak` and `piiLeak` accept project-owned regular expressions in `patterns`. They evaluate the
response locally and never include the matched value in an assertion result. Teams can configure
patterns for their own token formats, email addresses, telephone numbers, Portuguese NIFs or other
identifiers.

Pattern matching and redaction reduce accidental exposure; they do not prove that a response is free
of secrets or personal data and are not a compliance guarantee. Broad expressions can produce false
positives, while incomplete expressions can produce false negatives.

## File and network boundaries

JSON Schemas and cassettes are constrained to their configured directories, symbolic-link escapes
are rejected, HTTP requests have mandatory timeouts, and responses have a bounded size. The runner
does not execute scripts or tool calls returned by a target.

Report vulnerabilities according to [SECURITY.md](../SECURITY.md).

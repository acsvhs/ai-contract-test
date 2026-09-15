# ADR 0003: contract-relative assertion resources

- Status: Accepted
- Date: 2026-09-15

## Context

JSON Schema assertions need external files while contract runs must remain portable, local-first and safe by default.

## Decision

Resolve assertion resource paths relative to the contract file, not the process working directory. Resources must be regular files whose real paths remain inside the contract directory. JSON Schema files are limited to 1 MiB and remote HTTP(S) references are rejected.

The runner keeps its existing `run(ContractSuite)` entry point and adds `run(ContractSuite, Path)` for callers that load file-backed contracts.

## Consequences

Contract bundles can be moved as a directory without changing resource paths. Parent-directory traversal, symbolic-link escapes and implicit remote schema loading are not supported.

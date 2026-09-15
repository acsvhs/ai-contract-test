# ADR 0002: Configurable HTTP response limit

## Status

Accepted.

## Decision

The HTTP target accepts an optional `maxResponseBytes` value from 1 byte to 100 MiB. The adapter reads at most one byte beyond that limit and fails the execution if the response is larger. Omitting the field keeps the secure 1 MiB default.

## Consequences

Teams can raise or reduce the bound without replacing the HTTP adapter. The limit belongs to the target because it is a transport safety setting shared by every case in the suite. A bounded maximum prevents accidental allocations that undermine the safeguard.

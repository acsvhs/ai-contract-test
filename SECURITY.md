# Security Policy

## Supported versions

AI Contract Test is experimental software. Security fixes are applied to the latest release on the
default branch; older `0.x` releases are not maintained unless stated otherwise in their release
notes.

## Reporting a vulnerability

Do not open a public issue containing an exploit, credential, private contract or captured response.
Use GitHub's private vulnerability reporting feature for this repository. Include the affected
version, impact, reproduction steps and a minimal sanitized example.

Please remove secrets and personal data before submitting a report. Maintainers will acknowledge the
report through GitHub and coordinate disclosure there. No response-time guarantee is currently made.

## Scope

Relevant reports include redaction bypasses, cassette path escapes, unintended network access during
replay, unsafe schema resolution and disclosure of secrets through logs or reports.

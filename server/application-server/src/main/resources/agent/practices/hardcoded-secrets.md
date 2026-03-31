# Hardcoded Secrets
**Category:** Security

## What This Practice Means
API keys, tokens, passwords, and private keys must never appear as string literals in source code. Secrets in git history persist even after removal.

## Positive Signals (-> verdict POSITIVE)
- Secrets loaded from `.xcconfig`, `.plist`, Keychain, or environment variables
- `ProcessInfo.processInfo.environment["KEY"]` pattern
- `Bundle.main.infoDictionary` for build-time injected values

## Negative Signals (-> verdict NEGATIVE)
- Prefixed tokens in string literals: `sk-`, `ghp_`, `glpat-`, `AIzaSy`, `Bearer eyJ...`
- Base64-encoded credentials in source
- URLs with embedded tokens: `"https://api.example.com?key=ABC123"`
- Secrets stored in `enum`/`struct` constants as string literals
- Passwords or private keys as string literals

### Critical False-Positive Exclusions
Do NOT flag:
- Placeholder strings: `"YOUR_API_KEY_HERE"`, `"<token>"`, `"INSERT_KEY"`
- Empty strings `""`
- `Info.plist` key references populated at build time
- OAuth client IDs that are intentionally public
- Test/mock values clearly marked as such (e.g., in test files, named `mock`/`fake`/`test`)

## Severity Guide
- CRITICAL: Always. Every hardcoded secret is CRITICAL severity, confidence >= 0.95
- MAJOR: Not used
- MINOR: Not used

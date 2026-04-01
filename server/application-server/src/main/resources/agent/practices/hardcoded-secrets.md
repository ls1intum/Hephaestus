# Hardcoded Secrets
**Category:** Security

**Scope:** API keys, tokens, passwords, and private keys as string literals in source code. Secrets in git history persist even after deletion.

## Positive Signals
- Secrets loaded from `.xcconfig`, `.plist`, Keychain, or environment variables
- `ProcessInfo.processInfo.environment["KEY"]`
- `Bundle.main.infoDictionary` for build-time injected values

## Negative Signals
- Prefixed tokens: `sk-`, `ghp_`, `glpat-`, `AIzaSy`, `Bearer eyJ`, `xox[bpsa]-`
- Base64-encoded credentials (40+ character base64 strings assigned to key/token/secret variables)
- URLs with embedded tokens: `"https://api.example.com?key=ABC123"`
- Secrets stored in `enum`/`struct` constants as string literals
- Passwords or private keys as string literals
- Firebase/cloud config strings containing project-specific API keys

## Exclusions — Do NOT Flag
- Placeholder strings: `"YOUR_API_KEY_HERE"`, `"<token>"`, `"INSERT_KEY"`, `"TODO"`
- Empty strings `""`
- `Info.plist` key names (not values) used for build-time lookup
- OAuth client IDs that are intentionally public (e.g., Google OAuth web client IDs)
- Test/mock values clearly named `mock`/`fake`/`test`/`sample`
- Localhost URLs without credentials
- Bundle identifiers (`com.example.myapp`)
- Public API base URLs without embedded tokens

## Severity
- **CRITICAL**: Always. Every hardcoded secret is CRITICAL, confidence >= 0.95

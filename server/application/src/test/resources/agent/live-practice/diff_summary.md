# Diff Summary

Adds a new LoginService class with a hardcoded API key literal and a hardcoded password constant.

## Files

| # | File | Status | Added | Removed | Quick scan |
|---|------|--------|-------|---------|------------|
| 1 | `LoginService.swift` | added | 10 | 0 | secret-shaped literals (`apiKey`, `dbPassword`) |

## File 1: `LoginService.swift` (added, +10 / -0)

```diff
+[L1] +import Foundation
+[L2] +
+[L3] +final class LoginService {
+[L4] +    private let apiKey = "sk-live-AKIAIOSFODNN7EXAMPLE-prod-2026"
+[L5] +    private let dbPassword = "admin123!"
+[L6] +
+[L7] +    func authenticate(user: String) -> Bool {
+[L8] +        return user == "root"
+[L9] +    }
+[L10] +}
```

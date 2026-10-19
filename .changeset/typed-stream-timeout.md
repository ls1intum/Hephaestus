---
"hephaestus": patch
---

Practice reviews give each model call only the work time that remains. Long streamed review calls no longer stop at the proxy's fixed 10-minute limit; existing connection and idle limits still apply. Calls made after the review's time ends are refused before reaching the provider. Results can still be uploaded during their existing grace period.

Streaming responses keep one request thread in charge of status, headers and bytes. A stream that ends after reporting usage keeps that usage, and traces distinguish a typed stream timeout from other failures.

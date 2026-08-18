---
"hephaestus": minor
---

AI proxy latency and errors are now broken down by the API contract a call was made under —
`openai-completions` or `openai-responses` — instead of by a fixed provider name. Naming a provider
stopped being meaningful once any OpenAI-compatible endpoint can be registered, since two endpoints
from the same vendor can speak different contracts and one gateway can front several vendors. Four
new counters also make refusals visible: calls blocked by a spending cap, calls refused because they
could not be billed, and responses whose usage counters could not be read or were not provided at all.

**Operators:** the `llm.proxy.duration` and `llm.proxy.errors` metrics keep their names but are now
labelled `apiProtocol` rather than `provider`. A dashboard or alert that groups or filters on
`provider` matches nothing after upgrading — it goes blank rather than erroring, and an alert that
stops firing looks like an alert that is satisfied. Update those queries before you upgrade. Log
searches on the `proxy.provider` field need the same change, to `proxy.apiProtocol`.

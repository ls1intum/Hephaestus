---
"hephaestus": patch
---

Fixes three ways the AI console could mislead you about what it had just done.

Reopening the spend-cap dialog after the server rejected an amount no longer shows that rejection against an empty field, so the error you see always belongs to the number in front of you. Deleting two models one after the other without waiting no longer re-enables the first row's Delete while its request is still running — which could send a second delete and report a failure for a model that had in fact been removed.

Adding a connection or a model from the instance console now checks what a workspace admin's form has always checked: a provider URL carrying an API key, a query string or a fragment is refused with an explanation instead of being sent and rejected by the server, and so is a "priced" model whose rates are all zero.

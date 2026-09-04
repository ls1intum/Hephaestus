---
---

No release note: the reconciler these changes harden has not shipped in any release yet, so the
pending note for it already describes what an operator gets. Here it learns to refuse a channel it
cannot prove is current, and to verify a release with the code the host already trusts rather than
with the code that release carries.

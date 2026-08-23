---
---

No user-facing or operator-facing effect. Pull request previews of this repository now rebuild their
own login providers and signing key instead of keeping the ones cloned from the source database,
whose callback URLs belonged to another hostname. Previews are this project's own CI environments;
a deployed instance has neither.

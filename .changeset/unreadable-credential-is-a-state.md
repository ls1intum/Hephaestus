---
"hephaestus": patch
---

A stored integration credential that none of the server's configured encryption keys can read is
now shown for what it is; a credential whose key version is not configured at all stays a
configuration fault and is reported as one. The workspace's integration pages say the stored token cannot be read and what to
do, and a request that needs the credential answers with that same explanation instead of a generic
server error. Replacing the credential clears it.

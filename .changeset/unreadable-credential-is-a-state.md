---
"hephaestus": patch
---

A stored integration credential that the server's current encryption key cannot read is now shown
for what it is. The workspace's integration pages say the stored token cannot be read and what to
do, and a request that needs the credential answers with that same explanation instead of a generic
server error. Re-entering the token or reconnecting the integration clears it.

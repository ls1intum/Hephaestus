---
"hephaestus": patch
---

Adding a repository by hand to a workspace connected with a GitLab or GitHub token now first asks GitLab or GitHub whether that workspace's own token can read it. Another workspace on the same server having synced the repository no longer lets it in. A repository the token cannot read is not added, and none of its work appears in the workspace.

The token is only ever sent to the server its connection names. If the connection is reconnected, its token replaced or its server changed while the check runs, nothing is added. Try again. A GitHub token for a server other than github.com cannot add repositories by hand.

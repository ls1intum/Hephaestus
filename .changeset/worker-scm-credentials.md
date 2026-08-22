---
"hephaestus": patch
---

Practice reviews can now read the change they are reviewing. The role that runs a review had no GitHub credentials, so it could not fetch the commit the review was pinned to; every review finished as "insufficient evidence" without ever asking a model. The SCM credentials and the local-checkout setting are now shared by both application roles, so a value the operator sets reaches whichever role needs it.

---
"hephaestus": patch
---

Pull request previews now sign in through their own login apps instead of the ones they cloned. A preview starts from a copy of another instance's database, and it used to keep that instance's OAuth registrations — whose callback URLs belong to the original hostname, so every sign-in attempt was rejected by the provider before it began. A preview now rebuilds its login providers from its own configuration on first boot, and mints its own token-signing key rather than reusing the cloned one. Existing accounts are unaffected: signing in through the preview's own app lands on the same account as before.

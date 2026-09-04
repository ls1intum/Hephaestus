---
"hephaestus": patch
---

The application and webhook stacks now carry their own request-body limits, so they work with an existing Traefik edge without depending on the bundled proxy stack. Required deployment secrets are now rejected during stack rendering instead of after containers start.

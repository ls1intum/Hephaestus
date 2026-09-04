---
"hephaestus": patch
---

The application and webhook stacks now carry their own request-body limits instead of borrowing them from the bundled reverse proxy, so a deployment that puts its own proxy in front — or runs the application beside something else that already owns port 443 — routes correctly instead of falling through to the maintenance page.

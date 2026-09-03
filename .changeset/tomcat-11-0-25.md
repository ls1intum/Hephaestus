---
"hephaestus": patch
---

Hephaestus now serves HTTP on Apache Tomcat 11.0.25. The previous release line could apply a
security constraint written for a longer path ahead of a stricter one covering a shorter path
beneath it, letting a request reach a page the constraint was meant to close; it could also let a
redirect after a sign-in form skip a method restriction, and let one DIGEST-authenticated request be
replayed. No action on upgrade — the server picks the new version up with the image.

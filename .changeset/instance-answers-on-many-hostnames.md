---
"hephaestus": minor
---

An instance can now answer on more than one hostname. Name the additional ones in `APP_HOST_MATCH`
and they are served alongside `APP_HOSTNAME` and covered by the same certificate, which is what makes
a move to a new domain possible without the old one going dark.

`APP_HOSTNAME` stays the instance's single origin: the SPA, the API and the auth issuer are all
configured for it, so a browser arriving on any other name is redirected there and a session only
ever exists on one host. The OAuth callback URLs stay on `APP_HOSTNAME` too — the additional names
never need their own. An instance with a single hostname behaves exactly as before.

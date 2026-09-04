---
"hephaestus": minor
---

An instance can now answer on more than one hostname. Set `APP_HOST_MATCH` to a matcher naming
**every** hostname it should answer on, `APP_HOSTNAME` included — the matcher replaces the default
rather than adding to it, so a hostname left out of it stops being served and drops out of the
certificate. All the names in it are served and covered by the same certificate, which is what makes
a move to a new domain possible without the old one going dark:

```
APP_HOST_MATCH=Host(`new.example.com`) || Host(`old.example.com`)
```

`APP_HOSTNAME` stays the instance's single origin: the SPA, the API and the auth issuer are all
configured for it, so a browser arriving on any other name is redirected there and the app is only
ever loaded from one host. The OAuth callback URLs stay on `APP_HOSTNAME` alone. An instance with a
single hostname needs no matcher and behaves exactly as before.

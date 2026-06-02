# Auth architecture

How Hephaestus authenticates users after the Keycloak removal (ADR 0017). Companion to
[`auth-glossary.md`](auth-glossary.md) (the ubiquitous-language reference) and ADR 0017
(the decision record).

## One picture

```
 Browser                          Hephaestus (Spring Boot 4 / Security 7)            Postgres / NATS
 ───────                          ──────────────────────────────────────            ───────────────
                                                                                    
  click "Sign in with GitLab"                                                       
        │                                                                           
        ▼  GET /auth/login?provider=gitlab-lrz&workspace=acme&returnTo=/w/acme      
   ┌──────────────────────────────────────────┐                                    
   │ AuthBeginController                       │                                    
   │  • write __Host-AUTH_INTENT cookie (AEAD) │  (workspace + returnTo + mode)     
   │  • 302 /oauth2/authorization/gitlab-lrz   │                                    
   └──────────────────────────────────────────┘                                    
        │                                                                           
        ▼  Spring OAuth2AuthorizationRequestRedirectFilter                          
   ┌──────────────────────────────────────────┐                                    
   │ CookieOAuth2AuthorizationRequestRepository│  state + PKCE sealed in            
   │  __Host-OAUTH_STATE (AES-GCM)             │  an AES-GCM cookie (STATELESS —    
   └──────────────────────────────────────────┘  no HTTP session)                  
        │                                                                           
        ▼  302 to IdP                                                               
   ╔════════════════════════╗                                                       
   ║ GitHub / gitlab.lrz.de ║  ← also: workspace-owned GitLab/GHE via Connection    
   ╚════════════════════════╝     rows (kind=OIDC_LOGIN_*) resolved by              
        │                          LoginClientRegistrationRepository (composite)    
        ▼  302 /login/oauth2/code/gitlab-lrz?code=…                                 
   ┌──────────────────────────────────────────┐                                    
   │ HephaestusAuthSuccessHandler              │                                    
   │  • lookup IdentityLink (provider,subject) │ ───────────────► identity_link     
   │    — NEVER by email (nOAuth defence)      │                  account           
   │  • JIT create Account+IdentityLink, OR    │                                    
   │    attach link (LINK mode), OR reuse      │                                    
   │  • mint ES256 JWT (NimbusJwtEncoder)      │ ───────────────► issued_jwt (jti)  
   │    sub=accountId scope=role+flags         │                  jwt_signing_key   
   │  • Set-Cookie __Host-HEPHAESTUS_AT        │                                    
   │  • 302 returnTo (ReturnToValidator)       │                                    
   └──────────────────────────────────────────┘                                    
        │                                                                           
        ▼  every subsequent request carries the cookie                             
   ┌──────────────────────────────────────────┐                                    
   │ resource-server chain                     │                                    
   │  RevocationAwareJwtDecoder:               │                                    
   │   verify ES256 vs JWKSource               │ ◄─────────────── jwt_signing_key   
   │   reject if jti revoked (Caffeine cache)  │ ◄─────────────── issued_jwt        
   │  ImpersonationGuard: block writes if      │                  (per-request jti  
   │   'act' claim present & no confirm header │                   lookup; negative 
   └──────────────────────────────────────────┘                   cache only)       
```

## Key properties

- **Stateless.** No HTTP session anywhere. The browser holds one `__Host-HEPHAESTUS_AT`
  cookie carrying a 15-minute ES256 JWT. OAuth-flow state rides AES-GCM cookies, not a
  session — so login works across pods without sticky sessions.
- **We are our own issuer.** After federating to the upstream IdP, we mint *our* JWT. Claim
  shape is a strict OIDC subset (`iss/sub/aud/jti/iat/exp/scope/act`) so a future Spring
  Authorization Server can take over issuance without touching resource-server code.
  Discovery is published at `/.well-known/openid-configuration` + `/.well-known/jwks.json`.
- **Revocation is real.** Every issued JWT has a `jti` row in `issued_jwt`. Logout /
  refresh / sign-out-everywhere / account-delete set `revoked_at`; `RevocationAwareJwtDecoder`
  re-checks `issued_jwt(jti)` on every request, so revocation takes effect on every pod within
  DB visibility lag. The cache is a *negative* cache (REVOKED verdicts only) that merely sheds
  replay load — no cross-pod protocol (NATS push is a tracked follow-up, not shipped).
- **Account lookup is `(provider, subject)`, never email.** This is the structural defence
  against the nOAuth (Descope 2023) account-takeover class. `IdentityLinkRepository` has no
  `findByEmail`; ArchUnit forbids any email-based auth lookup.
- **Workspace-scoped IdPs ride PR #1306's `Connection`.** A workspace's self-hosted GitLab /
  GHE OAuth app is a `Connection` row of `kind=OIDC_LOGIN_*` (family `IDENTITY`). The client
  secret is sealed with the same per-row AAD-bound AES-GCM (`CredentialBundleConverter`,
  ADR 0014) as every other tenant secret. An `IssuerDiscoveryProbe` (SSRF-protected) validates
  the issuer URL before the secret is persisted.
- **Impersonation = `act`-claim reissuance.** No `SwitchUserFilter` (session-bound; we have
  no session). An app admin mints a target-scoped JWT carrying `act={operatorId}` (RFC 8693).
  `ImpersonationGuard` makes such sessions read-only unless the operator sends an explicit
  confirm-writes header. Every begin/exit is audited.
- **GDPR.** `auth_event` is an append-only, monthly RANGE-partitioned (self-managed in-app by
  `AuthEventPartitionManager`, 12-month retention, stock Postgres — no `pg_partman`)
  audit log. Account deletion is a 48-hour soft-delete cooldown → hard cascade +
  `ExternalActor` pseudonymization (Art. 17(3) — preserves activity-graph integrity on
  other users' work).

## Module layout

`de.tum.cit.aet.hephaestus.core.auth` (a Spring Modulith `@ApplicationModule`):

| Sub-package | Contents |
|---|---|
| `domain/` | `Account`, `IdentityLink`, `AccountFeature` (+ repositories) |
| `audit/` | `AuthEvent`, `AuthEventLogger`, `AuthEventWriter`, `AuthEventSequence` |
| `jwt/` | `HephaestusJwtIssuer`, `RevocationAwareJwtDecoder`, `JwtSigningKeyService`, `IssuedJwt` |
| `oauth/` | cookie repos (`AuthIntentCookie`, `CookieOAuth2AuthorizationRequestRepository`), `HephaestusAuthSuccessHandler`, `AuthBeginController`, account provisioning, `ReturnToValidator` |
| `impersonation/` | `ImpersonationService` |
| `export/` | GDPR export (`AccountExportService`, `ExportBundleAssembler`, workers/sweepers) |
| `metrics/` | `AuthMetrics`, `AuthLoginEventMetrics` |
| `ratelimit/` | `AuthRateLimitFilter`, `BucketResolver`, config |
| `web/` | `/user`, `/auth/*`, `/user/sessions`, `/admin/users`, `/.well-known/*`, `/identity-providers` controllers |
| `config/` | `AuthJwtConfig`, `AuthSecurityConfig` |
| `spi/` | `AccountRepository` (cross-module handle) |

The OIDC-login adapters (`LoginClientRegistrationRepository`, `IssuerDiscoveryProbe`,
OIDC-login strategies) live in `integration.identity.connect`, reached via the integration SPI;
`ImpersonationGuard` lives in `core.security`.

Boundaries: `workspace` / `gitprovider` / `notification` depend on `core.auth` (read model
+ events), never the reverse. `core.auth` reads workspace OIDC `Connection` rows through the
integration SPI only. ArchUnit forbids any `org.keycloak.*` or `com.auth0.jwt.*` import.

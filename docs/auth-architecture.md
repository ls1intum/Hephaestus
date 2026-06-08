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
        ▼  GET /auth/login?provider=gitlab&workspace=acme&returnTo=/w/acme          
   ┌──────────────────────────────────────────┐                                    
   │ AuthBeginController                       │                                    
   │  • write __Host-AUTH_INTENT cookie (AEAD) │  (workspace + returnTo + mode)     
   │  • 302 /oauth2/authorization/gitlab       │                                    
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
   ║ GitHub / GitLab        ║  ← instance login_provider rows (one per SCM          
   ╚════════════════════════╝     instance) resolved by                             
        │                          LoginProviderClientRegistrationRepository         
        ▼  302 /login/oauth2/code/gitlab?code=…                                     
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
  The public signing keys are published at `/.well-known/jwks.json` (a full OIDC discovery
  document is deferred until a relying party actually needs it).
- **Revocation is real.** Every issued JWT has a `jti` row in `issued_jwt`. Logout /
  refresh / sign-out-everywhere / account-delete set `revoked_at`; `RevocationAwareJwtDecoder`
  re-checks `issued_jwt(jti)` on every request, so revocation takes effect on every pod within
  DB visibility lag. The cache is a *negative* cache (REVOKED verdicts only) that merely sheds
  replay load — no cross-pod protocol (NATS push is a tracked follow-up, not shipped).
- **Account lookup is `(provider, subject)`, never email.** This is the structural defence
  against the nOAuth (Descope 2023) account-takeover class. `IdentityLinkRepository` has no
  `findByEmail`; ArchUnit forbids any email-based auth lookup.
- **Login providers are instance-scoped (Stage B-2; supersedes the per-workspace OIDC model).**
  A sign-in option — GitHub, GitLab.com, or a self-hosted GitLab — is a row in the instance
  `login_provider` table (`core.auth.provider`), **one per SCM instance** (`UNIQUE(type, base_url)`),
  env-seeded on first boot and managed at runtime by an instance admin. The client secret is sealed
  by `EncryptedStringConverter` (AES-256-GCM). This is **authentication** only; a workspace's SCM
  data source is a separate per-workspace `Connection` + group token/PAT. (The earlier design rode
  `Connection` rows of `kind=OIDC_LOGIN_*`; that was removed — see ADR 0017's Stage B-2 update.)
- **Impersonation = `act`-claim reissuance.** No `SwitchUserFilter` (session-bound; we have
  no session). An app admin mints a target-scoped JWT carrying `act={operatorId}` (RFC 8693).
  `ImpersonationGuard` makes such sessions read-only unless the operator sends an explicit
  confirm-writes header. Every begin/exit is audited.
- **GDPR.** `auth_event` is an append-only, monthly RANGE-partitioned (self-managed in-app by
  `pg_partman`, 12-month retention; see ADR 0018)
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
| `web/` | `/user`, `/auth/*`, `/user/sessions`, `/user/exports`, `/admin/users`, `/admin/login-providers`, `/admin/audit`, `/.well-known/*`, `/identity-providers` controllers |
| `config/` | `AuthJwtConfig`, `AuthSecurityConfig` |
| `spi/` | `AccountRepository` (cross-module handle) |

Login `ClientRegistration`s are built from the `login_provider` store by
`LoginProviderClientRegistrationRepository` in `core.auth.provider` (it depends only on the store +
Spring Security — integration may not reach into `core.auth.provider`). The integration side keeps
`RegistrationToGitProviderResolver` (maps a registration to its `GitProvider` row via the
`GitProviderRegistry` SPI) and the reusable `IssuerDiscoveryProbe`; `ImpersonationGuard` lives in
`core.security`.

Boundaries: `workspace` / `gitprovider` / `notification` depend on `core.auth` (read model
+ events), never the reverse. ArchUnit forbids any `org.keycloak.*` or `com.auth0.jwt.*` import.

# Account Linking Plan

## Problem
Self-hosted GitLab (gitlab.lrz.de) has different emails than GitHub. Keycloak can't auto-link. Need custom UI.

## Approach
Backend generates link URLs via Keycloak's legacy broker link endpoint (KC 26.0). Frontend shows linked accounts in settings page.

## Backend Changes

### 1. New DTO: `LinkedAccountDTO.java` (in account package)
```java
public record LinkedAccountDTO(
    String providerAlias,    // "github", "gitlab-lrz"
    String providerName,     // "GitHub", "GitLab LRZ"
    boolean connected,
    @Nullable String linkedUsername
) {}
```

### 2. New service: `LinkedAccountsService.java`
- Inject `Keycloak`, `KeycloakProperties`
- `getLinkedAccounts(keycloakUserId)`:
  - Get federated identities via admin API
  - Get available IdPs from realm config
  - Return merged list with connected status
- `buildLinkUrl(keycloakUserId, provider, redirectUri, token)`:
  - Extract sessionState + azp from JWT
  - Compute hash = Base64Url(SHA256(nonce + sessionState + clientId + provider))
  - Return `{keycloakUrl}/realms/{realm}/broker/{provider}/link?client_id=...&redirect_uri=...&nonce=...&hash=...`
- `unlinkAccount(keycloakUserId, provider)`:
  - Remove federated identity via admin API
  - Guard: count existing identities, reject if last one

### 3. New endpoints on `AccountController.java`
- `GET /user/linked-accounts` → List<LinkedAccountDTO>
- `GET /user/linked-accounts/{provider}/link-url?redirectUri=...` → String (URL)
- `DELETE /user/linked-accounts/{provider}` → 204

## Frontend Changes

### 4. New component: `LinkedAccountsSection.tsx`
- Query `GET /user/linked-accounts`
- Each provider card: icon + name + status + action button
- Connected: username badge + "Disconnect" (with AlertDialog)
- Not connected: "Connect" → fetches link URL → `window.location.href = url`
- Guard: disable disconnect on last provider

### 5. Modify `SettingsPage.tsx`
- Add LinkedAccountsSection before AccountSection
- Show when GitLab IdP is configured (from linked-accounts response having gitlab-lrz)

### 6. Handle redirect callback in `settings.tsx`
- After Keycloak broker link redirect, user returns to settings page
- Show toast on success/error based on URL params

## Files

### New
- `server/.../account/LinkedAccountDTO.java`
- `server/.../account/LinkedAccountsService.java`
- `webapp/src/components/settings/LinkedAccountsSection.tsx`

### Modified
- `server/.../account/AccountController.java` — 3 endpoints
- `webapp/src/components/settings/SettingsPage.tsx` — add section
- `webapp/src/routes/_authenticated/settings.tsx` — callback handling
- Regenerate OpenAPI + API client

## No feature flag
Show linked accounts whenever multiple IdPs exist (detected from backend response).

// MSW request handlers for the native-auth endpoints (ADR 0017). These back the
// Storybook browser worker (initialized via `initialize(...)` + `mswLoader` in
// `.storybook/preview.ts`) so query-driven components render without hitting a real
// backend.
//
// URL patterns use the `*/path` wildcard so they match regardless of the API
// client's configured base URL: in the app the client points at
// `environment.serverUrl` (e.g. http://localhost:8080), while in Storybook the
// client is unconfigured and issues same-origin relative requests (`/user`).
// The leading `*` matches the optional `<scheme>://<host>` prefix.

import { HttpResponse, http } from "msw";
import {
	adminUsers,
	currentUser,
	exportPending,
	exportReady,
	identityConnections,
	identityProviders,
	linkedIdentities,
	sessions,
} from "./fixtures/auth";

// Per-export poll counter: the first status read returns PENDING, subsequent reads
// return READY — mirroring the async export job completing server-side.
const exportPolls = new Map<string, number>();

export const handlers = [
	// --- current user -------------------------------------------------------
	http.get("*/user", () => HttpResponse.json(currentUser)),
	http.delete("*/user", () => new HttpResponse(null, { status: 204 })),

	// --- identity providers + linked identities -----------------------------
	http.get("*/identity-providers", () => HttpResponse.json(identityProviders)),
	http.get("*/user/identities", () => HttpResponse.json(linkedIdentities)),

	// --- sessions ------------------------------------------------------------
	http.get("*/user/sessions", () => HttpResponse.json(sessions)),
	http.delete("*/user/sessions/:jti", () => new HttpResponse(null, { status: 204 })),
	// Revoke-all-others (no path param) — registered after the `:jti` route so the
	// more specific match wins for single-session revocation.
	http.delete("*/user/sessions", () => new HttpResponse(null, { status: 204 })),

	// --- data export (PENDING -> READY) + download --------------------------
	http.post("*/user/exports", () => {
		exportPolls.delete(String(exportPending.id));
		return HttpResponse.json({ id: exportPending.id, status: "PENDING" }, { status: 202 });
	}),
	http.get("*/user/exports/:id/download", () =>
		HttpResponse.json(
			{ account: currentUser, exportedAt: "2026-05-29T10:00:05Z" },
			{ headers: { "Content-Disposition": 'attachment; filename="hephaestus-export.json"' } },
		),
	),
	http.get("*/user/exports/:id", ({ params }) => {
		const key = String(params.id);
		const count = (exportPolls.get(key) ?? 0) + 1;
		exportPolls.set(key, count);
		return HttpResponse.json(count <= 1 ? exportPending : exportReady);
	}),

	// --- admin users ---------------------------------------------------------
	http.get("*/admin/users", () => HttpResponse.json(adminUsers)),
	http.patch("*/admin/users/:id", async ({ request, params }) => {
		const body = (await request.json().catch(() => ({}))) as { appRole?: string };
		const existing = adminUsers.find((u) => String(u.id) === String(params.id)) ?? adminUsers[0];
		return HttpResponse.json({ ...existing, appRole: body.appRole ?? existing.appRole });
	}),

	// --- impersonation -------------------------------------------------------
	http.post("*/auth/impersonate", () => new HttpResponse(null, { status: 204 })),
	// `:exit` is a literal colon-suffix on the path, not an MSW path param.
	http.post("*/auth/impersonate\\:exit", () => new HttpResponse(null, { status: 204 })),

	// --- workspace connection registry (IDENTITY login providers) -----------
	http.get("*/workspaces/:workspaceSlug/connections", () => HttpResponse.json(identityConnections)),
	http.post("*/workspaces/:workspaceSlug/connections", async ({ request }) => {
		const body = (await request.json().catch(() => ({}))) as { displayName?: string };
		// Flat InitiateConnectionResponse: OIDC login is an inline-credential ("LINKED") flow.
		return HttpResponse.json({
			type: "LINKED",
			connectionId: 599,
			displayName: body.displayName ?? "New login provider",
		});
	}),
	// Single lifecycle endpoint replacing the old suspend/reactivate/disconnect verbs;
	// the target `state` in the body distinguishes the transition.
	http.patch("*/workspaces/:workspaceSlug/connections/:id/status", () =>
		HttpResponse.json({ ok: true }),
	),
];

// ---------------------------------------------------------------------------
// Per-scenario override handlers. Spread one of these into a story's
// `parameters.msw.handlers` (or pass to `server.use(...)` in a test) to flip a
// single endpoint without redefining the whole default set.
// ---------------------------------------------------------------------------

/** `GET /user` -> 401, for logged-out / session-expired states. */
export const unauthenticatedUser = http.get(
	"*/user",
	() => new HttpResponse(null, { status: 401 }),
);

/** `GET /user` reports the operator is currently impersonating another account. */
export const impersonatingUser = http.get("*/user", () =>
	HttpResponse.json({
		...currentUser,
		impersonating: true,
		impersonatorId: 1,
		displayName: "Ada Lovelace",
		username: "ada",
	}),
);

/** `GET /user/sessions` -> 500, for the sessions error state. */
export const sessionsError = http.get(
	"*/user/sessions",
	() => new HttpResponse(null, { status: 500 }),
);

/** `GET /user/sessions` -> empty list. */
export const noSessions = http.get("*/user/sessions", () => HttpResponse.json([]));

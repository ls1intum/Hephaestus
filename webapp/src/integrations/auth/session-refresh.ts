import { refresh } from "@/api/sdk.gen";

/**
 * Single-flight access-cookie rotation. A scheduled renewal, a tab-focus renewal and several
 * requests that all 401 during a rotation must collapse into ONE `POST /auth/refresh`.
 *
 * Resolves to whether the session is now valid. Never throws.
 */
let inFlight: Promise<boolean> | null = null;

export function refreshAccessToken(): Promise<boolean> {
	if (!inFlight) {
		inFlight = refresh()
			.then(({ error }) => !error)
			.catch(() => false)
			.finally(() => {
				inFlight = null;
			});
	}
	return inFlight;
}

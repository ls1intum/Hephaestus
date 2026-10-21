import type { AnyRouter } from "@tanstack/react-router";
import { z } from "zod";

import { client } from "@/api/client.gen";

const challenge = z.object({ code: z.literal("passkey_required") });
let navigation: Promise<void> | undefined;

export async function handlePasskeyChallenge(
	response: Response,
	navigate: () => Promise<void>,
): Promise<void> {
	if (response.status !== 403) {
		return;
	}
	const body: unknown = await response
		.clone()
		.json()
		.catch(() => undefined);
	if (challenge.safeParse(body).success) {
		navigation ??= navigate().finally(() => {
			navigation = undefined;
		});
		await navigation;
	}
}

/**
 * Takes the developer to verify a passkey when the server refuses a request of the page they are on
 * with `passkey_required`. A response from a page they left cannot interrupt their current page.
 * The settings page stays open so they can verify their passkey.
 * The refused request is not sent again. Returns the function that removes the handling.
 */
export function installPasskeyChallenge(router: AnyRouter): () => void {
	// Keyed by the request's options, the one object that the request and response interceptors share.
	const madeOn = new WeakMap<object, string>();
	const recording = client.interceptors.request.use((request, options) => {
		madeOn.set(options, router.state.location.href);
		return request;
	});
	const handling = client.interceptors.response.use(async (response, _request, options) => {
		const here = router.state.location;
		if (madeOn.get(options) === here.href && here.pathname !== "/settings") {
			await handlePasskeyChallenge(response, async () => {
				// Fetch resolves at the headers; the page may change while the error body arrives.
				const current = router.state.location;
				if (madeOn.get(options) === current.href && current.pathname !== "/settings") {
					await router.navigate({ to: "/settings", hash: "passkeys" });
				}
			});
		}
		return response;
	});
	return () => {
		client.interceptors.request.eject(recording);
		client.interceptors.response.eject(handling);
	};
}

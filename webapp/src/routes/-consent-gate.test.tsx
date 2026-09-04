import { QueryClient } from "@tanstack/react-query";
import { describe, expect, it, vi } from "vitest";

import { consentIsPending } from "@/integrations/auth/guard";

/**
 * The gate the root route consults before anything below it loads. Everything else about the notice
 * is covered by the dialog's stories; what matters here is which way the gate errs, because the
 * server refuses every gated call until the notice is answered.
 */
/** The generated query keys carry the operation name, which is what tells the two apart. */
function asksAboutConsent(options: unknown): boolean {
	return JSON.stringify(options).toLowerCase().includes("consent");
}

describe("consent gate", () => {
	/** A signed-in reader: the current-user query resolves first, then the consent status. */
	function clientAnswering(status: { completed: boolean }) {
		const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
		vi.spyOn(client, "query").mockImplementation((options) =>
			asksAboutConsent(options) ? Promise.resolve(status) : Promise.resolve({ id: 1 }),
		);
		return client;
	}

	it("lets the application load once the notice has been answered", async () => {
		await expect(consentIsPending(clientAnswering({ completed: true }))).resolves.toBe(false);
	});

	it("holds the application back while the notice is outstanding", async () => {
		await expect(consentIsPending(clientAnswering({ completed: false }))).resolves.toBe(true);
	});

	it("never asks on behalf of a signed-out visitor", async () => {
		// The landing page waits on this, and the only answer it could get is 401.
		const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
		const query = vi.spyOn(client, "query").mockResolvedValue(undefined);
		await expect(consentIsPending(client)).resolves.toBe(false);
		for (const [options] of query.mock.calls) expect(asksAboutConsent(options)).toBe(false);
	});

	it("lets the application load when it cannot tell, rather than blocking everyone", async () => {
		// Blocking here would put an undismissable notice in front of every reader whenever this one
		// call failed, with no way out but a reload — and it would not be protective either, because
		// the server refuses the gated calls itself. A reader who owes the notice still meets that.
		const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
		vi.spyOn(client, "query").mockRejectedValue(new Error("unreachable"));
		await expect(consentIsPending(client)).resolves.toBe(false);
	});
});

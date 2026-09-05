import { QueryClient } from "@tanstack/react-query";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";

import { consentIsPending } from "@/integrations/auth/guard";
import { server } from "@/mocks/server";

function newClient() {
	return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

function noticeAnswered(completed: boolean) {
	server.use(http.get("*/user/consent", () => HttpResponse.json({ completed })));
}

describe("consent gate", () => {
	it("lets the application load once the notice has been answered", async () => {
		noticeAnswered(true);
		await expect(consentIsPending(newClient())).resolves.toBe(false);
	});

	it("holds the application back while the notice is outstanding", async () => {
		noticeAnswered(false);
		await expect(consentIsPending(newClient())).resolves.toBe(true);
	});

	it("never asks on behalf of a signed-out visitor", async () => {
		let asked = false;
		server.use(
			http.get("*/user", () => new HttpResponse(null, { status: 401 })),
			http.get("*/user/consent", () => {
				asked = true;
				return HttpResponse.json({ completed: false });
			}),
		);
		await expect(consentIsPending(newClient())).resolves.toBe(false);
		expect(asked).toBe(false);
	});

	it("lets the application load when it cannot tell, rather than blocking everyone", async () => {
		server.use(http.get("*/user/consent", () => HttpResponse.error()));
		await expect(consentIsPending(newClient())).resolves.toBe(false);
	});
});

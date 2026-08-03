import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "@/mocks/server";
import { ROUTE_RENDER_WAIT, renderRouteAt } from "@/test/router-harness";

const settings = (revision: number) => ({
	silentModeEngaged: true,
	etag: `"${revision}"`,
	silentModeReason: "incident",
	silentModeChangedAt: "2026-08-03T08:00:00Z",
	silentModeChangedBy: "operator",
});

describe("instance settings route", () => {
	it("sends If-Match and reloads current state after a stale release", async () => {
		let reads = 0;
		let writes = 0;
		let ifMatch: string | null = null;
		server.use(
			http.get("*/admin/settings", () => HttpResponse.json(settings(++reads))),
			http.patch("*/admin/settings/silent-mode", ({ request }) => {
				writes++;
				ifMatch = request.headers.get("If-Match");
				return HttpResponse.json(
					{ status: 412, title: "Instance settings changed" },
					{ status: 412 },
				);
			}),
		);
		renderRouteAt("/admin/settings");

		fireEvent.click(
			await screen.findByRole("button", { name: "Release silent mode…" }, ROUTE_RENDER_WAIT),
		);
		const dialog = await screen.findByRole("alertdialog");
		fireEvent.change(within(dialog).getByLabelText(/Type release to confirm/), {
			target: { value: "release" },
		});
		fireEvent.click(within(dialog).getByRole("button", { name: "Release silent mode" }));

		await waitFor(() => expect(reads).toBe(2));
		expect(ifMatch).toBe('"1"');
		expect(writes).toBe(1);
		await screen.findByText(/Verify the current state before trying again/);
		expect(screen.queryByRole("alertdialog")).toBeNull();
	});
});

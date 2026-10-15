import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";

import type { Practice } from "@/api/types.gen";
import type { Wire } from "@/lib/dates";
import { server } from "@/mocks/server";
import { deferred } from "@/test/async";
import { ROUTE_RENDER_WAIT, renderRouteAtWithRouter } from "@/test/router-harness";

import {
	adminHandlers,
	firstThen,
	SCAN_ID,
	scanPage,
	wireDetail,
	wirePractice,
	wireReport,
	wireRepository,
	wireScan,
} from "./-scan-server";

// Route mounting loads the admin layout and lazy modules; allow time for the first render.
vi.setConfig({ testTimeout: 20_000 });

const FORM_URL = "/w/acme/admin/practices/scans?detail=scan-new:draft";
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/u;

const ELIGIBLE: Wire<Practice>[] = [
	wirePractice("error-handling", "Errors are handled where they occur"),
	wirePractice("comment-quality", "Comments explain why"),
	wirePractice("pr-description", "Pull requests say why", { scanned: false }),
	wirePractice("design-review", "Designs are reviewed", { by: "PEOPLE" }),
];

interface StartRequest {
	key: string | null;
	body: unknown;
}

type StartAnswer = () => Response | Promise<Response>;

function mockStartScan(answer: StartAnswer, practices: Wire<Practice>[] = ELIGIBLE) {
	const starts: StartRequest[] = [];
	const repositoryReads: URL[] = [];
	server.use(
		...adminHandlers(),
		http.get("*/workspaces/:workspaceSlug/practices/scans", () => HttpResponse.json(scanPage([]))),
		http.get("*/workspaces/:workspaceSlug/practices/items", ({ request }) => {
			repositoryReads.push(new URL(request.url));
			return HttpResponse.json({
				content: [wireRepository("300"), wireRepository("301", "artemis")],
				number: 0,
				size: 200,
				totalElements: 2,
				totalPages: 1,
				last: true,
			});
		}),
		http.get("*/workspaces/:workspaceSlug/practices", () => HttpResponse.json(practices)),
		http.post("*/workspaces/:workspaceSlug/practices/scans", async ({ request }) => {
			const body: unknown = await request.json();
			starts.push({ key: request.headers.get("Idempotency-Key"), body });
			return answer();
		}),
		http.get("*/workspaces/:workspaceSlug/practices/scans/:scanId", ({ params }) =>
			HttpResponse.json(wireDetail(wireScan({ id: String(params.scanId), status: "QUEUED" }))),
		),
		http.get("*/workspaces/:workspaceSlug/practices/scans/:scanId/report", ({ params }) =>
			HttpResponse.json(wireReport(wireScan({ id: String(params.scanId), status: "QUEUED" }))),
		),
		http.get("*/workspaces/:workspaceSlug/practices/scans/:scanId/report/items", () =>
			HttpResponse.json({ content: [], nextCursor: null, hasNext: false }),
		),
		http.get("*/workspaces/:workspaceSlug/practices/scans/:scanId/report/problems", () =>
			HttpResponse.json({ content: [], nextCursor: null, hasNext: false }),
		),
	);
	return { starts, repositoryReads };
}

const refusal = (status: number, detail: string, extra: Record<string, string> = {}) =>
	HttpResponse.json(
		{ type: "about:blank", title: "Refused", status, detail, ...extra },
		{ status, headers: { "Content-Type": "application/problem+json" } },
	);

async function openForm() {
	const result = renderRouteAtWithRouter(FORM_URL);
	const drawer = await screen.findByRole("dialog", undefined, ROUTE_RENDER_WAIT);
	await within(drawer).findByRole("button", { name: "Start scan" }, ROUTE_RENDER_WAIT);
	return { ...result, drawer };
}

describe("start scan route", () => {
	it("offers the repository, the default branch, only scan practices, a budget and reuse", async () => {
		const { starts } = mockStartScan(() => HttpResponse.json(wireScan()));
		const { drawer } = await openForm();
		const user = userEvent.setup();

		const repository = within(drawer).getByRole("combobox", { name: "Repository" });
		expect(repository.textContent).toContain("ls1intum/hephaestus");
		const branch = within(drawer).getByRole("textbox", { name: "Branch" });
		expect(branch.getAttribute("value")).toBe("Default branch");
		expect(branch.hasAttribute("readonly")).toBe(true);
		expect(within(drawer).getByRole("textbox", { name: "Budget (US dollars)" })).toHaveProperty(
			"value",
			"10",
		);
		const reuse = within(drawer).getByRole("checkbox", { name: "Reuse unchanged results" });
		expect(reuse.getAttribute("aria-checked")).toBe("true");

		const practiceBoxes = within(
			within(drawer).getByRole("group", { name: "Practices" }),
		).getAllByRole("checkbox");
		expect(practiceBoxes.map((box) => box.getAttribute("aria-checked"))).toStrictEqual([
			"true",
			"true",
		]);
		expect(within(drawer).queryByText("Pull requests say why")).toBeNull();
		expect(within(drawer).queryByText("Designs are reviewed")).toBeNull();
		within(drawer).getByText(/2 of 2 selected/u);

		for (const box of practiceBoxes) {
			await user.click(box);
		}
		await user.click(within(drawer).getByRole("button", { name: "Start scan" }));
		await within(drawer).findByText("Select at least one practice.");
		expect(starts).toHaveLength(0);
	});

	it("explains how to include practices in scans when none can be scanned", async () => {
		mockStartScan(
			() => HttpResponse.json(wireScan()),
			ELIGIBLE.filter((practice) => practice.slug === "pr-description"),
		);
		renderRouteAtWithRouter(FORM_URL);

		const drawer = await screen.findByRole("dialog", undefined, ROUTE_RENDER_WAIT);
		await within(drawer).findByText(
			"No practice is included in scans",
			undefined,
			ROUTE_RENDER_WAIT,
		);
		expect(
			within(drawer).getByRole("link", { name: "Open Practice setup" }).getAttribute("href"),
		).toBe("/w/acme/admin/practices");
		expect(within(drawer).queryByRole("button", { name: "Start scan" })).toBeNull();
	});

	it("sends exactly one request for a double press, then opens the started scan", async () => {
		const answered = deferred();
		const startedId = "b0000000-0000-4000-8000-000000000009";
		const { starts } = mockStartScan(async () => {
			await answered.promise;
			return HttpResponse.json(wireScan({ id: startedId, status: "QUEUED" }));
		});
		const { drawer, router } = await openForm();
		const user = userEvent.setup();

		const submit = within(drawer).getByRole("button", { name: "Start scan" });
		await user.dblClick(submit);
		await within(drawer).findByRole("button", { name: "Starting scan…" });
		expect(within(drawer).getByRole("button", { name: "Starting scan…" })).toHaveProperty(
			"disabled",
			true,
		);
		await waitFor(() => expect(starts).toHaveLength(1));

		answered.resolve();
		await waitFor(() =>
			expect(router.state.location.pathname).toBe(`/w/acme/admin/practices/scans/${startedId}`),
		);
		expect(starts).toHaveLength(1);
		const [start] = starts;
		expect(start?.key).toMatch(UUID);
		expect(start?.body).toStrictEqual({
			repositoryItemId: "300",
			ref: null,
			expectedVersion: null,
			practiceSlugs: ["comment-quality", "error-handling"],
			budgetUsd: "10",
			reuse: true,
		});
	});

	it("repeats the same key on an explicit retry after a server failure, and a new one for a changed draft", async () => {
		const { starts } = mockStartScan(() => refusal(500, "The server had a problem."));
		const { drawer } = await openForm();
		const user = userEvent.setup();

		await user.click(within(drawer).getByRole("button", { name: "Start scan" }));
		await within(drawer).findByText("We could not start the scan");
		expect(within(drawer).getByRole("textbox", { name: "Budget (US dollars)" })).toHaveProperty(
			"value",
			"10",
		);

		await user.click(within(drawer).getByRole("button", { name: "Start scan" }));
		await waitFor(() => expect(starts).toHaveLength(2));
		expect(starts[1]?.key).toBe(starts[0]?.key);

		const budget = within(drawer).getByRole("textbox", { name: "Budget (US dollars)" });
		await user.clear(budget);
		await user.type(budget, "12.5");
		await user.click(within(drawer).getByRole("button", { name: "Start scan" }));
		await waitFor(() => expect(starts).toHaveLength(3));
		expect(starts[2]?.key).not.toBe(starts[0]?.key);
		expect(starts[2]?.body).toMatchObject({ budgetUsd: "12.5" });
	});

	it("links to the scan already running when the workspace has one", async () => {
		const activeScanId = "c0000000-0000-4000-8000-000000000003";
		const { starts } = mockStartScan(() =>
			refusal(409, "A scan is already active in this workspace.", { activeScanId }),
		);
		const { drawer, router } = await openForm();
		const user = userEvent.setup();

		await user.click(within(drawer).getByRole("button", { name: "Start scan" }));
		const open = await within(drawer).findByRole("link", { name: "Open the running scan" });
		expect(open.getAttribute("href")).toBe(`/w/acme/admin/practices/scans/${activeScanId}`);
		expect(starts).toHaveLength(1);

		await user.click(open);
		await waitFor(() =>
			expect(router.state.location.pathname).toBe(`/w/acme/admin/practices/scans/${activeScanId}`),
		);
	});

	it("reads again after a version conflict without starting again, and expects that version next", async () => {
		const currentVersion = "9e8d7c6b5a4f3e2d1c0b9a8f7e6d5c4b3a2f1e0d";
		const { starts, repositoryReads } = mockStartScan(
			firstThen(
				() => refusal(409, "The repository version changed.", { currentVersion }),
				() => HttpResponse.json(wireScan({ id: SCAN_ID, status: "QUEUED" })),
			),
		);
		const { drawer, router } = await openForm();
		const user = userEvent.setup();
		const readsBefore = repositoryReads.length;

		await user.click(within(drawer).getByRole("button", { name: "Start scan" }));
		await within(drawer).findByText("The repository changed");
		within(drawer).getByText(/moved to version 9e8d7c6/u);
		await waitFor(() => expect(repositoryReads.length).toBeGreaterThan(readsBefore));
		expect(starts).toHaveLength(1);
		expect(router.state.location.pathname).toBe("/w/acme/admin/practices/scans");

		await user.click(within(drawer).getByRole("button", { name: "Start scan" }));
		await waitFor(() => expect(starts).toHaveLength(2));
		expect(starts[1]?.body).toMatchObject({ expectedVersion: currentVersion });
		expect(starts[1]?.key).not.toBe(starts[0]?.key);
	});

	it("shows the server's refusal in place, such as an AI model that is not ready", async () => {
		const { starts } = mockStartScan(() =>
			refusal(422, "Assign a ready AI model to practice reviews before you start a scan."),
		);
		const { drawer } = await openForm();
		const user = userEvent.setup();

		await user.click(within(drawer).getByRole("button", { name: "Start scan" }));
		await within(drawer).findByText("The scan did not start");
		within(drawer).getByText(
			"Assign a ready AI model to practice reviews before you start a scan.",
		);

		// A definitive refusal is not retried under its key: the next press is a new request.
		await user.click(within(drawer).getByRole("button", { name: "Start scan" }));
		await waitFor(() => expect(starts).toHaveLength(2));
		expect(starts[1]?.key).not.toBe(starts[0]?.key);
	});
});

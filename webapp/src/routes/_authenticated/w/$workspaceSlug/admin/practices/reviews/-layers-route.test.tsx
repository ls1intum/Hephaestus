import { act, cleanup, fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { afterEach, assert, beforeEach, describe, expect, it, vi } from "vitest";

import {
	feedbackDetail,
	observationDetail,
	practiceCounts,
	reviewFeedback,
	reviewJob,
	reviewObservations,
	reviewPracticeScripts,
	reviewRuns,
} from "@/components/admin/practice-reviews/fixtures";
import {
	LINEAGE_OBS,
	LINEAGE_REVIEW_ID,
	wholeReview,
} from "@/components/admin/practice-reviews/lineage-fixtures";
import {
	reviewHandlers,
	feedbackCountHandlers,
} from "@/components/admin/practice-reviews/story-mock-server";
import { forgetTraceDisplay } from "@/components/admin/practice-reviews/trace-display";
import { stackInSearch } from "@/components/layout/detail-drawer/detail-stack";
import { tracedArtifacts } from "@/components/practice-trace/fixtures";
import { workspaceListItem } from "@/mocks/fixtures/workspaces";
import { server } from "@/mocks/server";
import { deferred, sleep } from "@/test/async";
import { ROUTE_RENDER_WAIT, renderRouteAtWithRouter } from "@/test/router-harness";

// Mounting the real route pulls in the whole admin layout and its lazy modules; the timeout is a
// deadlock backstop, not a budget these renders were meant to fit inside.
vi.setConfig({ testTimeout: 30_000 });

function readsOldReview(url: URL) {
	return (
		url.pathname.endsWith(`/agents/jobs/${LINEAGE_REVIEW_ID}`) ||
		url.searchParams.get("agentJobId") === LINEAGE_REVIEW_ID
	);
}

function heldWorkspaceRead(until: Promise<void>) {
	return async () => {
		await until;
		return HttpResponse.json({});
	};
}

const REVIEWS = "/w/acme/admin/practices/reviews";
/**
 * Real version-4 UUIDs: the lists' schema drops a tracked id that is not one, as the fixtures' are.
 * The fixture's review, observation and piece of feedback are served under them.
 */
const REVIEW = "3f2b6c1e-8a4d-4f0b-9c7e-2d5a1b0c9e84";
const OBSERVATION_ROW = "9a1d3c5e-7b2f-4e6a-8c0d-1f3b5d7e9a2c";
const FEEDBACK_ROW = "4c2e8a1b-6d3f-4a5c-9e7b-0f1d2c3b4a5e";
/** The observation of the traced chain that a step opens. */
const OBSERVATION = LINEAGE_OBS.cache404;
const TRACE = `trace:review:${REVIEW}`;

const fixtureRun = reviewRuns.find((run) => run.id === LINEAGE_REVIEW_ID);
const [fixtureObservation] = reviewObservations;
const [fixtureFeedback] = reviewFeedback;

function page(content: unknown[]) {
	return HttpResponse.json({
		content,
		page: { number: 0, size: 25, totalElements: content.length, totalPages: 1 },
	});
}

const requested: URL[] = [];
const record = ({ request }: { request: Request }) => {
	requested.push(new URL(request.url));
};

function requestsTo(pathname: string): URL[] {
	return requested.filter((url) => url.pathname === `/workspaces/acme${pathname}`);
}

beforeEach(() => {
	forgetTraceDisplay();
	requested.length = 0;
	server.events.on("request:start", record);
	server.use(
		http.get("*/workspaces/:workspaceSlug/members/me", () =>
			HttpResponse.json({ role: "ADMIN", userId: 1, userLogin: "ada", userName: "Ada" }),
		),
		// Every trace tells the whole review the fixture's records sit in.
		http.get("*/workspaces/:workspaceSlug/practices/reviews/lineage", () =>
			HttpResponse.json(wholeReview),
		),
		http.get("*/workspaces/:workspaceSlug/practices/reviews", () =>
			page([{ ...fixtureRun, id: REVIEW }]),
		),
		http.get("*/workspaces/:workspaceSlug/practices/reviews/observations", () =>
			page([{ ...fixtureObservation, id: OBSERVATION_ROW, agentJobId: REVIEW }]),
		),
		http.get("*/workspaces/:workspaceSlug/practices/reviews/feedback", () =>
			page([{ ...fixtureFeedback, id: FEEDBACK_ROW, agentJobId: REVIEW }]),
		),
		http.get("*/workspaces/:workspaceSlug/agents/jobs/:jobId", ({ params }) =>
			HttpResponse.json({ ...reviewJob(LINEAGE_REVIEW_ID), id: String(params.jobId) }),
		),
		http.get(
			"*/workspaces/:workspaceSlug/practices/reviews/observations/:observationId",
			({ params }) =>
				HttpResponse.json({ ...observationDetail(OBSERVATION), id: String(params.observationId) }),
		),
		...feedbackCountHandlers(),
		http.get("*/workspaces/:workspaceSlug/practices/reviews/feedback/:feedbackId", ({ params }) =>
			HttpResponse.json({
				...feedbackDetail(String(fixtureFeedback?.id)),
				id: String(params.feedbackId),
			}),
		),
		...reviewHandlers(),
	);
});

afterEach(() => {
	server.events.removeListener("request:start", record);
});

/** The trace in front: the wide sheet with the story of the record it was opened for. */
async function traceSheet() {
	const story = await screen.findByRole(
		"group",
		{ name: "How this came about" },
		ROUTE_RENDER_WAIT,
	);
	const sheet = story.closest<HTMLElement>('[role="dialog"]');
	assert(sheet);
	return sheet;
}

/** The addressed front level, after its parent has entered and its own heading is visible. */
async function frontSheet(levels: number) {
	return waitFor(() => {
		const sheet = document.querySelector<HTMLElement>(
			`[role="dialog"][data-detail-depth="${levels - 1}"]`,
		);
		assert(sheet);
		within(sheet).getByRole("heading", { level: 2 });
		return sheet;
	}, ROUTE_RENDER_WAIT);
}

/** A sheet links out to the provider or to a setting, never to another record of Practice reviews. */
function expectNoRecordLinks(sheet: HTMLElement) {
	for (const link of within(sheet).queryAllByRole("link")) {
		expect(String(link.getAttribute("href"))).not.toMatch(/\/admin\/practices\/reviews/u);
	}
}

/** Escape where focus is, as a reader presses it. */
function pressEscape() {
	fireEvent.keyDown(document.activeElement ?? document.body, { key: "Escape" });
}

/** Where the reader started: the key of the step marked "You started here", whose control is current. */
function startedAt(sheet: HTMLElement): string | undefined {
	return sheet.querySelector<HTMLElement>('[data-step-key]:has(> [aria-current="step"])')?.dataset
		.stepKey;
}

describe("layer 0: a row's own record, Track and Trace", () => {
	it("opens a review's sheet from its row: the review alone, with no story and no record links", async () => {
		const { router } = renderRouteAtWithRouter(`${REVIEWS}/runs`);
		const [open] = await screen.findAllByRole(
			"link",
			{ name: /^Open review of #/u },
			ROUTE_RENDER_WAIT,
		);
		assert(open);
		await userEvent.click(open);

		const sheet = await screen.findByRole("dialog", undefined, ROUTE_RENDER_WAIT);
		await waitFor(() =>
			expect(stackInSearch(router.state.location.search.detail)).toStrictEqual([
				expect.stringMatching(/^review:/u),
			]),
		);
		await within(sheet).findByRole("heading", { name: "State and history" }, ROUTE_RENDER_WAIT);
		within(sheet).getByRole("heading", { name: "Technical" });
		expect(within(sheet).queryByRole("group", { name: "How this came about" })).toBeNull();
		expectNoRecordLinks(sheet);
	});

	it("tracks an observation from its row: every table narrows to it, and the bar names it", async () => {
		const first = { ...fixtureObservation, id: OBSERVATION_ROW, agentJobId: REVIEW };
		assert(fixtureObservation);
		const { router } = renderRouteAtWithRouter(`${REVIEWS}/observations`);
		// The loading table is replaced by the loaded one, so the row is found first and its table after.
		const track = await screen.findByRole(
			"button",
			{ name: `Track observation ${fixtureObservation.summary}` },
			ROUTE_RENDER_WAIT,
		);
		expect(track.closest("table")?.getAttribute("aria-label")).toBe("Observations");
		await userEvent.click(track);

		await waitFor(() =>
			expect(router.state.location.search).toMatchObject({
				observationId: first.id,
				agentJobId: first.agentJobId,
				selected: true,
			}),
		);
		expect(router.state.location.search).not.toHaveProperty("detail");
		const bar = await screen.findByRole("group", { name: "Selection" }, ROUTE_RENDER_WAIT);
		within(bar).getByText("Tracking:");
		await within(bar).findByText(
			"A cache miss and a permission failure come back as the same 404",
			undefined,
			ROUTE_RENDER_WAIT,
		);
		// The tracked row's toggle is pressed.
		await waitFor(() =>
			expect(
				// The list is read again for the narrowed scope, so the row is found afresh.
				screen
					.getByRole("button", { name: `Track observation ${fixtureObservation.summary}` })
					.getAttribute("aria-pressed"),
			).toBe("true"),
		);
		await waitFor(() =>
			expect(
				requestsTo("/practices/reviews/observations").at(-1)?.searchParams.get("agentJobId"),
			).toBe(first.agentJobId),
		);
	});

	it("traces a review from its row: the trace opens and the review is tracked", async () => {
		const { router } = renderRouteAtWithRouter(`${REVIEWS}/runs`);
		const [trace] = await screen.findAllByRole(
			"link",
			{ name: /^Trace review of #/u },
			ROUTE_RENDER_WAIT,
		);
		assert(trace);
		await userEvent.click(trace);

		await waitFor(() =>
			expect(router.state.location.search).toMatchObject({
				detail: TRACE,
				agentJobId: REVIEW,
				selected: true,
			}),
		);
		const sheet = await traceSheet();
		sheet.querySelector("[data-trace-verdict]");
		expect(sheet.querySelector<HTMLElement>('[data-here="true"]')?.dataset.stepKey).toBe("review");
		within(sheet).getByText("You started here");
		// The tables behind it are narrowed to the review it traces.
		await screen.findByRole("group", { name: "Selection", hidden: true }, ROUTE_RENDER_WAIT);
	});

	it("traces a piece of feedback from its row, tracking it and its review", async () => {
		const { router } = renderRouteAtWithRouter(`${REVIEWS}/feedback`);
		// The loading table is replaced by the loaded one, so the row is found first and its table after.
		const [trace] = await screen.findAllByRole(
			"link",
			{ name: /^Trace feedback .+ for /u },
			ROUTE_RENDER_WAIT,
		);
		assert(trace);
		expect(trace.closest("table")?.getAttribute("aria-label")).toBe("Feedback");
		await userEvent.click(trace);

		await waitFor(() =>
			expect(router.state.location.search).toMatchObject({
				selected: true,
			}),
		);
		expect(router.state.location.search).toMatchObject({
			detail: `trace:feedback:${FEEDBACK_ROW}`,
			feedbackId: FEEDBACK_ROW,
			agentJobId: REVIEW,
		});
		await traceSheet();
	});

	it("keeps Track and Trace out of the overview's Practices table", async () => {
		const [practice] = practiceCounts;
		assert(practice);
		renderRouteAtWithRouter(REVIEWS);
		await screen.findByRole(
			"link",
			{ name: `Open practice ${practice.practiceName}` },
			ROUTE_RENDER_WAIT,
		);
		expect(
			screen.queryByRole("link", { name: `Trace practice ${practice.practiceName}` }),
		).toBeNull();
		expect(
			screen.queryByRole("button", { name: `Track practice ${practice.practiceName}` }),
		).toBeNull();
	});
});

describe("a review's sheet", () => {
	/** The real route renders the phase boundaries the job endpoint supplies. */
	it("shows each phase of the run between the boundaries the server sent", async () => {
		server.use(
			http.get("*/workspaces/:workspaceSlug/agents/jobs/:jobId", ({ params }) =>
				HttpResponse.json({
					...reviewJob(LINEAGE_REVIEW_ID),
					id: String(params.jobId),
					createdAt: "2026-07-28T13:34:48Z",
					startedAt: "2026-07-28T13:35:00Z",
					executionStartedAt: "2026-07-28T13:35:30Z",
					completedAt: "2026-07-28T13:40:30Z",
				}),
			),
		);

		renderRouteAtWithRouter(`${REVIEWS}/runs?detail=review:${REVIEW}`);
		const sheet = await screen.findByRole("dialog", undefined, ROUTE_RENDER_WAIT);
		const phase = async (label: string) => {
			const term = await within(sheet).findByText(label, { selector: "dt" }, ROUTE_RENDER_WAIT);
			return term.nextElementSibling?.textContent;
		};

		await expect(phase("Wait to start")).resolves.toBe("12 seconds");
		await expect(phase("Preparation")).resolves.toBe("30 seconds");
		await expect(phase("Execution")).resolves.toBe("5 minutes");
	});

	/**
	 * Its own read, by this review's id, so a slow or failed one costs the reader that section alone.
	 * What the section shows of the answer is its stories' to prove.
	 */
	it("asks for what this review's practice scripts did", async () => {
		server.use(
			http.get("*/workspaces/:workspaceSlug/agents/jobs/:jobId/practice-scripts", () =>
				HttpResponse.json([reviewPracticeScripts.found]),
			),
		);

		renderRouteAtWithRouter(`${REVIEWS}/runs?detail=review:${REVIEW}`);
		const sheet = await screen.findByRole("dialog", undefined, ROUTE_RENDER_WAIT);
		await within(sheet).findByRole("region", { name: "Practice scripts" }, ROUTE_RENDER_WAIT);

		expect(requestsTo(`/agents/jobs/${REVIEW}/practice-scripts`)).toHaveLength(1);
	});
});

describe("the trace and a record over it", () => {
	it("opens an event's sheet over the trace, with no links on, and Escape closes it, then the trace", async () => {
		const { router } = renderRouteAtWithRouter(`${REVIEWS}/runs?detail=${TRACE}`);
		const sheet = await traceSheet();
		const dot = within(sheet).getByRole("button", {
			name: /A cache miss and a permission failure come back as the same 404/u,
		});
		await userEvent.click(dot);

		await waitFor(() =>
			expect(stackInSearch(router.state.location.search.detail)).toStrictEqual([
				TRACE,
				`observation:${OBSERVATION}`,
			]),
		);
		const over = await screen.findByRole(
			"dialog",
			{ name: "A cache miss and a permission failure come back as the same 404" },
			ROUTE_RENDER_WAIT,
		);
		// The same sheet as from its row: the observation alone, and no way on to another record.
		expect(within(over).queryByRole("group", { name: "How this came about" })).toBeNull();
		expectNoRecordLinks(over);
		within(over).getByRole("button", { name: "Mark as incorrect" });

		pressEscape();
		await waitFor(
			() => expect(stackInSearch(router.state.location.search.detail)).toStrictEqual([TRACE]),
			ROUTE_RENDER_WAIT,
		);
		await traceSheet();

		pressEscape();
		await waitFor(
			() => expect(router.state.location.search).not.toHaveProperty("detail"),
			ROUTE_RENDER_WAIT,
		);
		await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
	});

	/**
	 * Closing both levels at once from the sheet's path goes back past both entries they were opened
	 * with, so the browser's Back afterwards does not open them again.
	 */
	it("closes a trace and the sheet over it from the path without Back reopening them", async () => {
		const { router } = renderRouteAtWithRouter(`${REVIEWS}/runs`);
		const [trace] = await screen.findAllByRole(
			"link",
			{ name: /^Trace review of #/u },
			ROUTE_RENDER_WAIT,
		);
		assert(trace);
		await userEvent.click(trace);
		const sheet = await traceSheet();
		await userEvent.click(
			within(sheet).getByRole("button", {
				name: /A cache miss and a permission failure come back as the same 404/u,
			}),
		);
		const over = await screen.findByRole(
			"dialog",
			{ name: "A cache miss and a permission failure come back as the same 404" },
			ROUTE_RENDER_WAIT,
		);
		await userEvent.click(
			within(within(over).getByRole("list", { name: "Path" })).getByText("Practice reviews"),
		);
		await waitFor(
			() => expect(router.state.location.search).not.toHaveProperty("detail"),
			ROUTE_RENDER_WAIT,
		);

		router.history.back();
		await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
		expect(router.state.location.search).not.toHaveProperty("detail");
	});

	it("keeps the mark where the reader started while a record opens over the trace", async () => {
		const { router } = renderRouteAtWithRouter(`${REVIEWS}/runs?detail=${TRACE}`);
		const sheet = await traceSheet();
		expect(startedAt(sheet)).toBe("review");
		within(sheet).getByText("You started here");

		await userEvent.click(
			within(sheet).getByRole("button", {
				name: /A cache miss and a permission failure come back as the same 404/u,
			}),
		);
		await waitFor(() =>
			expect(stackInSearch(router.state.location.search.detail)).toStrictEqual([
				TRACE,
				`observation:${OBSERVATION}`,
			]),
		);
		expect(router.state.location.search).not.toHaveProperty("focus");
		expect(startedAt(sheet)).toBe("review");
		expect(sheet.querySelector<HTMLElement>('[data-here="true"]')?.dataset.stepKey).toBe("review");
	});

	it("keeps the mark where it started", async () => {
		renderRouteAtWithRouter(`${REVIEWS}/runs?detail=${TRACE}`);
		const sheet = await traceSheet();
		await userEvent.click(within(sheet).getByRole("button", { name: "Expand all" }));
		expect(startedAt(sheet)).toBe("review");
	});

	it("keeps a sheet over the trace as the deepest level: an address with more keeps the front one", async () => {
		const { router } = renderRouteAtWithRouter(
			`${REVIEWS}/runs?detail=${encodeURIComponent(
				JSON.stringify([TRACE, `review:${REVIEW}`, `observation:${OBSERVATION}`]),
			)}`,
		);
		await waitFor(
			() =>
				expect(stackInSearch(router.state.location.search.detail)).toStrictEqual([
					TRACE,
					`observation:${OBSERVATION}`,
				]),
			ROUTE_RENDER_WAIT,
		);
	});

	/**
	 * Another workspace and person, with its replies held: neither the last workspace's tab counts nor
	 * its chain stand in, and the last chain's review is not read under the new workspace.
	 */
	it("shows no counts or chain of the last workspace while the next one's replies are held", async () => {
		const OTHER = "/workspaces/other";
		const held = deferred<undefined>();
		server.use(
			http.get(`*${OTHER}/practices/reviews`, heldWorkspaceRead(held.promise)),
			http.get(`*${OTHER}/practices/reviews/observations`, heldWorkspaceRead(held.promise)),
			http.get(`*${OTHER}/practices/reviews/feedback`, heldWorkspaceRead(held.promise)),
			http.get(`*${OTHER}/practices/reviews/lineage`, heldWorkspaceRead(held.promise)),
		);
		try {
			const { router } = renderRouteAtWithRouter(`${REVIEWS}/runs`);
			await screen.findByRole("link", { name: "Reviews, 1" }, ROUTE_RENDER_WAIT);
			await act(async () => router.history.push(`${REVIEWS}/runs?detail=${TRACE}`));
			within(await traceSheet()).getByText("You started here");
			await act(async () =>
				router.history.push(
					`/w/other/admin/practices/reviews/runs?detail=${TRACE}&subjectUserId=7`,
				),
			);
			const otherReads = () => requested.filter((url) => url.pathname.startsWith(`${OTHER}/`));
			await waitFor(() => {
				expect(otherReads().some((url) => url.pathname.endsWith("/lineage"))).toBe(true);
				expect(
					otherReads().filter((url) => url.pathname === `${OTHER}/practices/reviews`),
				).not.toHaveLength(0);
			}, ROUTE_RENDER_WAIT);
			const tabs = document.querySelector('nav[aria-label="Practice review sections"]');
			assert(tabs);
			expect(tabs.querySelector('[aria-label^="Reviews,"]')).toBeNull();
			expect(tabs.querySelectorAll('[aria-label$=", 1"]')).toHaveLength(0);
			expect(screen.queryByText("You started here")).toBeNull();
			expect(otherReads().filter(readsOldReview)).toStrictEqual([]);
		} finally {
			await act(async () => held.resolve(undefined));
		}
	});
});

/** One of the trace's "Show" toggles. */
function showToggle(sheet: HTMLElement, name: string): HTMLElement {
	return within(within(sheet).getByRole("group", { name: "Show" })).getByRole("button", { name });
}

describe("what the trace shows", () => {
	it("offers one Show control", async () => {
		renderRouteAtWithRouter(`${REVIEWS}/runs?detail=${TRACE}`);
		const sheet = await traceSheet();
		const pressed = ["Times", "System info", "Siblings", "Connections"].map((name) =>
			showToggle(sheet, name).getAttribute("aria-pressed"),
		);
		expect(pressed).toStrictEqual(["true", "false", "false", "false"]);
	});

	it("applies each toggle to the whole trace at once", async () => {
		renderRouteAtWithRouter(`${REVIEWS}/runs?detail=${TRACE}`);
		const sheet = await traceSheet();
		const steps = within(sheet).getByRole("list", { name: "Steps, oldest first" });
		expect(steps.querySelectorAll("time").length).toBeGreaterThan(1);
		expect(sheet.querySelector("[data-step-facts]")).toBeNull();

		await userEvent.click(showToggle(sheet, "Times"));
		await waitFor(() => expect(steps.querySelectorAll("time")).toHaveLength(0));

		await userEvent.click(showToggle(sheet, "System info"));
		await waitFor(() => expect(sheet.querySelector("[data-step-facts]")).not.toBeNull());
		// Every piece of feedback lists its facts, not only the step in view.
		for (const piece of steps.querySelectorAll('[data-step="feedback"]')) {
			expect(piece.querySelector("ul")).not.toBeNull();
		}

		await userEvent.click(showToggle(sheet, "Connections"));
		await waitFor(() =>
			expect(sheet.querySelectorAll("path[data-connector-line]").length).toBeGreaterThan(0),
		);
	});

	it("remembers the reader's choice for the next trace they open", async () => {
		renderRouteAtWithRouter(`${REVIEWS}/runs?detail=${TRACE}`);
		const first = await traceSheet();
		await userEvent.click(showToggle(first, "Times"));
		await userEvent.click(showToggle(first, "Connections"));
		await waitFor(() =>
			expect(showToggle(first, "Connections").getAttribute("aria-pressed")).toBe("true"),
		);
		cleanup();

		renderRouteAtWithRouter(`${REVIEWS}/feedback?detail=trace:feedback:${FEEDBACK_ROW}`);
		const second = await traceSheet();
		expect(showToggle(second, "Times").getAttribute("aria-pressed")).toBe("false");
		expect(showToggle(second, "Connections").getAttribute("aria-pressed")).toBe("true");
	});

	it("starts from the default where storage is unavailable", async () => {
		const getItem = vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
			throw new Error("Storage is blocked");
		});
		try {
			renderRouteAtWithRouter(`${REVIEWS}/runs?detail=${TRACE}`);
			const sheet = await traceSheet();
			expect(showToggle(sheet, "Times").getAttribute("aria-pressed")).toBe("true");
		} finally {
			getItem.mockRestore();
		}
	});
});

describe("a record's sheet is the record alone", () => {
	/**
	 * The trace is the one place a record's story is told: no sheet, opened from a row or over a
	 * trace, renders a story, a verdict, a way to the whole review or a Trace action.
	 */
	it.each([
		["review", `review:${REVIEW}`],
		["observation", `observation:${OBSERVATION_ROW}`],
		["feedback", `feedback:${FEEDBACK_ROW}`],
		["work", "work:issue:1430"],
		["practice", `practice:${String(practiceCounts[0]?.practiceSlug)}`],
	])("renders no story in the %s sheet, alone or over a trace", async (_kind, level) => {
		for (const detail of [[level], [TRACE, level]]) {
			renderRouteAtWithRouter(
				`${REVIEWS}/runs?detail=${encodeURIComponent(JSON.stringify(detail))}`,
			);
			const sheet = await frontSheet(detail.length);
			await within(sheet).findByRole("heading", { level: 2 }, ROUTE_RENDER_WAIT);
			expect(sheet.querySelector("[data-trace-verdict]")).toBeNull();
			expect(sheet.querySelector("[data-step-key]")).toBeNull();
			expect(within(sheet).queryByRole("link", { name: /^Trace /u })).toBeNull();
			expect(within(sheet).queryByText("You started here")).toBeNull();
			expectNoRecordLinks(sheet);
			cleanup();
		}
	});
});

/**
 * A sheet's body and footer as markup, without the ids React generates, which differ between two
 * renders of the same sheet.
 */
function sheetMarkup(sheet: HTMLElement): string {
	return [
		// The title: a practice sheet's introduction has headings of its own.
		sheet.querySelector('[data-slot="drawer-title"]')?.outerHTML ?? "",
		sheet.querySelector('[data-slot="drawer-body"]')?.outerHTML ?? "",
		sheet.querySelector('[data-slot="drawer-footer"]')?.outerHTML ?? "",
	]
		.join("\n")
		.replaceAll(/ (?:id|for|aria-labelledby|aria-describedby|aria-controls)="[^"]*"/gu, "");
}

/** The sheet in front once it has settled: its record read and nothing left loading. */
async function settledSheet(detail: string[]): Promise<string> {
	renderRouteAtWithRouter(`${REVIEWS}/runs?detail=${encodeURIComponent(JSON.stringify(detail))}`);
	const sheet = await frontSheet(detail.length);
	await within(sheet).findByRole("heading", { level: 2 }, ROUTE_RENDER_WAIT);
	await waitFor(
		() => expect(sheet.querySelector('[data-slot="skeleton"]')).toBeNull(),
		ROUTE_RENDER_WAIT,
	);
	// One more turn for the reads that land after the first ones, such as a review's verdicts.
	await sleep(300);
	const markup = sheetMarkup(sheet);
	cleanup();
	return markup;
}

describe("a sheet over the trace is the sheet from its row", () => {
	/**
	 * The same component, the same sections and the same reads: only the layer differs, which the
	 * header's path and its Back or Close say, and those are left out of the comparison.
	 */
	it.each([
		["review", `review:${REVIEW}`],
		["observation", `observation:${OBSERVATION_ROW}`],
		["feedback", `feedback:${FEEDBACK_ROW}`],
		["work", "work:issue:1430"],
		["practice", `practice:${String(practiceCounts[0]?.practiceSlug)}`],
	])("renders the %s sheet alike from its row and over a trace", async (_kind, level) => {
		const alone = await settledSheet([level]);
		const over = await settledSheet([TRACE, level]);
		expect(alone).toContain("data-sheet-section");
		expect(over).toBe(alone);
	});
});

describe("the trace of work no review started on", () => {
	const queued = {
		...tracedArtifacts[1],
		artifactKind: "scm.issue",
		artifactId: 1430,
		reviewedSignalCount: 0,
		lastSignal: {
			...tracedArtifacts[1]?.lastSignal,
			state: "PENDING",
			stateReason: "PRACTICES_DISABLED",
			stateReasonDescription:
				"Practice reviews are turned off for this workspace. Hephaestus tries again when they are turned on.",
		},
	};

	it("asks for no chain, and says the work is queued while reviews are on", async () => {
		server.use(
			http.get("*/workspaces", () =>
				HttpResponse.json([workspaceListItem("acme", { practicesEnabled: true })]),
			),
			http.get("*/workspaces/:workspaceSlug/practices/trace", () => page([queued])),
		);
		renderRouteAtWithRouter(
			`${REVIEWS}/runs?detail=${encodeURIComponent(JSON.stringify(["trace:work:issue:1430"]))}`,
		);
		const sheet = await traceSheet();
		await waitFor(() =>
			expect(sheet.querySelector("[data-trace-verdict]")?.textContent).toBe(
				"No review has started on this work yet: it is queued for review.",
			),
		);
		expect(sheet.textContent).not.toMatch(/turned off/u);
		expect(requestsTo("/practices/reviews/lineage")).toHaveLength(0);
	});
});

describe("deep links", () => {
	it("opens a review's page address that focused an observation as that observation's trace, keeping the selection and the scope", async () => {
		const { router } = renderRouteAtWithRouter(
			`${REVIEWS}/review/${REVIEW}?focus=observation:${OBSERVATION}&agentJobId=${REVIEW}&selected=true&practiceSlug=thin-controllers&from=2026-08-01&subjectUserId=7`,
		);
		await waitFor(
			() => expect(router.state.location.pathname).toBe(`${REVIEWS}/runs`),
			ROUTE_RENDER_WAIT,
		);
		expect(router.state.location.search).toMatchObject({
			detail: `trace:observation:${OBSERVATION}`,
			agentJobId: REVIEW,
			selected: true,
			practiceSlug: "thin-controllers",
			from: "2026-08-01",
			subjectUserId: 7,
		});
		expect(router.state.location.search).not.toHaveProperty("focus");
		const sheet = await traceSheet();
		await waitFor(() => expect(startedAt(sheet)).toBe(`observation:${OBSERVATION}`));
	});

	it("opens an old review level as the review's sheet", async () => {
		const { router } = renderRouteAtWithRouter(`${REVIEWS}/observations?detail=review:${REVIEW}`);
		const sheet = await screen.findByRole("dialog", undefined, ROUTE_RENDER_WAIT);
		await within(sheet).findByRole("heading", { name: "State and history" }, ROUTE_RENDER_WAIT);
		expect(router.state.location.pathname).toBe(`${REVIEWS}/observations`);
		expect(stackInSearch(router.state.location.search.detail)).toStrictEqual([`review:${REVIEW}`]);
	});

	it("drops a step an old address still names", async () => {
		const { router } = renderRouteAtWithRouter(
			`${REVIEWS}/runs?detail=review:${REVIEW}&focus=observations`,
		);
		await screen.findByRole("dialog", undefined, ROUTE_RENDER_WAIT);
		expect(router.state.location.search).not.toHaveProperty("focus");
	});
});

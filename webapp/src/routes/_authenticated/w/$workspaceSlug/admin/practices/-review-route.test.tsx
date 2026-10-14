import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http, type PathParams } from "msw";
import { assert, describe, expect, it, vi } from "vitest";

import type {
	PracticeGroup,
	PracticeReviewSettings,
	ReviewSchedule,
	SetFeedbackModeRequest,
	SetReviewScheduleEnabledRequest,
	UpdatePracticeReviewSettingsRequest,
	WorkspaceMembership,
	WorkspaceReviewScope,
} from "@/api/types.gen";
import { buildFeedbackModeFixture } from "@/components/admin/practices/practice-feedback-mode/fixtures";
import {
	adaAccount,
	reviewSchedule,
	reviewSettings,
} from "@/components/admin/practices/review/fixtures";
import { server } from "@/mocks/server";
import { ROUTE_RENDER_WAIT, renderRouteAt } from "@/test/router-harness";

vi.setConfig({ testTimeout: 20_000 });

const fixture = buildFeedbackModeFixture({
	workspaceDefault: "APPROVE_FIRST",
	groups: [
		{
			slug: "hygiene",
			name: "Hygiene",
			practices: [{ name: "States the motivation" }, { name: "Links the issue", override: "OFF" }],
		},
	],
});

const membership = (overrides: Partial<WorkspaceMembership>): WorkspaceMembership => ({
	accountId: adaAccount.id,
	account: adaAccount,
	role: "ADMIN",
	joinedAt: new Date("2026-01-01T00:00:00Z"),
	leftAt: null,
	hidden: false,
	reviewSelected: true,
	eligibleForPracticeReview: true,
	...overrides,
});

/** An account nobody has signed in to yet, known only by its GitLab identity. */
const unclaimed = membership({
	accountId: "9",
	role: "MEMBER",
	account: {
		id: "9",
		status: "UNCLAIMED",
		displayName: null,
		avatarUrl: null,
		identities: [{ provider: "GITLAB", login: "grace", displayName: null, avatarUrl: null }],
	},
});

/** `extra` goes first: MSW answers with the first handler that matches, so a test can override one. */
function stubWorkspace(extra: Parameters<typeof server.use>) {
	server.use(
		...extra,
		http.get("*/workspaces/:workspaceSlug/members/me", () => HttpResponse.json(membership({}))),
		http.get("*/workspaces/:workspaceSlug", () =>
			HttpResponse.json({
				id: 1,
				slug: "acme",
				displayName: "Acme",
				practicesEnabled: true,
				practiceReviewAutoTriggerEnabled: true,
				practiceReviewManualTriggerEnabled: true,
			}),
		),
		http.get("*/workspaces/:workspaceSlug/agents", () => HttpResponse.json([])),
		http.get("*/workspaces/:workspaceSlug/members", () =>
			HttpResponse.json([membership({}), unclaimed]),
		),
		http.get("*/workspaces/:workspaceSlug/practices/items", () =>
			HttpResponse.json({
				content: [
					{
						id: "101",
						kind: "scm.repository",
						label: "owner/repo",
						title: null,
						container: null,
						parentId: null,
						path: null,
						provider: "GITHUB",
						removedAt: null,
						url: null,
					},
				],
			}),
		),
		http.get("*/workspaces/:workspaceSlug/practices/review-schedules", () => HttpResponse.json([])),
		http.get("*/workspaces/:workspaceSlug/practices/review-batches", () =>
			HttpResponse.json({ content: [] }),
		),
		http.get("*/workspaces/:workspaceSlug/practices/review-settings", () =>
			HttpResponse.json(fixture.settings),
		),
		http.get("*/workspaces/:workspaceSlug/practices/feedback-modes", () =>
			HttpResponse.json(fixture.rollup),
		),
		http.get("*/workspaces/:workspaceSlug/practice-groups", () =>
			HttpResponse.json(fixture.groups),
		),
		http.get("*/workspaces/:workspaceSlug/practices", () => HttpResponse.json(fixture.practices)),
	);
}

/** Serves `initial` and records every settings write with the If-Match it carried. */
function stubSettings(initial: PracticeReviewSettings) {
	let settings = initial;
	const previews: WorkspaceReviewScope[] = [];
	const patches: { body: UpdatePracticeReviewSettingsRequest; etag: string | null }[] = [];
	const handlers = (widens: boolean) => [
		http.get("*/workspaces/:workspaceSlug/practices/review-settings", () =>
			HttpResponse.json(settings),
		),
		http.post<PathParams, WorkspaceReviewScope>(
			"*/workspaces/:workspaceSlug/practices/review-settings/scope-preview",
			async ({ request }) => {
				previews.push(await request.json());
				return HttpResponse.json({
					current: settings.scopeSummary,
					proposed: { ...settings.scopeSummary, selectedRepositories: 3 },
					widens,
				});
			},
		),
		http.patch<PathParams, UpdatePracticeReviewSettingsRequest>(
			"*/workspaces/:workspaceSlug/practices/review-settings",
			async ({ request }) => {
				const body = await request.json();
				patches.push({ body, etag: request.headers.get("If-Match") });
				settings = {
					...settings,
					etag: `"${patches.length}"`,
					reviewScope: body.reviewScope ?? settings.reviewScope,
					generatedPaths: { ...settings.generatedPaths, ...body.generatedPaths },
				};
				return HttpResponse.json(settings);
			},
		),
	];
	return { previews, patches, handlers };
}

async function openPractice(name: string): Promise<HTMLElement> {
	await userEvent.click(
		await screen.findByRole("button", { name: /^Hygiene/u }, ROUTE_RENDER_WAIT),
	);
	const label = await screen.findByText(name);
	const row = label.closest("li");
	assert(row instanceof HTMLElement, "Practice row not rendered");
	return row;
}

describe("review route", () => {
	it("answers 'what is this workspace doing' from the rollup rather than from the rows", async () => {
		stubWorkspace([]);

		renderRouteAt("/w/acme/admin/practices/review");

		await screen.findByRole("heading", { name: "Review settings" }, ROUTE_RENDER_WAIT);
		await screen.findByRole("button", { name: /^Hygiene/u }, ROUTE_RENDER_WAIT);

		screen.getByText(
			"2 practices: 1 set to off and 1 set to approve first. 1 practice set by hand.",
		);
		expect(screen.queryByText("States the motivation")).toBeNull();
	});

	/**
	 * The whole reason the page hands its section bodies down as elements instead of composing them:
	 * an element that is never mounted runs no hooks, so a reader who opens one tab pays for one
	 * tab's data. Asserted at the network, because the DOM cannot tell a section that rendered nothing
	 * from one that never ran.
	 */
	it("asks for a section's data only once its tab is opened", async () => {
		const requested: string[] = [];
		const recorded = (path: string, body: Parameters<typeof HttpResponse.json>[0]) =>
			http.get(path, ({ request }) => {
				requested.push(new URL(request.url).pathname);
				return HttpResponse.json(body);
			});
		stubWorkspace([
			recorded("*/workspaces/:workspaceSlug/practices/feedback-modes", fixture.rollup),
			recorded("*/workspaces/:workspaceSlug/practices/review-batches", { content: [] }),
		]);

		renderRouteAt("/w/acme/admin/practices/review?section=past-work");

		await waitFor(
			() => expect(requested.some((path) => path.endsWith("/review-batches"))).toBe(true),
			ROUTE_RENDER_WAIT,
		);
		expect(requested.some((path) => path.endsWith("/feedback-modes"))).toBe(false);

		await userEvent.click(screen.getByRole("tab", { name: "Feedback mode" }));

		await waitFor(
			() => expect(requested.some((path) => path.endsWith("/feedback-modes"))).toBe(true),
			ROUTE_RENDER_WAIT,
		);
	});

	it("opens the section a deep link names", async () => {
		stubWorkspace([]);

		renderRouteAt("/w/acme/admin/practices/review?section=past-work");

		const tab = await screen.findByRole("tab", { name: "Past work" }, ROUTE_RENDER_WAIT);
		expect(tab.getAttribute("aria-selected")).toBe("true");
		expect(screen.queryByRole("tabpanel", { name: "Feedback mode" })).toBeNull();
	});

	/**
	 * Null is the inherit instruction; Off is a choice like any other. Sending Off to mean "inherit",
	 * or omitting the field, would turn a reset into a silent stop of every destination.
	 */
	it("overrides a practice's feedback mode and makes it inherit again with an explicit null", async () => {
		const bodies: SetFeedbackModeRequest[] = [];
		stubWorkspace([
			http.patch<PathParams, SetFeedbackModeRequest>(
				"*/workspaces/:workspaceSlug/practices/:practiceSlug/feedback-mode",
				async ({ request, params }) => {
					bodies.push(await request.json());
					const practice = fixture.practices.find(
						(candidate) => candidate.slug === params.practiceSlug,
					);
					return HttpResponse.json(practice);
				},
			),
		]);

		renderRouteAt("/w/acme/admin/practices/review");

		// `hidden: true`: jsdom runs no layout, so Base UI leaves the opened accordion panel carrying
		// the attribute it strips once the panel has a height.
		const inherited = await openPractice("States the motivation");
		await userEvent.click(within(inherited).getByRole("radio", { name: "Off", hidden: true }));
		await waitFor(() => expect(bodies).toStrictEqual([{ feedbackMode: "OFF" }]));

		const overridden = await openPractice("Links the issue");
		await userEvent.click(
			within(overridden).getByRole("button", {
				name: "Use the default feedback mode for Links the issue",
				hidden: true,
			}),
		);
		await waitFor(() => expect(bodies[1]).toStrictEqual({ feedbackMode: null }));
	});

	it("overrides a group's feedback mode and makes it inherit again", async () => {
		const bodies: SetFeedbackModeRequest[] = [];
		const [hygiene] = fixture.groups;
		assert(hygiene);
		/** What the server answers after each write, in order: Off chosen here, then inherited again. */
		const answers: PracticeGroup[] = [
			{ ...hygiene, feedbackMode: { effective: "OFF", override: "OFF", source: "GROUP" } },
			hygiene,
		];
		let group = hygiene;
		stubWorkspace([
			http.patch<PathParams, SetFeedbackModeRequest>(
				"*/workspaces/:workspaceSlug/practice-groups/:groupSlug/feedback-mode",
				async ({ request }) => {
					bodies.push(await request.json());
					const answer = answers[bodies.length - 1];
					assert(answer, "No answer for this write");
					group = answer;
					return HttpResponse.json(group);
				},
			),
			http.get("*/workspaces/:workspaceSlug/practice-groups", () => HttpResponse.json([group])),
		]);

		renderRouteAt("/w/acme/admin/practices/review");

		const ladder = await screen.findByRole(
			"radiogroup",
			{ name: "Feedback mode for Hygiene" },
			ROUTE_RENDER_WAIT,
		);
		await userEvent.click(within(ladder).getByRole("radio", { name: "Off" }));
		await waitFor(() => expect(bodies).toStrictEqual([{ feedbackMode: "OFF" }]));

		await userEvent.click(
			await screen.findByRole(
				"button",
				{ name: "Use the default feedback mode for Hygiene" },
				ROUTE_RENDER_WAIT,
			),
		);
		await waitFor(() => expect(bodies[1]).toStrictEqual({ feedbackMode: null }));
	});

	it("saves a selected scope with nobody in it as nobody, never as everyone", async () => {
		const settings = stubSettings(reviewSettings());
		stubWorkspace(settings.handlers(false));

		renderRouteAt("/w/acme/admin/practices/review?section=automated-review");

		await userEvent.click(
			await screen.findByRole("radio", { name: "Selected people" }, ROUTE_RENDER_WAIT),
		);
		screen.getByText("No people are selected");
		await userEvent.click(screen.getByRole("button", { name: "Review changes" }));

		const nobody = {
			people: "SELECTED",
			repositories: "ALL",
			accountIds: [],
			selectedRepositories: [],
		} satisfies WorkspaceReviewScope;
		await waitFor(() =>
			expect(settings.patches).toStrictEqual([{ body: { reviewScope: nobody }, etag: '"0"' }]),
		);
		expect(settings.previews).toStrictEqual([nobody]);
		expect(screen.queryByRole("alertdialog")).toBeNull();
	});

	it("widens the review scope only after the preview is confirmed, with the read ETag", async () => {
		const persisted = reviewSettings({
			etag: '"7"',
			reviewScope: {
				people: "SELECTED",
				repositories: "SELECTED",
				accountIds: ["9"],
				selectedRepositories: [{ repositoryItemId: "101", baseBranches: ["main"] }],
			},
		});
		const settings = stubSettings(persisted);
		stubWorkspace(settings.handlers(true));

		renderRouteAt("/w/acme/admin/practices/review?section=automated-review");

		// An unclaimed account is a member like any other, named by its workspace identity.
		await screen.findByRole(
			"button",
			{ name: "Remove grace from the review scope" },
			ROUTE_RENDER_WAIT,
		);
		await screen.findByText("owner/repo");

		await userEvent.click(screen.getByRole("radio", { name: "All monitored repositories" }));
		await userEvent.click(screen.getByRole("button", { name: "Review changes" }));

		const dialog = await screen.findByRole("alertdialog", {}, ROUTE_RENDER_WAIT);
		expect(settings.patches).toStrictEqual([]);
		await userEvent.click(within(dialog).getByRole("button", { name: "Widen review scope" }));

		const widened = {
			...persisted.reviewScope,
			repositories: "ALL",
		} satisfies WorkspaceReviewScope;
		await waitFor(() =>
			expect(settings.patches).toStrictEqual([{ body: { reviewScope: widened }, etag: '"7"' }]),
		);
		expect(settings.previews).toStrictEqual([widened]);
	});

	it("pauses and resumes a recurring check with the schedule's version as its ETag", async () => {
		let schedule: ReviewSchedule = reviewSchedule({ version: "3" });
		const writes: { body: SetReviewScheduleEnabledRequest; etag: string | null }[] = [];
		stubWorkspace([
			http.get("*/workspaces/:workspaceSlug/practices/review-schedules", () =>
				HttpResponse.json([schedule]),
			),
			http.patch<PathParams, SetReviewScheduleEnabledRequest>(
				"*/workspaces/:workspaceSlug/practices/review-schedules/:scheduleId",
				async ({ request }) => {
					const body = await request.json();
					writes.push({ body, etag: request.headers.get("If-Match") });
					schedule = {
						...schedule,
						enabled: body.enabled === true,
						version: String(Number(schedule.version) + 1),
					};
					return HttpResponse.json(schedule);
				},
			),
		]);

		renderRouteAt("/w/acme/admin/practices/review?section=automated-review");

		await userEvent.click(
			await screen.findByRole(
				"button",
				{ name: "Pause checking pull or merge requests" },
				ROUTE_RENDER_WAIT,
			),
		);
		await userEvent.click(
			await screen.findByRole(
				"button",
				{ name: "Resume checking pull or merge requests" },
				ROUTE_RENDER_WAIT,
			),
		);

		await waitFor(() =>
			expect(writes).toStrictEqual([
				{ body: { enabled: false }, etag: '"3"' },
				{ body: { enabled: true }, etag: '"4"' },
			]),
		);
	});

	it("saves and clears generated patterns through the settings API with its ETag", async () => {
		const settings = stubSettings(reviewSettings({ generatedPaths: { "owner/repo": ["old/**"] } }));
		stubWorkspace(settings.handlers(false));

		renderRouteAt("/w/acme/admin/practices/review?section=automated-review");
		const input = await screen.findByRole("textbox", { name: "owner/repo" }, ROUTE_RENDER_WAIT);
		await userEvent.clear(input);
		await userEvent.type(input, "generated/**");
		await userEvent.click(
			screen.getByRole("button", { name: "Save generated paths for owner/repo" }),
		);
		await waitFor(
			() =>
				expect(settings.patches).toStrictEqual([
					{ body: { generatedPaths: { "owner/repo": ["generated/**"] } }, etag: '"0"' },
				]),
			ROUTE_RENDER_WAIT,
		);
		await waitFor(() =>
			expect(
				screen.getByRole("button", { name: "Save generated paths for owner/repo" }),
			).toHaveProperty("disabled", true),
		);
		await userEvent.clear(input);
		await userEvent.click(
			screen.getByRole("button", { name: "Save generated paths for owner/repo" }),
		);
		await waitFor(() =>
			expect(settings.patches[1]).toStrictEqual({
				body: { generatedPaths: { "owner/repo": [] } },
				etag: '"1"',
			}),
		);
	});
});

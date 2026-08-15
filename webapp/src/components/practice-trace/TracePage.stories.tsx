import type { Meta, StoryObj } from "@storybook/react-vite";
import { HttpResponse, http } from "msw";
import { expect, screen, userEvent, waitFor, within } from "storybook/test";
import { expectNoPageOverflow } from "@/test/reflow";
import { artifactTrace, documentArtifactTrace, untouchedArtifactTrace } from "./story-mock-data";
import { TracePage } from "./TracePage";

const TRACE_URL = "*/workspaces/:workspaceSlug/practices/trace/:artifactKind/:artifactId";
const REQUEST_URL = "*/workspaces/:workspaceSlug/practices/review-requests";

const traceHandler = http.get(TRACE_URL, () => HttpResponse.json(artifactTrace));

const COOLDOWN_SENTENCE = "This work was reviewed a moment ago, so nothing new was started.";

// Reset by the play function, not only here: a story replayed in the same session would otherwise
// carry the previous run's count and submit on the first click.
let requestCalls = 0;

const resetRequestCalls = () => {
	requestCalls = 0;
};

const refuseThenSubmitHandler = http.post(REQUEST_URL, () => {
	requestCalls += 1;
	return HttpResponse.json(
		requestCalls === 1
			? {
					status: "REFUSED",
					reason: "REQUEST_COOLDOWN_ACTIVE",
					reasonDescription: COOLDOWN_SENTENCE,
				}
			: { status: "SUBMITTED", jobId: "0f2b7c1e-9a3d-4c5b-8e1f-2d6a7b8c9d01" },
	);
});

const meta = {
	title: "Practice trace/Review activity detail",
	component: TracePage,
	parameters: {
		// One MSW worker answers a whole Docs page, so each story gets its own frame until MSW goes.
		docs: { story: { inline: false, height: "600px" } },
		layout: "padded",
		chromatic: { viewports: [320, 768, 1440] },
		msw: { handlers: [http.get(TRACE_URL, () => HttpResponse.json(artifactTrace))] },
	},
	tags: ["autodocs"],
	args: {
		workspaceSlug: "demo",
		artifactKind: "scm.pull_request",
		artifactId: 1423,
		canAdminister: true,
	},
} satisfies Meta<typeof TracePage>;

export default meta;
type Story = StoryObj<typeof meta>;

export const EveryOutcome: Story = {
	play: async ({ canvas }) => {
		await expect(
			await canvas.findByRole("heading", { name: /Member-facing review activity/ }),
		).toBeVisible();
		// Measured and delivered are two axes: this practice was reviewed and still said nothing.
		await expect(canvas.getByText("2 measurements, none sent")).toBeVisible();
		await expect(
			canvas.getByText("This practice is set to measure quietly rather than to speak up."),
		).toBeVisible();
		await expect(canvas.getAllByText("Reviewed")).toHaveLength(2);
		// The word, not `REVIEW_TIER_LABELS.PROPOSE`: reading the registry back would agree with any
		// label it happens to hold, including a renamed one.
		await expect(canvas.getByText("Propose")).toBeVisible();
	},
};

/** The same signal name recurs on every revision, so a row has to point at the occurrence. */
export const SameSignalTwice: Story = {
	play: async ({ canvas, canvasElement }) => {
		await canvas.findByText("Small, reviewable changes");

		const jumpFrom = (practiceName: string) => {
			const row = canvas.getByText(practiceName).closest('[role="listitem"]');
			if (!(row instanceof HTMLElement)) throw new Error(`No row for ${practiceName}`);
			return within(row).getByRole("link", { name: "Jump to: New commits pushed" });
		};
		const skipped = jumpFrom("Small, reviewable changes");
		const lapsed = jumpFrom("Drafts are not left open");
		await expect(skipped).toHaveAttribute("href", "#occurrence-sig-sync-9ab3c410");
		await expect(lapsed).toHaveAttribute("href", "#occurrence-sig-sync-b71d0a52");

		// Not clicked: fragment navigation would move the test runner's own page. What is ours to prove
		// is that each href resolves to a timeline entry that can take focus.
		for (const id of ["occurrence-sig-sync-9ab3c410", "occurrence-sig-sync-b71d0a52"]) {
			const target = canvasElement.ownerDocument.getElementById(id);
			if (!target) throw new Error(`No timeline entry with id ${id}`);
			await expect(target).toHaveAttribute("tabindex", "-1");
			await expect(within(target).getByText("New commits pushed")).toBeVisible();
		}
	},
};

export const SignalsExplainThemselves: Story = {
	play: async ({ canvas }) => {
		// Scoped to the timeline: an occurrence's label also appears as "Rests on" text on every
		// practice row that rests on it, so a page-wide query is ambiguous by design.
		const timeline = within(await canvas.findByRole("region", { name: "What we noticed" }));
		await expect(timeline.getByText("Marked ready for review")).toBeVisible();
		await expect(
			timeline.getByText(
				"This work was reviewed too recently; a later change gets its own review.",
			),
		).toBeVisible();
		await expect(timeline.getByText("It waited too long to be picked up.")).toBeVisible();
	},
};

export const RefusalsLinkToTheirFix: Story = {
	play: async ({ canvas }) => {
		const timeline = within(await canvas.findByRole("region", { name: "What we noticed" }));

		await expect(
			timeline.getByRole("link", { name: "Open Review: When and where" }),
		).toHaveAttribute("href", "/w/demo/admin/practices/review?section=when-and-where");
		await expect(timeline.getAllByRole("link", { name: /^Open |^Set up / })).toHaveLength(1);
	},
};

export const MembersAreOfferedNoAdminLinks: Story = {
	args: { canAdminister: false },
	play: async ({ canvas }) => {
		const timeline = within(await canvas.findByRole("region", { name: "What we noticed" }));
		await expect(
			timeline.getByText("This workspace's review settings turned it away."),
		).toBeVisible();
		await expect(timeline.queryByRole("link", { name: /^Open |^Set up / })).not.toBeInTheDocument();
	},
};

export const NothingWasReviewed: Story = {
	args: { artifactKind: "scm.issue", artifactId: 1430 },
	parameters: {
		msw: { handlers: [http.get(TRACE_URL, () => HttpResponse.json(untouchedArtifactTrace))] },
	},
	play: async ({ canvas }) => {
		await expect(await canvas.findByText("Opened")).toBeVisible();
		await expect(
			canvas.getByText("No practice was watching for this when it happened."),
		).toBeVisible();
		await expect(canvas.getByText("Turned off")).toBeVisible();
	},
};

/** What a version skew between the recorder and this endpoint looks like from the page. */
export const OccurrenceMissingFromTheTimeline: Story = {
	parameters: {
		msw: {
			handlers: [
				http.get(TRACE_URL, () =>
					HttpResponse.json({
						...artifactTrace,
						practices: artifactTrace.practices.map((entry) =>
							entry.practiceSlug === "small-changes"
								? { ...entry, occasionedById: "sig-from-a-newer-server" }
								: entry,
						),
					}),
				),
			],
		},
	},
	play: async ({ canvas }) => {
		const row = (await canvas.findByText("Small, reviewable changes")).closest('[role="listitem"]');
		if (!(row instanceof HTMLElement)) throw new Error("No row for the skipped practice");

		// Scoped to "Rests on": the same signal name also appears in the row's "Starts a review on"
		// list, and a row-wide query cannot say which of the two is the fallback under test.
		const restsOn = within(row).getByText("Rests on").closest("div");
		if (!(restsOn instanceof HTMLElement)) throw new Error("No 'Rests on' entry on the row");
		await expect(within(restsOn).getByText("scm.pull_request.synchronized")).toBeVisible();
		await expect(within(row).queryByRole("link", { name: /^Jump to:/ })).toBeNull();
		// The rows whose occurrence does resolve are untouched, so this is a fallback and not a mode.
		const resolved = canvas.getByText("Drafts are not left open").closest('[role="listitem"]');
		if (!(resolved instanceof HTMLElement)) throw new Error("No row for the lapsed practice");
		await expect(
			within(resolved).getByRole("link", { name: "Jump to: New commits pushed" }),
		).toBeVisible();
	},
};

export const NothingReachedIt: Story = {
	args: { artifactKind: "scm.issue", artifactId: 1430 },
	parameters: {
		msw: {
			handlers: [
				http.get(TRACE_URL, () =>
					HttpResponse.json({ ...untouchedArtifactTrace, signals: [], practices: [] }),
				),
			],
		},
	},
	play: async ({ canvas }) => {
		await expect(await canvas.findByText("Nothing was recorded about this work")).toBeVisible();
		await expect(canvas.getByText("No practice covers this kind of work")).toBeVisible();
		// Named in the reader's words, not as `scm.issue`.
		await expect(canvas.getByText(/runs no practice against issue/)).toBeVisible();
	},
};

export const DocumentHasNoButtonToAsk: Story = {
	args: { artifactKind: "docs.document", artifactId: 512 },
	parameters: {
		msw: { handlers: [http.get(TRACE_URL, () => HttpResponse.json(documentArtifactTrace))] },
	},
	play: async ({ canvas }) => {
		await expect(
			await canvas.findByRole("heading", { name: /Onboarding: your first week/ }),
		).toBeVisible();
		await expect(canvas.getByText("Document")).toBeVisible();
		await expect(canvas.queryByText("docs.document")).not.toBeInTheDocument();
		await expect(canvas.queryByRole("button", { name: "Review this now" })).not.toBeInTheDocument();
	},
};

export const NotFound: Story = {
	parameters: {
		msw: {
			handlers: [
				http.get(TRACE_URL, () =>
					HttpResponse.json(
						{ status: 404, title: "Not Found", detail: "Nothing recorded about this artifact." },
						{ status: 404, headers: { "Content-Type": "application/problem+json" } },
					),
				),
			],
		},
	},
	play: async ({ canvas }) => {
		await expect(
			await canvas.findByText("Couldn't load this work's review activity"),
		).toBeVisible();
	},
};

export const Mobile: Story = {
	parameters: {
		chromatic: { disableSnapshot: true },
		viewport: { defaultViewport: "reflow" },
	},
	play: async ({ canvas }) => {
		await expect(await canvas.findByText("Thin controllers")).toBeVisible();
		await expect(canvas.getByText("Waiting on a connection")).toBeVisible();
		await expectNoPageOverflow();
	},
};

/** A refused ask is a 200 carrying the server's own sentence, not an error. */
export const RefusesTheAskInTheServersWords: Story = {
	parameters: {
		msw: {
			handlers: [
				traceHandler,
				http.post(REQUEST_URL, () =>
					HttpResponse.json({
						status: "REFUSED",
						reason: "REQUESTER_QUOTA_EXHAUSTED",
						reasonDescription:
							"You have asked for as many reviews as an hour allows; the allowance refills.",
					}),
				),
			],
		},
	},
	play: async ({ canvas }) => {
		await userEvent.click(await canvas.findByRole("button", { name: "Review this now" }));
		await expect(await canvas.findByText("No review was started")).toBeVisible();
		const alert = within(await canvas.findByRole("alert"));
		await expect(
			alert.getByText(
				"You have asked for as many reviews as an hour allows; the allowance refills.",
			),
		).toBeVisible();
		// An allowance that refills is not something an admin can go and change.
		await expect(alert.queryByRole("link")).not.toBeInTheDocument();
	},
};

/** The link is keyed on the coded reason, never on the prose the server sent beside it. */
export const RefusalOffersTheFixToAnAdmin: Story = {
	parameters: {
		msw: {
			handlers: [
				traceHandler,
				http.post(REQUEST_URL, () =>
					HttpResponse.json({
						status: "REFUSED",
						reason: "REVIEW_MODEL_UNBOUND",
						reasonDescription: "No AI model is set up to run reviews in this workspace.",
					}),
				),
			],
		},
	},
	play: async ({ canvas }) => {
		await userEvent.click(await canvas.findByRole("button", { name: "Review this now" }));
		const alert = within(await canvas.findByRole("alert"));
		await expect(
			alert.getByText("No AI model is set up to run reviews in this workspace."),
		).toBeVisible();
		await expect(alert.getByRole("link", { name: "Set up a review model" })).toHaveAttribute(
			"href",
			"/w/demo/admin/models",
		);
	},
};

export const RefusalWithheldFixFromAMember: Story = {
	args: { canAdminister: false },
	parameters: {
		msw: {
			handlers: [
				traceHandler,
				http.post(REQUEST_URL, () =>
					HttpResponse.json({
						status: "REFUSED",
						reason: "REVIEW_MODEL_UNBOUND",
						reasonDescription: "No AI model is set up to run reviews in this workspace.",
					}),
				),
			],
		},
	},
	play: async ({ canvas }) => {
		await userEvent.click(await canvas.findByRole("button", { name: "Review this now" }));
		const alert = within(await canvas.findByRole("alert"));
		await expect(
			alert.getByText("No AI model is set up to run reviews in this workspace."),
		).toBeVisible();
		await expect(alert.queryByRole("link")).not.toBeInTheDocument();
	},
};

/**
 * The first attempt has to be refused: a story that only ever submits asserts that a refusal is
 * absent from a page it was never on, which an implementation clearing nothing satisfies too.
 */
export const StartsAReviewAfterARefusal: Story = {
	parameters: {
		msw: { handlers: [traceHandler, refuseThenSubmitHandler] },
	},
	play: async ({ canvas }) => {
		resetRequestCalls();

		await userEvent.click(await canvas.findByRole("button", { name: "Review this now" }));
		await canvas.findByText(COOLDOWN_SENTENCE);

		await userEvent.click(await canvas.findByRole("button", { name: "Review this now" }));
		await waitFor(() => expect(canvas.queryByText(COOLDOWN_SENTENCE)).not.toBeInTheDocument());
		await expect(await canvas.findByRole("button", { name: "Review this now" })).toBeEnabled();
	},
};

export const RefusesSomebodyWithNoStanding: Story = {
	parameters: {
		msw: {
			handlers: [
				traceHandler,
				http.post(REQUEST_URL, () =>
					HttpResponse.json(
						{
							status: 403,
							title: "Access denied",
							detail:
								"Only the work's author or assignees, or a workspace admin, can ask for a review of it.",
						},
						{ status: 403, headers: { "Content-Type": "application/problem+json" } },
					),
				),
			],
		},
	},
	play: async ({ canvas }) => {
		await userEvent.click(await canvas.findByRole("button", { name: "Review this now" }));

		// A 403 is not a decision the workspace made, so it must not reach the refusal alert, which is
		// reserved for a `REFUSED` outcome. The toast is portalled, so it is on `screen`.
		await screen.findByText("Couldn't ask for a review");
		await screen.findByText(
			"Only the work's author or assignees, or a workspace admin, can ask for a review of it.",
		);
		await expect(canvas.queryByRole("alert")).not.toBeInTheDocument();
	},
};

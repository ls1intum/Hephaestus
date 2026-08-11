import type { Meta, StoryObj } from "@storybook/react-vite";
import { HttpResponse, http } from "msw";
import { expect, userEvent, within } from "storybook/test";
import { REVIEW_TIER_LABELS } from "@/lib/review-tiers";
import { expectNoPageOverflow } from "@/test/reflow";
import { artifactTrace, documentArtifactTrace, untouchedArtifactTrace } from "./story-mock-data";
import { TracePage } from "./TracePage";

const TRACE_URL = "*/workspaces/:workspaceSlug/practices/trace/:artifactKind/:artifactId";
const REQUEST_URL = "*/workspaces/:workspaceSlug/practices/review-requests";

const traceHandler = http.get(TRACE_URL, () => HttpResponse.json(artifactTrace));

const meta = {
	title: "Practice trace/Review activity detail",
	component: TracePage,
	parameters: {
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

/** Every outcome the API can report, each with its own reason, none of them behind a toggle. */
export const EveryOutcome: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(
			await canvas.findByRole("heading", { name: /Member-facing review activity/ }),
		).toBeVisible();
		// Measured and delivered are two axes: this practice was reviewed and still said nothing.
		await expect(canvas.getByText("2 measurements, none sent")).toBeVisible();
		await expect(
			canvas.getByText("Measured, kept quiet by the practice's loudness tier"),
		).toBeVisible();
		await expect(canvas.getAllByText("Reviewed")).toHaveLength(2);
		// The tier is named exactly as the catalog names it, from one shared list. This screen used to
		// say "Measure only" for the tier the catalog called "Measure".
		await expect(canvas.getByText(REVIEW_TIER_LABELS.PROPOSE)).toBeVisible();
	},
};

/**
 * Two occurrences share one signal name at different revisions: a practice row has to point at the
 * occurrence, not at the name.
 */
export const SameSignalTwice: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
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

/** The timeline carries the reason a signal never became a review, not just that it did not. */
export const SignalsExplainThemselves: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		// Scoped to the timeline: an occurrence's label also appears as "Rests on" text on every
		// practice row that rests on it, so a page-wide query is ambiguous by design.
		const timeline = within(await canvas.findByRole("region", { name: "What we noticed" }));
		await expect(timeline.getByText("Marked ready for review")).toBeVisible();
		// Names the cause and what follows from it: a cooldown is not the end of the matter, and a
		// reason that stops at the cause leaves the reader thinking it is.
		await expect(
			timeline.getByText(
				"This work was reviewed too recently; a later change gets its own review.",
			),
		).toBeVisible();
		await expect(timeline.getByText("It waited too long to be picked up.")).toBeVisible();
	},
};

/**
 * A refusal that can be undone offers the way to undo it, and one that cannot says nothing extra.
 *
 * <p>This is the whole thesis of the page in one line of markup. "The workspace's review settings
 * turned it away" names a screen an admin has to go and find; a link is that screen. The two quiet
 * reasons beside it are the control: a cooldown expires on its own and a missed deadline is already
 * past, so a settings link there would only invite somebody to widen a limit to fix a non-fault.
 */
export const RefusalsLinkToTheirFix: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		const timeline = within(await canvas.findByRole("region", { name: "What we noticed" }));

		// The accessible name names the destination. "Open review settings" survives being read out of
		// its sentence, in a screen reader's list of the page's links, which "here" does not.
		await expect(timeline.getByRole("link", { name: "Open review settings" })).toHaveAttribute(
			"href",
			"/w/demo/admin/practices/settings",
		);
		await expect(timeline.getAllByRole("link", { name: /^Open |^Set up / })).toHaveLength(1);
	},
};

/**
 * The same page for somebody who cannot open administration: the reasons stay, the links go.
 *
 * <p>Every member of a workspace can read a trace, and most of them are not admins. A link into
 * `/admin` would bounce them off the route guard and back to the workspace home — losing the page
 * they were reading, to be told nothing. The sentence already says everything they can act on.
 */
export const MembersAreOfferedNoAdminLinks: Story = {
	args: { canAdminister: false },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		const timeline = within(await canvas.findByRole("region", { name: "What we noticed" }));
		await expect(
			timeline.getByText("This workspace's review settings turned it away."),
		).toBeVisible();
		await expect(timeline.queryByRole("link", { name: /^Open |^Set up / })).not.toBeInTheDocument();
	},
};

/** Nothing was ever measured here, and the page says why rather than showing an empty screen. */
export const NothingWasReviewed: Story = {
	args: { artifactKind: "scm.issue", artifactId: 1430 },
	parameters: {
		msw: { handlers: [http.get(TRACE_URL, () => HttpResponse.json(untouchedArtifactTrace))] },
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(await canvas.findByText("Opened")).toBeVisible();
		await expect(
			canvas.getByText("No practice was watching for this when it happened."),
		).toBeVisible();
		await expect(canvas.getByText("Turned off")).toBeVisible();
	},
};

/**
 * A practice can name an occurrence this timeline does not carry — what a version skew between the
 * recorder and this endpoint looks like from here. The raw signal name is worse copy than a label
 * and far better than a row that drops the one thing explaining it.
 */
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
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
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

/**
 * An empty state has to be a statement about us — "we never saw it" — rather than a blank page the
 * reader is left to interpret.
 */
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
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(await canvas.findByText("Nothing was recorded about this work")).toBeVisible();
		await expect(canvas.getByText("No practice covers this kind of work")).toBeVisible();
		// Named in the reader's words, not as `scm.issue`.
		await expect(canvas.getByText(/runs no practice against issue/)).toBeVisible();
	},
};

/**
 * A written document: named in the reader's words rather than as `docs.document`, and without the
 * "Review this now" button — a document is reviewed when its source publishes it, and asking for one
 * by hand is refused, so the button could only ever produce an error.
 */
export const DocumentHasNoButtonToAsk: Story = {
	args: { artifactKind: "docs.document", artifactId: 512 },
	parameters: {
		msw: { handlers: [http.get(TRACE_URL, () => HttpResponse.json(documentArtifactTrace))] },
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
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
	play: async ({ canvasElement }) => {
		await expect(
			await within(canvasElement).findByText("Couldn't load this work's review activity"),
		).toBeVisible();
	},
};

export const Mobile: Story = {
	parameters: {
		chromatic: { disableSnapshot: true },
		viewport: { defaultViewport: "reflow" },
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(await canvas.findByText("Thin controllers")).toBeVisible();
		await expect(canvas.getByText("Waiting on a connection")).toBeVisible();
		await expectNoPageOverflow();
	},
};

/**
 * The refusal is the point. A workspace turns an ask down for reasons the person asking can neither
 * see nor fix, so the answer is a 200 carrying a sentence — and that sentence is printed exactly as
 * the server phrased it, because a re-worded copy is how a screen and a support answer start
 * disagreeing about the same refusal.
 */
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
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(await canvas.findByRole("button", { name: "Review this now" }));
		await expect(await canvas.findByText("No review was started")).toBeVisible();
		const alert = within(await canvas.findByRole("alert"));
		await expect(
			alert.getByText(
				"You have asked for as many reviews as an hour allows; the allowance refills.",
			),
		).toBeVisible();
		// An allowance that refills is not something an admin can go and change, so the alert offers
		// nowhere to go. Every refusal getting a link would make the links worth nothing.
		await expect(alert.queryByRole("link")).not.toBeInTheDocument();
	},
};

/**
 * Somebody pressed a button and was told no. That is the reader with the most immediate use for the
 * fix, and until now this alert was the one refusal surface that named none.
 *
 * <p>The server's sentence is still printed exactly as it phrased it — the link is added beside it,
 * keyed on the coded reason, never on the prose.
 */
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
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
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

/** The same refusal for a member: the server's sentence, and no door they cannot open. */
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
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(await canvas.findByRole("button", { name: "Review this now" }));
		const alert = within(await canvas.findByRole("alert"));
		await expect(
			alert.getByText("No AI model is set up to run reviews in this workspace."),
		).toBeVisible();
		await expect(alert.queryByRole("link")).not.toBeInTheDocument();
	},
};

/** A started review clears any earlier refusal instead of leaving a stale explanation on screen. */
export const StartsAReview: Story = {
	parameters: {
		msw: {
			handlers: [
				traceHandler,
				http.post(REQUEST_URL, () =>
					HttpResponse.json({
						status: "SUBMITTED",
						jobId: "0f2b7c1e-9a3d-4c5b-8e1f-2d6a7b8c9d01",
					}),
				),
			],
		},
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(await canvas.findByRole("button", { name: "Review this now" }));
		await expect(await canvas.findByRole("button", { name: "Review this now" })).toBeEnabled();
		await expect(canvas.queryByText("No review was started")).not.toBeInTheDocument();
	},
};

/**
 * Standing on the work is not the same as reaching it, so this one really is a 403 — and the page
 * says so through the error channel rather than pretending the workspace declined.
 */
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
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(await canvas.findByRole("button", { name: "Review this now" }));
		await expect(await canvas.findByRole("button", { name: "Review this now" })).toBeEnabled();
		await expect(canvas.queryByText("No review was started")).not.toBeInTheDocument();
	},
};

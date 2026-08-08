import type { Meta, StoryObj } from "@storybook/react-vite";
import { HttpResponse, http } from "msw";
import { expect, within } from "storybook/test";
import { expectNoPageOverflow } from "@/test/reflow";
import { artifactTrace, untouchedArtifactTrace } from "./story-mock-data";
import { TracePage } from "./TracePage";

const TRACE_URL = "*/workspaces/:workspaceSlug/practices/trace/:artifactKind/:artifactId";

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
		await expect(timeline.getByText("This work was reviewed too recently.")).toBeVisible();
		await expect(timeline.getByText("It waited too long to be picked up.")).toBeVisible();
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
		await expect(canvas.getByText("No practice was active for this kind of work.")).toBeVisible();
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

		await expect(within(row).getByText("scm.pull_request.synchronized")).toBeVisible();
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

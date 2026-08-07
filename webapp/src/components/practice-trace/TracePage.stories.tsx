import type { Meta, StoryObj } from "@storybook/react-vite";
import { HttpResponse, http } from "msw";
import { expect, within } from "storybook/test";
import { expectNoPageOverflow } from "@/test/reflow";
import { TracePage } from "./TracePage";
import { artifactTrace, practiceTraceEntries, untouchedArtifactTrace } from "./story-mock-data";
import { OUTCOME_LABELS } from "./trace-format";

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

/**
 * The visual spec: every outcome the API can report, each with its own reason, none of them hidden
 * behind a toggle. The quiet rows are the ones the reader came for.
 */
export const EveryOutcome: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(
			await canvas.findByRole("heading", { name: /Member-facing review activity/ }),
		).toBeVisible();
		for (const entry of practiceTraceEntries) {
			await expect(canvas.getByText(entry.practiceName)).toBeVisible();
			// The explanation is server-rendered and must appear exactly as sent.
			await expect(canvas.getByText(entry.explanation)).toBeVisible();
		}
		// Measured and delivered are two axes: this practice was reviewed and still said nothing.
		await expect(canvas.getByText("2 measurements, none sent")).toBeVisible();
		await expect(
			canvas.getByText("Measured, kept quiet by the practice's loudness tier"),
		).toBeVisible();
		await expect(canvas.getAllByText(OUTCOME_LABELS.REVIEWED)).toHaveLength(2);
	},
};

/**
 * Two of the occurrences are the same signal name at different revisions. The proof that a practice
 * row now points at a specific *occurrence* rather than at a name: two rows say "New commits pushed"
 * and go to different places, and following one lands focus on the entry it names.
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
		// Same visible label on both rows — the signal name never could tell them apart — and yet the
		// two links go to different occurrences.
		const skipped = jumpFrom("Small, reviewable changes");
		const lapsed = jumpFrom("Drafts are not left open");
		await expect(skipped).toHaveAttribute("href", "#occurrence-sig-sync-9ab3c410");
		await expect(lapsed).toHaveAttribute("href", "#occurrence-sig-sync-b71d0a52");

		// The click itself is the browser's job (fragment navigation focuses a focusable target), and
		// exercising it here would navigate the test runner's own page. What is ours to prove is that
		// each href resolves to a real timeline entry that can take focus.
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
		// Scoped to the timeline: an occurrence's label also appears as the "Rests on" link text on
		// every practice row that rests on it, so a page-wide query is ambiguous by design.
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
		await expect(canvas.getByText(OUTCOME_LABELS.SILENCED)).toBeVisible();
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
		await expect(canvas.getByText(OUTCOME_LABELS.DORMANT)).toBeVisible();
		await expectNoPageOverflow();
	},
};

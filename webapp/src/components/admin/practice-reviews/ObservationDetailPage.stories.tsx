import type { Meta, StoryObj } from "@storybook/react-vite";
import { HttpResponse, http } from "msw";
import { expect, within } from "storybook/test";
import { expectNoPageOverflow } from "@/test/reflow";
import { ObservationDetailPage } from "./ObservationDetailPage";
import { reviewObservationDetail } from "./story-mock-data";
import { reviewHandlers } from "./story-mock-server";

/**
 * Every story here opens a record that exists in the fixture, by id, through the same mock endpoint
 * the list screens use. Hand-patched copies of one detail — `{...detail, claimCurrentness: "STALE"}` —
 * were how a story came to show a stale observation whose own review had never produced one.
 */
const meta = {
	title: "Workspace admin/Practice reviews/Observation details",
	component: ObservationDetailPage,
	parameters: {
		layout: "padded",
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 768, 1440] },
		msw: { handlers: reviewHandlers() },
	},
	tags: ["autodocs"],
	args: {
		workspaceSlug: "demo",
		observationId: reviewObservationDetail.id,
		search: {
			agentJobId: reviewObservationDetail.agentJobId,
			presence: undefined,
			assessment: undefined,
			severity: undefined,
		},
	},
} satisfies Meta<typeof ObservationDetailPage>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * The page an operator reaches when they want to know whether an observation is fair.
 *
 * <p>Everything it used to hide is on it. The review that produced this was a UUID at the bottom of a
 * collapsed "Technical details" drawer and is now a link in the line under the title; the evidence was
 * rendered once as citations and again, immediately below, as `JSON.stringify` of the same object; and
 * an alert announced "AI-generated observation" on a screen where every observation is one.
 */
export const Default: Story = {
	play: async ({ canvas, canvasElement }) => {
		// The page fetches, so the first query has to wait; everything after it is already rendered.
		await canvas.findByRole("heading", {
			name: "A cache miss and a permission failure come back as the same 404",
			level: 2,
		});
		canvas.getByRole("link", { name: "in a review" });
		// The title says what was seen; the reasoning says why it was raised. Neither restates the
		// other — a fixture whose title was its first sentence is what made them look duplicated.
		canvas.getByRole("heading", { name: "Why this was raised", level: 3 });
		await expect(canvas.queryByText(/Hephaestus review/)).not.toBeInTheDocument();
		await expect(canvas.queryByText("AI-generated observation")).not.toBeInTheDocument();
		// No accordion basement, and no second rendering of the evidence as raw JSON.
		await expect(canvas.queryByText("Technical details")).not.toBeInTheDocument();
		await expect(canvasElement.querySelector("code")?.textContent).not.toContain("citations");
		await expectNoPageOverflow();
	},
};

/**
 * The evidence, named once per source in words rather than as a contract id under every quote.
 *
 * The diff citation gets a file coordinate because the server verifies it against the annotated
 * diff; so does the unchanged repository file. The human review thread does not — its line number is
 * an offset into the serialised context file the quote came from, and nothing checks it points at it.
 */
export const EvidenceAcrossSources: Story = {
	play: async ({ canvas }) => {
		await canvas.findByText("3 passages from 3 sources.");
		canvas.getByRole("heading", { name: "The code changes", level: 4 });
		canvas.getByRole("heading", { name: "Files in the repository", level: 4 });
		canvas.getByRole("heading", { name: "Review threads on the code", level: 4 });
		await expect(canvas.queryByText(/scm\.pull-request/)).not.toBeInTheDocument();
	},
};

/**
 * Two pieces of feedback lead with this observation: the note that went out, and the earlier one it
 * replaced. Each is named by what it is to the observation, not by where it went — the row used to be
 * titled with the delivery place, so a link to a piece of feedback read "On the work".
 */
export const LinkedFeedback: Story = {
	play: async ({ canvas }) => {
		const links = await canvas.findAllByRole("link", { name: "Feedback about this observation" });
		await expect(links).toHaveLength(2);
		await expect(links[0]).toHaveAttribute(
			"href",
			expect.stringContaining("/admin/practices/reviews/delivery/"),
		);
		canvas.getByText("Replaced by newer");
	},
};

/**
 * An observation that reinforced somebody else's point rather than leading one of its own — the
 * supporting half of a two-observation note, which no story had ever shown.
 */
export const SupportsAnotherObservation: Story = {
	args: { observationId: "77777777-7777-7777-7777-777777777777" },
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		await canvas.findByRole("link", { name: "Feedback this observation supports" });
	},
};

/** A real shortfall whose feedback was withheld, with the reason on the row that was stopped. */
export const FeedbackWasWithheld: Story = {
	args: { observationId: "bbbbbbbb-2222-2222-2222-222222222222" },
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		await canvas.findByRole("link", { name: "Feedback about this observation" });
		canvas.getByText("Found while reviewing past work, which is measured but never sent.");
	},
};

/** An observation nothing was said about states that, rather than showing an empty panel. */
export const NoFeedbackComposed: Story = {
	args: { observationId: "cccccccc-2222-2222-2222-222222222222" },
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		await canvas.findByText("Nothing was said to anybody about this");
	},
};

/** Judged against a version of the practice that has since been edited. */
export const JudgedAgainstOlderRules: Story = {
	args: { observationId: "bbbbbbbb-2222-2222-2222-222222222222" },
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		await canvas.findByText("This was judged against an older version of the practice");
	},
};

/**
 * The case the product owner found hardest to read: the review cannot tell whether the rules it used
 * are still the current ones, because the work it read is a chat thread with no revision to compare.
 */
export const CannotTellWhichRulesApplied: Story = {
	args: { observationId: "eeeeeeee-3333-3333-3333-333333333333" },
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		await canvas.findByRole("heading", {
			name: "The thread ends without naming what was chosen",
			level: 2,
		});
		canvas.getByText("The conversation", { selector: "h4" });
	},
};

export const LoadFailed: Story = {
	parameters: {
		chromatic: { viewports: [1440] },
		msw: {
			handlers: [
				http.get(
					"*/workspaces/:workspaceSlug/practices/reviews/observations/:observationId",
					() => new HttpResponse(null, { status: 500 }),
				),
				...reviewHandlers(),
			],
		},
	},
	play: async ({ canvasElement }) => {
		await within(canvasElement).findByText("Couldn't load this observation");
	},
};

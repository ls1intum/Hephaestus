import type { Meta, StoryObj } from "@storybook/react-vite";
import { HttpResponse, http } from "msw";
import { expect, within } from "storybook/test";
import type { ReviewObservationDetail } from "@/api/types.gen";
import { expectNoPageOverflow } from "@/test/reflow";
import { ObservationDetailPage } from "./ObservationDetailPage";
import { reviewObservationDetail, workspacePractices } from "./story-mock-data";

const detailHandlers = (detail: ReviewObservationDetail = reviewObservationDetail) => [
	http.get("*/workspaces/:workspaceSlug/practices/reviews/observations/:observationId", () =>
		HttpResponse.json(detail),
	),
	http.get("*/workspaces/:workspaceSlug/practices", () => HttpResponse.json(workspacePractices)),
];

const meta = {
	title: "Workspace admin/Practice reviews/Observation details",
	component: ObservationDetailPage,
	parameters: {
		layout: "padded",
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 768, 1440] },
		msw: { handlers: detailHandlers() },
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
		canvas.getByRole("heading", { name: reviewObservationDetail.title, level: 2 });
		canvas.getByRole("link", { name: "in a review" });
		// The reasoning is introduced by what it is, not by the product's name.
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
 * Both diff citations get a file coordinate because the server verifies them against the annotated
 * diff. The comment and the referenced document do not: their line numbers are offsets into the
 * serialised context file the quote came from, and nothing checks that they point at it.
 */
export const EvidenceAcrossSources: Story = {
	play: async ({ canvas }) => {
		canvas.getByText("4 passages from 3 sources.");
		canvas.getByRole("heading", { name: "The code changes", level: 4 });
		canvas.getByRole("heading", { name: "Comments on the pull request", level: 4 });
		canvas.getByRole("heading", { name: "Referenced documents", level: 4 });
		canvas.getByText("Comment by @grace");
		await expect(canvas.queryByText(/scm\.pull-request/)).not.toBeInTheDocument();
	},
};

/**
 * The feedback this observation produced, named by what it is to the observation.
 *
 * The row used to be titled with the delivery place, so a link to a piece of feedback read "On the
 * work" — a title saying nothing about the thing it opens, next to a badge already saying it.
 */
export const LinkedFeedback: Story = {
	play: async ({ canvas }) => {
		const link = canvas.getByRole("link", { name: "Feedback about this observation" });
		await expect(link).toHaveAttribute(
			"href",
			expect.stringContaining("/admin/practices/reviews/delivery/"),
		);
		canvas.getByText("The work was already merged, so a note on it would arrive too late.");
	},
};

/** An observation nothing was said about states that, rather than showing an empty panel. */
export const NoFeedbackComposed: Story = {
	parameters: {
		chromatic: { viewports: [1440] },
		msw: { handlers: detailHandlers({ ...reviewObservationDetail, feedback: [] }) },
	},
	play: async ({ canvas }) => {
		await canvas.findByText("Nothing was said to anybody about this");
	},
};

/** Judged against a version of the practice that has since been edited. */
export const JudgedAgainstOlderRules: Story = {
	parameters: {
		chromatic: { viewports: [1440] },
		msw: {
			handlers: detailHandlers({ ...reviewObservationDetail, claimCurrentness: "STALE" }),
		},
	},
	play: async ({ canvas }) => {
		await canvas.findByText("This was judged against an older version of the practice");
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
			],
		},
	},
	play: async ({ canvasElement }) => {
		await within(canvasElement).findByText("Couldn't load this observation");
	},
};

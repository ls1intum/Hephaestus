import type { Meta, StoryObj } from "@storybook/react-vite";
import { HttpResponse, http } from "msw";
import { expect, within } from "storybook/test";
import { expectNoPageOverflow } from "@/test/reflow";
import { ObservationDetailPage } from "./ObservationDetailPage";
import { reviewObservationDetail } from "./story-mock-data";
import { reviewHandlers } from "./story-mock-server";

// Every story opens a record that exists in the fixture, by id. A hand-patched copy of one detail
// — `{...detail, claimCurrentness: "STALE"}` — can describe a record no review could have produced.
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

export const Default: Story = {
	play: async ({ canvas, canvasElement }) => {
		await canvas.findByRole("heading", {
			name: "A cache miss and a permission failure come back as the same 404",
			level: 2,
		});
		canvas.getByRole("link", { name: "in a review" });
		canvas.getByRole("heading", { name: "Why this was raised", level: 3 });
		await expect(canvas.queryByText(/Hephaestus review/)).not.toBeInTheDocument();
		await expect(canvas.queryByText("AI-generated observation")).not.toBeInTheDocument();
		await expect(canvas.queryByText("Technical details")).not.toBeInTheDocument();
		await expect(canvasElement.querySelector("code")?.textContent).not.toContain("citations");
		await expectNoPageOverflow();
	},
};

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
 * A row is named by what the feedback is to the observation, and wears two tags because they answer
 * two questions: *where* it was placed, and *what became of it*.
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
		await expect(await canvas.findAllByText("On the work")).toHaveLength(2);
	},
};

export const SupportsAnotherObservation: Story = {
	args: { observationId: "77777777-7777-7777-7777-777777777777" },
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		await canvas.findByRole("link", { name: "Feedback this observation supports" });
	},
};

/**
 * Judged against a version of the practice that has since been edited, which the page has to say so
 * a reader does not measure today's rules against yesterday's answer.
 */
export const FeedbackWasWithheld: Story = {
	args: { observationId: "bbbbbbbb-2222-2222-2222-222222222222" },
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		await canvas.findByRole("link", { name: "Feedback about this observation" });
		canvas.getByText("Found while reviewing past work, which is measured but never sent.");
		canvas.getByText("This was judged against an older version of the practice");
	},
};

export const NoFeedbackComposed: Story = {
	args: { observationId: "cccccccc-2222-2222-2222-222222222222" },
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		await canvas.findByText("Nothing was said to anybody about this");
	},
};

/**
 * The review cannot tell whether the rules it used are still the current ones, because the work it
 * read is a chat thread with no revision to compare.
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

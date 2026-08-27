import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, screen } from "storybook/test";
import { expectNoPageOverflow } from "@/test/reflow";
import { FeedbackDetailPage } from "./FeedbackDetailPage";
import {
	feedbackDetail,
	longFeedbackDetail,
	reviewFeedbackDetail,
	workspacePractices,
} from "./story-mock-data";

/**
 * The route fetches the record and the workspace's practice list; this screen only draws what it is
 * handed, so every story here is a record and nothing else. Each record comes out of the fixture by
 * id rather than being patched by hand — a hand-patched copy can describe a feedback no composer
 * could have produced.
 */
const meta = {
	title: "Workspace admin/Practice reviews/Feedback details",
	component: FeedbackDetailPage,
	parameters: {
		layout: "padded",
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 768, 1440] },
	},
	tags: ["autodocs"],
	args: {
		workspaceSlug: "demo",
		search: {
			agentJobId: reviewFeedbackDetail.agentJobId,
			deliveryState: undefined,
			withheldFamily: undefined,
			channel: undefined,
		},
		feedback: reviewFeedbackDetail,
		isLoading: false,
		error: undefined,
		practices: workspacePractices,
	},
} satisfies Meta<typeof FeedbackDetailPage>;

export default meta;
type Story = StoryObj<typeof meta>;

export const NotDelivered: Story = {
	play: async ({ canvas }) => {
		// Twice, and deliberately: once on the card around the text — so a body that never reached
		// anybody cannot be quoted as though it had — and once as the trace's terminal step.
		await expect(await canvas.findAllByText("Withheld")).toHaveLength(2);
		canvas.getByRole("link", { name: "See everything reviewed on this work" });
		canvas.getByRole("link", { name: "in a review" });
		await expect(canvas.queryByText("Technical details")).not.toBeInTheDocument();
		canvas.getByText("Policy kept it quiet");
		canvas.getByText("Found while reviewing past work, which is measured but never sent.");
		await expectNoPageOverflow();
	},
};

export const Delivered: Story = {
	args: { feedback: feedbackDetail("99999999-6666-6666-6666-666666666666") },
	play: async ({ canvas }) => {
		// Exactly one "Delivered": the trace's terminal step. The composed text carries no badge,
		// because reaching the developer is the ordinary case.
		await expect(await canvas.findAllByText("Delivered")).toHaveLength(1);
		canvas.getByText(/As an inline note on the work/);
		canvas.getByText("server/application/src/main/resources/application.yml:118–120");
	},
};

/**
 * A note of the length the composer really produces. Nothing on this page truncates it: the cut an
 * operator sees in the delivery list is a list preview and stops there.
 */
export const LongFeedback: Story = {
	args: { feedback: longFeedbackDetail },
	parameters: { chromatic: { viewports: [320, 1440] } },
	play: async ({ canvas }) => {
		await canvas.findByText(/2 issues to tighten in this change/);
		// The end of the note as well as its opening, so nothing between them was dropped — including
		// the quoted code, which is the part a preview cannot carry.
		canvas.getByText(/without HTTP\./);
		canvas.getByText(/repository\.findVisible/);
		canvas.getByRole("link", {
			name: "A cache miss and a permission failure come back as the same 404",
		});
		canvas.getByRole("link", {
			name: "Three of the new tests are named after the method they call",
		});
		canvas.getByText("What this feedback is about");
		canvas.getByText("Supporting this feedback");
		await expectNoPageOverflow();
	},
};

export const PreparedForConversation: Story = {
	args: { feedback: feedbackDetail("11111111-4444-4444-4444-444444444444") },
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		// The lane, not the exact wording: the `PREPARED` label lives in `delivery-outcome-defs`.
		await canvas.findAllByText(/for conversation/);
		canvas.getByText("How should we roll back the pricing migration?");
	},
};

export const RenderedAndSource: Story = {
	args: { feedback: longFeedbackDetail },
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas, userEvent }) => {
		await canvas.findByRole("link", { name: "See the feedback this replaced" });
		await canvas.findByText(/2 issues to tighten in this change/);

		// Read each view inside its own panel: the body has a fenced code block, so both views hold a
		// `pre`, and the panel being left behind outlives the click by a frame.
		await userEvent.click(canvas.getByRole("tab", { name: "Source" }));
		await expect(canvas.getByRole("tabpanel", { name: "Source" }).textContent).toContain("```java");
		await userEvent.click(canvas.getByRole("tab", { name: "Rendered" }));
		await expect(canvas.getByRole("tabpanel", { name: "Rendered" }).textContent).toContain(
			"2 issues to tighten in this change",
		);
	},
};

/**
 * A source observation names the practice it was judged against, and the name says what the practice
 * is without leaving the page. The card is the half that goes quiet on its own: a page that stops
 * being handed the practice list still renders a perfectly good link.
 */
export const PracticeSaysWhatItIs: Story = {
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ canvas, userEvent }) => {
		const productLanguage = workspacePractices.find((p) => p.slug === "product-language");
		if (!productLanguage) throw new Error("The practice fixtures no longer cover product-language");
		await userEvent.hover(await canvas.findByRole("link", { name: /Product language/ }));
		// The card is a portal, so it is looked for on the whole screen rather than in the canvas.
		await screen.findByText(productLanguage.whyItMatters ?? "");
	},
};

export const Loading: Story = {
	args: { feedback: undefined, isLoading: true },
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		await canvas.findByRole("link", { name: "Delivery" });
		await expect(canvas.queryByText("Couldn't load this feedback")).not.toBeInTheDocument();
	},
};

/**
 * The error arrives as a prop, so nothing here depends on a request failing at the right moment. A
 * status-less error is the one that reads "check your connection" — see `QueryErrorAlert`.
 */
export const LoadFailed: Story = {
	args: { feedback: undefined, error: { status: 500, detail: "Something went wrong." } },
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		await canvas.findByText("Couldn't load this feedback");
	},
};

/**
 * No record and nothing that failed — a fetch that never came back, which is what being offline
 * looks like here. It is deliberately not the error alert: with no status to classify, the alert
 * would name a lost connection as the cause on no evidence, and a reader who *has* lost the record
 * (it was deleted) never reaches this branch, because a 404 is an error.
 */
export const NeverArrived: Story = {
	args: { feedback: undefined, error: undefined },
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("This feedback hasn't loaded")).toBeVisible();
		await expect(canvas.queryByText("Couldn't load this feedback")).toBeNull();
		// The way back out is still on the page, so this is never a dead end.
		await expect(canvas.getByRole("link", { name: "Delivery" })).toBeVisible();
	},
};

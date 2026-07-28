import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";
import type { ReviewFinding } from "@/api/types.gen";
import { expectNoPageOverflow } from "@/test/reflow";
import { FindingResults } from "./FindingResults";
import { reviewFindings } from "./story-mock-data";

const findingWithoutFeedback = {
	...reviewFindings[1],
	id: "77777777-7777-7777-7777-777777777777",
	feedbackDisposition: { delivered: 0, failed: 0, prepared: 0, superseded: 0, suppressed: 0 },
	title: "The finding was not selected for feedback",
} satisfies ReviewFinding;

const longContentFinding = {
	...reviewFindings[0],
	id: "88888888-8888-8888-8888-888888888888",
	title:
		"The review keeps every important boundary visible even when the finding needs enough words to wrap across a narrow screen",
	subject: {
		id: 10,
		login: "alexandria-occasional-contributor",
		name: "Alexandria Catherine Montgomery-Worthington",
	},
	artifact: {
		...reviewFindings[0].artifact,
		repositoryName: "hephaestustest/platform-engineering-and-developer-experience",
	},
} satisfies ReviewFinding;

const meta = {
	title: "Admin/Practice reviews/Building blocks/Finding results",
	component: FindingResults,
	parameters: {
		a11y: { test: "error" },
		layout: "padded",
		chromatic: { viewports: [320, 768, 1440] },
	},
	tags: ["autodocs"],
	args: {
		workspaceSlug: "demo",
		state: { status: "ready", findings: [...reviewFindings, findingWithoutFeedback] },
	},
} satisfies Meta<typeof FindingResults>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};
export const Mobile: Story = {
	parameters: {
		chromatic: { disableSnapshot: true },
		viewport: { defaultViewport: "reflow" },
	},
	play: async ({ canvasElement }) => {
		const firstFinding = within(within(canvasElement).getAllByRole("listitem")[0]);
		await expect(firstFinding.getByText(reviewFindings[0].title)).toBeVisible();
		await expect(firstFinding.getByText("Code quality · Thin controllers")).toBeVisible();
		await expect(firstFinding.getByText("Strength")).toBeVisible();
		await expect(
			firstFinding.getByText("Feedback: 1 delivered · 1 awaiting conversation"),
		).toBeVisible();
		await expect(firstFinding.getByText("Ada Lovelace")).toBeVisible();
		await expect(firstFinding.getByText(/PR #1420/)).toBeVisible();
		const noFeedbackFinding = within(
			within(canvasElement).getByRole("link", {
				name: /The finding was not selected for feedback/i,
			}),
		);
		await expect(noFeedbackFinding.getByText("No feedback composed")).toBeVisible();
		await expectNoPageOverflow();
	},
};
export const LongContent: Story = {
	args: {
		state: { status: "ready", findings: [longContentFinding] },
	},
	parameters: {
		chromatic: { viewports: [320] },
		viewport: { defaultViewport: "reflow" },
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		const finding = within(canvas.getByRole("listitem"));
		await expect(finding.getByText(longContentFinding.title)).toBeVisible();
		await expect(finding.getByText(longContentFinding.subject.name)).toBeVisible();
		await expect(finding.getByText(/PR #1420/)).toBeVisible();
		await expectNoPageOverflow();
	},
};
export const Loading: Story = {
	args: { state: { status: "loading" } },
	parameters: { chromatic: { viewports: [1440] } },
};
export const Empty: Story = {
	args: { state: { status: "empty", filtered: false } },
	parameters: { chromatic: { viewports: [1440] } },
};
export const FilteredToNothing: Story = {
	args: { state: { status: "empty", filtered: true } },
	parameters: { chromatic: { viewports: [1440] } },
};

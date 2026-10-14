import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect } from "storybook/test";

import { expectNoOverflowingElement } from "@/stories/reflow";

import { definitionOf, mockIssueItemType, mockPullRequestItemType, mockScan } from "./fixtures";
import { PracticeDefinitionSummary } from "./PracticeDefinitionSummary";

const meta = {
	component: PracticeDefinitionSummary,
	args: {
		kind: "scm.pull_request",
		definition: definitionOf(),
		itemType: mockPullRequestItemType,
	},
	parameters: { layout: "padded" },
	tags: ["autodocs"],
} satisfies Meta<typeof PracticeDefinitionSummary>;

export default meta;
type Story = StoryObj<typeof meta>;

export const ReviewedByHephaestus: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Opened")).toBeVisible();
		await expect(canvas.getByText("Draft status: Ready for review")).toBeVisible();
		await expect(canvas.getByText("Author")).toBeVisible();
		await expect(canvas.getByText("Built-in")).toBeVisible();
		await expect(canvas.getByText("Not included")).toBeVisible();
	},
};

export const CustomScriptAndScan: Story = {
	args: {
		definition: definitionOf({
			script: "export default definePracticeScript({});",
			scan: mockScan,
			about: "MERGER",
			reads: {
				contract: "2.0.0",
				sources: [{ source: "scm.review-threads", need: "COMPLETE" }],
			},
		}),
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Custom script")).toBeVisible();
		await expect(canvas.getByText("Whoever merged it")).toBeVisible();
		await expect(canvas.getByText(/^Included: files matching/u)).toBeVisible();
		await expect(canvas.getByText("· captured whole")).toBeVisible();
	},
};

export const AppliesWhen: Story = {
	args: {
		definition: definitionOf({
			appliesWhen: {
				anyOf: [{ changedPath: ["**/*.swift"] }],
				otherwise: "the change touches no Swift source file",
			},
		}),
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByText("A changed path matches **/*.swift")).toBeVisible();
		await expect(
			canvas.getByText("Otherwise: the change touches no Swift source file"),
		).toBeVisible();
	},
};

export const ReviewedByPeople: Story = {
	args: {
		kind: "scm.issue",
		itemType: mockIssueItemType,
		definition: definitionOf({
			reviewOn: { events: ["scm.issue.closed"], state: {} },
			reads: { contract: "2.0.0", sources: [] },
			reviewedBy: {
				by: "PEOPLE",
				humanReason: {
					code: "AT_CLOSE_STATE_NOT_CAPTURED",
					description: "Hephaestus reads the issue after it closed, not as it stood at the close.",
				},
				limits: [],
			},
		}),
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByText("People")).toBeVisible();
		await expect(
			canvas.getByText("Hephaestus reads the issue after it closed, not as it stood at the close."),
		).toBeVisible();
		await expect(canvas.queryByText("Must have")).toBeNull();
	},
};

export const ReviewedByNobody: Story = {
	args: {
		definition: definitionOf({
			reads: { contract: "2.0.0", sources: [] },
			reviewedBy: { by: "NOBODY", humanReason: null, limits: [] },
		}),
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Nobody")).toBeVisible();
		await expect(
			canvas.getByText("This practice offers guidance without automated review."),
		).toBeVisible();
	},
};

export const AboutTheWorkOnRequestOnly: Story = {
	args: { definition: definitionOf({ about: null, reviewOn: { events: [], state: {} } }) },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("The work")).toBeVisible();
		await expect(canvas.getByText("Only when someone requests a review")).toBeVisible();
	},
};

export const WithoutOptions: Story = {
	args: { itemType: undefined },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Pull or merge request")).toBeVisible();
	},
};

export const NarrowViewport: Story = {
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
	play: async ({ canvasElement }) => {
		await expectNoOverflowingElement(canvasElement);
	},
};

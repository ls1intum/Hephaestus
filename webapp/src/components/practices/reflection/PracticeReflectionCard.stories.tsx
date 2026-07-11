import type { Meta, StoryObj } from "@storybook/react";
import { expect, within } from "storybook/test";
import type { PracticeReportCard } from "@/api/types.gen";
import { PracticeReflectionCard } from "./PracticeReflectionCard";

const basePractice: PracticeReportCard = {
	name: "Write a clear pull request description",
	areaName: "Pull requests",
	slug: "clear-pr-description",
	standing: "MIXED",
	trend: "STEADY",
	whyItMatters:
		"A clear description helps reviewers understand the change quickly and gives future readers the context behind it.",
	strengths: [
		{
			artifactId: 42,
			artifactType: "PULL_REQUEST",
			observationId: "strength-1",
			title: "Explained the user-facing impact up front",
			guidance: "You opened with what changes for the user — reviewers get oriented fast.",
			locator: "PR #42",
			severity: "INFO",
		},
	],
	toWorkOn: [
		{
			artifactId: 42,
			artifactType: "PULL_REQUEST",
			observationId: "work-1",
			title: "Link the issue this pull request closes",
			guidance: "Add a closing keyword (e.g. “Closes #17”) so the issue and PR stay connected.",
			locator: "PR #42",
			severity: "MINOR",
		},
		{
			artifactId: 51,
			artifactType: "ISSUE",
			observationId: "work-2",
			title: "State the acceptance criteria",
			guidance: "List what “done” looks like so reviewers can check the change against it.",
			severity: "MAJOR",
		},
	],
};

/** One practice's readable feedback for a developer: a criterion-referenced standing chip, strengths, and what to work on. */
const meta = {
	component: PracticeReflectionCard,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: { practice: basePractice },
} satisfies Meta<typeof PracticeReflectionCard>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	play: async ({ canvasElement }) => {
		// The card surfaces the practice name, area, and why-it-matters, plus both feedback lists
		// (strengths and to-work-on).
		const canvas = within(canvasElement);
		await expect(
			canvas.getByRole("heading", { name: "Write a clear pull request description" }),
		).toBeVisible();
		await expect(canvas.getByText("Pull requests")).toBeVisible();
		await expect(
			canvas.getByText(/A clear description helps reviewers understand the change quickly/),
		).toBeVisible();
		await expect(canvas.getByText("Explained the user-facing impact up front")).toBeVisible();
		await expect(canvas.getByText("Link the issue this pull request closes")).toBeVisible();
	},
};

export const Strength: Story = {
	args: {
		practice: {
			...basePractice,
			standing: "STRENGTH",
			toWorkOn: [],
		},
	},
	play: async ({ canvasElement }) => {
		// The standing enum maps to a human chip label.
		const canvas = within(canvasElement);
		await expect(canvas.getByText("Strength")).toBeVisible();
	},
};

export const Developing: Story = {
	args: {
		practice: {
			...basePractice,
			standing: "DEVELOPING",
			strengths: [],
		},
	},
};

export const NoGuidance: Story = {
	args: {
		practice: {
			...basePractice,
			whyItMatters: undefined,
			strengths: [],
			toWorkOn: [
				{
					artifactId: 42,
					artifactType: "PULL_REQUEST",
					observationId: "work-only",
					title: "Add a summary line",
				},
			],
		},
	},
};

export const TrendImproving: Story = {
	args: { practice: { ...basePractice, trend: "IMPROVING" } },
	play: async ({ canvasElement }) => {
		// A criterion-referenced trajectory note, not a number or a peer comparison.
		const canvas = within(canvasElement);
		await expect(canvas.getByText("Improving since last cycle")).toBeVisible();
	},
};

export const TrendWorsening: Story = {
	args: { practice: { ...basePractice, trend: "WORSENING" } },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("Slipped since last cycle")).toBeVisible();
	},
};

export const TrendNew: Story = {
	args: { practice: { ...basePractice, trend: "NEW" } },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("New this cycle")).toBeVisible();
	},
};

export const TrendSteady: Story = {
	args: { practice: { ...basePractice, trend: "STEADY" } },
	play: async ({ canvasElement }) => {
		// STEADY is deliberately silent — no badge, no noise.
		const canvas = within(canvasElement);
		await expect(canvas.queryByText("Improving since last cycle")).toBeNull();
		await expect(canvas.queryByText("Slipped since last cycle")).toBeNull();
		await expect(canvas.queryByText("New this cycle")).toBeNull();
	},
};

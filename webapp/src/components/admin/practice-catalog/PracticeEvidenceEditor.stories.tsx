import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn } from "storybook/test";
import { mockPracticeEvidenceAuthoring } from "@/mocks/fixtures/practice";
import { PracticeEvidenceEditor } from "./PracticeEvidenceEditor";

const pullRequestOptions = mockPracticeEvidenceAuthoring.artifacts[0];

const meta = {
	title: "Workspace admin/Practices/Evidence editor",
	component: PracticeEvidenceEditor,
	args: {
		options: pullRequestOptions,
		value: pullRequestOptions.baseline,
		onChange: fn(),
	},
	parameters: { layout: "padded" },
	tags: ["autodocs"],
} satisfies Meta<typeof PracticeEvidenceEditor>;

export default meta;
type Story = StoryObj<typeof meta>;

export const RecommendedRule: Story = {};

export const Customizing: Story = {
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: "Customize evidence" }));
		await expect(canvas.getByText("Pull request details")).toBeVisible();
		await expect(canvas.getByText("Code changes")).toBeVisible();
		await expect(canvas.getByLabelText("How can Hephaestus assess this practice?")).toBeVisible();
	},
};

export const InvalidRule: Story = {
	args: {
		value: { ...pullRequestOptions.baseline, required: [] },
		error: "Choose at least one required evidence source.",
	},
};

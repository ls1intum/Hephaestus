import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn } from "storybook/test";
import { mockPracticeEvidenceOptions } from "@/mocks/fixtures/practice";
import { PracticeEvidenceEditor } from "./PracticeEvidenceEditor";

const pullRequestOptions = mockPracticeEvidenceOptions.workTypes[0];

const meta = {
	title: "Workspace admin/Practices/Evidence editor",
	component: PracticeEvidenceEditor,
	args: {
		options: pullRequestOptions,
		value: pullRequestOptions.recommendedRequirements,
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
		await userEvent.click(canvas.getByRole("button", { name: "Edit evidence requirements" }));
		await expect(canvas.getByText("Pull request details")).toBeVisible();
		await expect(canvas.getByText("Code changes")).toBeVisible();
		await expect(
			canvas.getByLabelText("How should Hephaestus assess reviewed work?"),
		).toBeVisible();
	},
};

export const InvalidRule: Story = {
	args: {
		value: { ...pullRequestOptions.recommendedRequirements, requiredEvidence: [] },
		error: "Choose at least one required evidence source.",
	},
};

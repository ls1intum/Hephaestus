import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, within } from "storybook/test";
import { mockPracticeDefinitionOptions } from "@/mocks/fixtures/practice";
import { PracticeEvidenceEditor } from "./PracticeEvidenceEditor";

const pullRequestOptions = mockPracticeDefinitionOptions.workTypes[0];

const meta = {
	title: "Workspace admin/Practices/AI mentoring",
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

export const RecommendedSetup: Story = {};

export const Customizing: Story = {
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: "Customize evidence" }));
		await expect(canvas.getByText("Pull request details")).toBeVisible();
		await expect(canvas.getByText("Code changes")).toBeVisible();
		await expect(canvas.getByText("Connected evidence")).toBeVisible();
	},
};

/**
 * Every evidence choice is a visible control rather than a menu: the three-way role is a radio
 * group, and each minimum-quality requirement is a checkbox rendered only where the source can
 * establish it. The pull-request record cannot demonstrate currentness, so it offers no freshness
 * checkbox, and an author is never shown an option that cannot be chosen.
 */
export const EveryChoiceIsVisible: Story = {
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: "Customize evidence" }));

		const diff = canvas.getByRole("radiogroup", {
			name: "Use in this practice for Code changes",
		});
		await expect(within(diff).getByRole("radio", { name: "Required" })).toBeChecked();
		await expect(within(diff).getByRole("radio", { name: "Optional context" })).toBeVisible();
		await expect(within(diff).getByRole("radio", { name: "Not used" })).toBeVisible();

		await expect(
			canvas.getByRole("checkbox", { name: "Must not be empty for Code changes" }),
		).toBeChecked();
		await expect(
			canvas.getByRole("checkbox", { name: "Must be complete for Code changes" }),
		).toBeChecked();

		// The pull-request record's freshness mode is NOT_APPLICABLE, so the control is absent rather
		// than present-and-unselectable.
		await expect(
			canvas.getByRole("checkbox", { name: "Must be complete for Pull request details" }),
		).toBeChecked();
		await expect(
			canvas.queryByRole("checkbox", { name: "Must be current for Pull request details" }),
		).toBeNull();

		await expect(canvas.queryByRole("combobox", { name: /Use in this practice/ })).toBeNull();
	},
};

export const InvalidRule: Story = {
	args: {
		value: { ...pullRequestOptions.recommendedRequirements, requiredEvidence: [] },
		error: "Choose at least one required evidence source.",
	},
};

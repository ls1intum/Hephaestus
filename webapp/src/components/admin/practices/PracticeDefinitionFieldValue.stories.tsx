import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect } from "storybook/test";

import { definitionOf, mockScan } from "@/components/admin/practice-editor/fixtures";

import { PracticeDefinitionFieldValue } from "./PracticeDefinitionFieldValue";

const meta = {
	component: PracticeDefinitionFieldValue,
	tags: ["autodocs"],
	args: { definition: definitionOf(), field: "CRITERIA" },
} satisfies Meta<typeof PracticeDefinitionFieldValue>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Criteria: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.getByText(/small enough to review in one sitting/u)).toBeVisible();
	},
};

/** No script means the built-in review, said in words rather than left blank. */
export const BuiltInScript: Story = {
	args: { field: "SCRIPT" },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Built-in")).toBeVisible();
	},
};

export const ReviewedByPeople: Story = {
	args: {
		field: "REVIEWED_BY",
		definition: definitionOf({
			reviewedBy: {
				by: "PEOPLE",
				humanReason: { code: "DESIGN_JUDGEMENT", description: "Design choices need a person." },
				limits: [],
			},
		}),
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByText(/People\s+Design choices need a person\./u)).toBeVisible();
	},
};

export const Scan: Story = {
	args: { field: "SCAN", definition: definitionOf({ scan: mockScan }) },
	play: async ({ canvas }) => {
		await expect(canvas.getByText(/Include: \*\*\/\*\.ts, \*\*\/\*\.tsx/u)).toBeVisible();
	},
};

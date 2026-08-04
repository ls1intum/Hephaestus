import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";
import { FindingEvidence } from "./FindingEvidence";

const meta = {
	title: "Admin/Practice reviews/Finding evidence",
	component: FindingEvidence,
	args: {
		evidence: {
			citations: [
				{
					sourceKind: "scm.pull-request.diff",
					artifactPath: "inputs/context/diff.patch",
					path: "src/config.ts",
					side: "NEW",
					startLine: 12,
					endLine: 12,
					quoteRedacted: true,
				},
			],
		},
	},
} satisfies Meta<typeof FindingEvidence>;

export default meta;
type Story = StoryObj<typeof meta>;

export const RedactedQuote: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("Quote redacted.")).toBeVisible();
		await expect(canvasElement.querySelector("pre")).toBeNull();
	},
};

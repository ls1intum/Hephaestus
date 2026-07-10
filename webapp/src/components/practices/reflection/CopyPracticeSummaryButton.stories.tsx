import type { Meta, StoryObj } from "@storybook/react";
import { expect, userEvent, within } from "storybook/test";
import type { PracticeReportCard } from "@/api/types.gen";
import { CopyPracticeSummaryButton } from "./CopyPracticeSummaryButton";

/** Copies the developer's own practice reflection as a markdown + HTML digest. Self-referential, never comparative. */
const meta = {
	component: CopyPracticeSummaryButton,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
} satisfies Meta<typeof CopyPracticeSummaryButton>;

export default meta;
type Story = StoryObj<typeof meta>;

const practices: PracticeReportCard[] = [
	{
		name: "Clear PR description",
		slug: "clear-pr",
		standing: "STRENGTH",
		strengths: [
			{
				observationId: "o1",
				title: "Explained the why, not just the what",
				artifactType: "PULL_REQUEST",
				artifactId: 42,
				locator: "PR #42",
			},
		],
		toWorkOn: [],
	},
	{
		name: "Small PRs",
		slug: "small-prs",
		standing: "DEVELOPING",
		strengths: [],
		toWorkOn: [
			{
				observationId: "o2",
				title: "Split large changes into reviewable steps",
				artifactType: "PULL_REQUEST",
				artifactId: 43,
				guidance: "Aim for focused PRs under ~400 lines.",
			},
		],
	},
];

/** Populated — ready to copy. */
export const Default: Story = { args: { practices } };

/** Nothing to copy yet — the button is disabled. */
export const Empty: Story = { args: { practices: [] } };

/** After clicking, the button confirms the copy. */
export const Copied: Story = {
	args: { practices },
	play: async ({ canvasElement }) => {
		// The button only confirms once the clipboard write resolves; the test browser may not
		// grant clipboard-write permission, so stub the API to make the success path deterministic.
		Object.defineProperty(navigator, "clipboard", {
			value: { write: async () => {}, writeText: async () => {} },
			configurable: true,
		});
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("button"));
		await expect(canvas.getByRole("button")).toHaveTextContent(/copied/i);
	},
};

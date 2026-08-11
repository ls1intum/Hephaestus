import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect } from "storybook/test";
import { FindingEvidence } from "./FindingEvidence";
import { reviewFindingDetail } from "./story-mock-data";

// Evidence is optional on the wire, and the fixture this file is about carries it. Asserted rather
// than asserted-away with `!`, so a fixture that loses it names itself instead of failing as a null
// dereference three stories down.
const { evidence } = reviewFindingDetail;
if (!evidence) {
	throw new Error("The shared review-finding fixture must carry evidence for these stories.");
}
const [citation] = evidence.citations;

const meta = {
	title: "Workspace admin/Practice reviews/Finding evidence",
	component: FindingEvidence,
	args: { evidence },
	parameters: { layout: "padded" },
	tags: ["autodocs"],
} satisfies Meta<typeof FindingEvidence>;

export default meta;
type Story = StoryObj<typeof meta>;

/** What a citation is for: the passage, and where in the source contract it was drawn from. */
export const Quoted: Story = {
	play: async ({ canvasElement, canvas }) => {
		await expect(canvasElement.querySelector("pre")).toHaveTextContent("const routeName =");
		await expect(canvas.getByText(/scm\.pull-request\.diff · inputs\/context\/diff\.patch/));
	},
};

/**
 * A quote the review was not allowed to keep.
 *
 * The citation still names where it came from, because the provenance is the part an admin can act
 * on — and the passage is replaced by a sentence rather than by an empty block, which would read as
 * a finding that cited nothing.
 */
export const RedactedQuote: Story = {
	args: {
		evidence: { citations: [{ ...citation, quote: undefined, quoteRedacted: true }] },
	},
	play: async ({ canvasElement, canvas }) => {
		await expect(canvas.getByText("Quote redacted.")).toBeVisible();
		await expect(canvasElement.querySelector("pre")).toBeNull();
	},
};

/** No evidence at all is a statement, not a blank panel. */
export const NoEvidence: Story = {
	args: { evidence: null },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("No evidence was recorded.")).toBeVisible();
	},
};

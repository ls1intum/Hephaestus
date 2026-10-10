import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, userEvent, within } from "storybook/test";

import type { PracticeDefinition, PracticeReleaseProposal } from "@/api/types.gen";
import { mockPullRequestReviewFields, mockPullRequestPolicy } from "@/mocks/fixtures/practice";
import { bundledGuidance, unthemedVisual } from "@/stories/practice-guidance-story-mock-data";
import { expectTablesScrollInPlace } from "@/stories/reflow";

import { PracticeReleaseReview } from "./PracticeReleaseReview";

const base: PracticeDefinition = {
	name: "Explain the change",
	...mockPullRequestReviewFields,
	criteria: "Explain the old behavior",
	automatedReviewPolicy: mockPullRequestPolicy,
	whyItMatters: "Reviewers need context.",
	deliveryBehavior: { summaryOnly: false },
};

const proposal: PracticeReleaseProposal = {
	slug: "explain-the-change",
	base,
	current: {
		...base,
		criteria: "Our own review criteria",
		whyItMatters: "Our team needs context.",
	},
	offered: {
		...base,
		criteria: "Explain the change and why it matters",
		deliveryBehavior: { summaryOnly: true },
	},
	baseSource: "EXACT_ADOPTION",
	offeredDigest: "offered",
	etag: "proposal",
	currentRevision: 2,
	fields: [
		{ field: "CRITERIA", offeredChanged: true, conflict: true },
		{ field: "WHY_IT_MATTERS", offeredChanged: false, conflict: false },
		{ field: "DELIVERY_BEHAVIOR", offeredChanged: true, conflict: false },
	],
};

const meta = {
	component: PracticeReleaseReview,
	args: { proposal, pending: null, onAccept: fn(), onDecline: fn() },
	parameters: { layout: "padded" },
	tags: ["autodocs"],
} satisfies Meta<typeof PracticeReleaseReview>;

export default meta;
type Story = StoryObj<typeof meta>;

export const ConflictingUpdate: Story = {
	play: async ({ args }) => {
		const accept = screen.getByRole("button", { name: "Accept selected fields" });
		await expect(accept).toBeDisabled();
		await expect(screen.getByText("Local edit")).toBeVisible();
		await userEvent.click(
			within(
				screen.getByRole("radiogroup", { name: "Use a version for Review criteria" }),
			).getByRole("radio", { name: "Proposed" }),
		);
		await expect(accept).toBeEnabled();
		await userEvent.click(accept);
		await expect(args.onAccept).toHaveBeenCalledWith({
			CRITERIA: "OFFERED",
			DELIVERY_BEHAVIOR: "OFFERED",
		});
	},
};

/** Delivery choices read as sentences on both sides, never as the object the wire carries. */
export const DeliveryInWords: Story = {
	play: async () => {
		await expect(
			screen.getByText("Feedback stays in the summary, not on a changed line."),
		).toBeVisible();
		await expect(screen.getAllByText("No special delivery rules.").length).toBeGreaterThan(0);
		await expect(screen.queryByText(/summaryOnly/u)).not.toBeInTheDocument();
	},
};

export const RecoveredBase: Story = {
	args: { proposal: { ...proposal, baseSource: "CURRENT_DEFINITION" } },
	play: async ({ args }) => {
		await expect(screen.getByText(/Choose a version for each changed field/u)).toBeVisible();
		const accept = screen.getByRole("button", { name: "Accept selected fields" });
		const delivery = within(
			screen.getByRole("radiogroup", { name: "Use a version for Feedback delivery" }),
		);
		await expect(delivery.getByRole("radio", { name: "Proposed" })).not.toBeChecked();
		await expect(delivery.getByRole("radio", { name: "Current" })).not.toBeChecked();
		await userEvent.click(
			within(
				screen.getByRole("radiogroup", { name: "Use a version for Review criteria" }),
			).getByRole("radio", { name: "Proposed" }),
		);
		await expect(accept).toBeDisabled();
		await userEvent.click(delivery.getByRole("radio", { name: "Current" }));
		await expect(accept).toBeEnabled();
		await userEvent.click(accept);
		await expect(args.onAccept).toHaveBeenCalledWith({
			CRITERIA: "OFFERED",
			DELIVERY_BEHAVIOR: "CURRENT",
		});
	},
};

export const RecordedVersionBase: Story = {
	...RecoveredBase,
	args: { proposal: { ...proposal, baseSource: "REVISION_FINGERPRINT_MATCH" } },
};

const readyProposal = {
	...proposal,
	current: base,
	fields: proposal.fields
		.filter((field) => field.offeredChanged)
		.map((field) => ({ ...field, conflict: false })),
};

export const Accepting: Story = {
	args: { proposal: readyProposal, pending: "accept" },
	play: async () => {
		await expect(screen.getByRole("button", { name: "Accepting selected fields…" })).toBeDisabled();
		await expect(screen.getByRole("button", { name: "Decline this update" })).toBeDisabled();
		for (const choice of screen.getAllByRole("radio")) {
			await expect(choice).toHaveAttribute("aria-disabled", "true");
		}
	},
};

export const Declining: Story = {
	args: { proposal: readyProposal, pending: "decline" },
	play: async () => {
		await expect(screen.getByRole("button", { name: "Declining update…" })).toBeDisabled();
		await expect(screen.getByRole("button", { name: "Accept selected fields" })).toBeDisabled();
		for (const choice of screen.getAllByRole("radio")) {
			await expect(choice).toHaveAttribute("aria-disabled", "true");
		}
	},
};

export const NarrowComparison: Story = {
	args: {
		proposal: {
			...proposal,
			fields: [{ field: "CRITERIA", offeredChanged: true, conflict: false }],
		},
	},
	parameters: { chromatic: { viewports: [320, 930] } },
	render: (args) => (
		<section aria-label="Practice update panel" className="grid w-full max-w-160">
			<PracticeReleaseReview {...args} />
		</section>
	),
	play: async ({ canvas }) => {
		const panel = canvas.getByRole("region", { name: "Practice update panel" });
		await expect(panel.scrollWidth).toBeLessThanOrEqual(panel.clientWidth + 1);
		await expectTablesScrollInPlace(panel, { expectOverflow: true });
		const accept = canvas.getByRole("button", { name: "Accept selected fields" });
		await expect(accept).toBeEnabled();
		const bounds = accept.getBoundingClientRect();
		const panelBounds = panel.getBoundingClientRect();
		await expect(bounds.left).toBeGreaterThanOrEqual(panelBounds.left);
		await expect(bounds.right).toBeLessThanOrEqual(panelBounds.right);
		await expect(
			document.elementFromPoint(bounds.left + bounds.width / 2, bounds.top + bounds.height / 2),
		).toBe(accept);
	},
};

/**
 * A visual or guide reads as the developer would see it, not as markup: each version's picture with
 * its description, and each guide's text with its figures.
 */
export const GuidanceUpdate: Story = {
	args: {
		proposal: {
			...proposal,
			current: { ...base, visual: unthemedVisual },
			offered: { ...base, visual: bundledGuidance.visual, guide: bundledGuidance.guide },
			fields: [
				{ field: "VISUAL", offeredChanged: true, conflict: true },
				{ field: "GUIDE", offeredChanged: true, conflict: false },
			],
		},
	},
	play: async ({ args }) => {
		await expect(screen.getByRole("img", { name: unthemedVisual.alt })).toBeVisible();
		await expect(screen.getByRole("img", { name: bundledGuidance.visual.alt })).toBeVisible();
		// A picture's description wraps inside its column instead of running into the next one.
		const description = screen.getByText(bundledGuidance.visual.alt, { selector: "p" });
		await expect(description.scrollWidth).toBeLessThanOrEqual(description.clientWidth);
		await expect(screen.getByText(/^## How to do it/u)).toBeVisible();
		await expect(screen.getByText("figures/split-order.svg")).toBeVisible();
		await expect(screen.queryByText(/<svg/u)).not.toBeInTheDocument();

		await userEvent.click(
			within(screen.getByRole("radiogroup", { name: "Use a version for Visual" })).getByRole(
				"radio",
				{ name: "Current" },
			),
		);
		await userEvent.click(screen.getByRole("button", { name: "Accept selected fields" }));
		await expect(args.onAccept).toHaveBeenCalledWith({ VISUAL: "CURRENT", GUIDE: "OFFERED" });
	},
};

/** The script that runs before each review carries the editor's name for it. */
export const PrecomputeScriptUpdate: Story = {
	args: {
		proposal: {
			...proposal,
			current: base,
			offered: { ...base, precomputeScript: "export default { models: {} };" },
			fields: [{ field: "PRECOMPUTE_SCRIPT", offeredChanged: true, conflict: false }],
		},
	},
	play: async () => {
		await expect(screen.getByText("Precompute script")).toBeVisible();
	},
};

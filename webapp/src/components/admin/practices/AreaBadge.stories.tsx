import type { Meta, StoryObj } from "@storybook/react";
import { AreaBadge } from "./AreaBadge";
import { SEEDED_AREA_SLUGS } from "./areaVisuals";

/**
 * Identity chip for a practice area: a lucide icon plus an accessible colour pill. The icon and name
 * carry the meaning; colour is a redundant cue, so the chip reads with colour vision deficiency.
 */
const meta = {
	component: AreaBadge,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: { slug: "review-ready-work", name: "Packaging work for review" },
} satisfies Meta<typeof AreaBadge>;

export default meta;
type Story = StoryObj<typeof meta>;

const SEEDED: Record<string, string> = {
	"review-ready-work": "Packaging work for review",
	"acting-on-review-feedback": "Acting on review feedback",
	"actionable-issue-authoring": "Writing issues a maintainer can act on",
	"constructive-code-review": "Reviewing a teammate's work constructively",
	"testing-discipline": "Testing your changes",
	"code-craftsmanship": "Writing maintainable code",
	"robust-error-handling": "Handling failure well",
	"secure-by-default-changes": "Making changes secure by default",
	"decisions-and-documentation": "Recording decisions and documenting changes",
	"delivery-and-version-control-discipline": "Disciplined delivery and version control",
	"issue-traceability-and-lifecycle": "Tracking and planning the work",
};

export const Default: Story = {};

/** All 11 seeded areas, each with its own icon and colour. */
export const AllAreas: Story = {
	render: () => (
		<div className="flex flex-wrap gap-2">
			{SEEDED_AREA_SLUGS.map((slug) => (
				<AreaBadge key={slug} slug={slug} name={SEEDED[slug] ?? slug} showBlocking />
			))}
		</div>
	),
};

/** The three correctness/security areas show a redundant "Blocking" marker (not colour alone). */
export const BlockingMarker: Story = {
	args: {
		slug: "secure-by-default-changes",
		name: "Making changes secure by default",
		showBlocking: true,
	},
};

/** An admin-set icon + colour override the seeded default for the same slug. */
export const AdminOverride: Story = {
	args: {
		slug: "review-ready-work",
		name: "Packaging work for review",
		icon: "Rocket",
		color: "fuchsia",
	},
};

/** An admin-created area not in the seeded set falls back to a keyword-derived icon, else a folder. */
export const UnknownFallback: Story = {
	render: () => (
		<div className="flex flex-wrap gap-2">
			<AreaBadge slug="performance-tuning" name="Performance tuning" />
			<AreaBadge slug="security-hardening" name="Security hardening" />
			<AreaBadge slug="custom-team-area" name="Custom team area" />
		</div>
	),
};

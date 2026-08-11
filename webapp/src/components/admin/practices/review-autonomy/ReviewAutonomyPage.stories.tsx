import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, within } from "storybook/test";
import { expectNoPageOverflow } from "@/test/reflow";
import { ReviewAutonomyPage } from "./ReviewAutonomyPage";
import { buildAutonomyFixture, scaleFixture } from "./story-mock-data";

const small = buildAutonomyFixture({
	areas: [
		{
			slug: "pull-request-hygiene",
			name: "Pull request hygiene",
			practices: [
				{ name: "States the motivation" },
				{ name: "Links the issue it closes", override: "PROPOSE" },
			],
		},
		{
			slug: "testing",
			name: "Testing",
			override: "OFF",
			practices: [{ name: "Covers the new branch" }, { name: "Names what it asserts" }],
		},
		{
			slug: null,
			name: null,
			practices: [{ name: "Handles the error state" }],
		},
	],
});

const idle = {
	workspace: false,
	areaSlugs: new Set<string>(),
	practiceSlugs: new Set<string>(),
	bulk: null,
};

const meta = {
	title: "Workspace admin/Practices/Review autonomy",
	component: ReviewAutonomyPage,
	parameters: {
		layout: "padded",
		chromatic: { viewports: [320, 1440] },
		viewport: { defaultViewport: "reflow" },
	},
	args: {
		workspaceSlug: "demo",
		settings: small.settings,
		rollup: small.rollup,
		practices: small.practices,
		pending: idle,
		overridesOnly: false,
		onOverridesOnlyChange: fn(),
		onSetWorkspaceDefault: fn(),
		onClearWorkspaceDefault: fn(),
		onSetFeedbackReach: fn(),
		onClearFeedbackReach: fn(),
		onSetAreaTier: fn(),
		onClearAreaTier: fn(),
		onSetPracticeTier: fn(),
		onClearPracticeTier: fn(),
		onBulkSetTier: fn(),
	},
	decorators: [
		(Story) => (
			<div className="mx-auto w-full max-w-6xl">
				<Story />
			</div>
		),
	],
	tags: ["autodocs"],
} satisfies Meta<typeof ReviewAutonomyPage>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * A workspace that has never expressed an opinion. The default reads Deliver because that is what an
 * unset chain resolves to, and the line under it says so rather than pretending somebody chose it.
 */
export const WorkspaceDefaultUnset: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Not chosen yet, so Deliver applies.")).toBeVisible();
		await expect(
			canvas.getByRole("radiogroup", { name: "How far Hephaestus may go without you" }),
		).toBeVisible();
		await expect(canvas.queryByText(/How loud/i)).not.toBeInTheDocument();
		await expectNoPageOverflow();
	},
};

/** The one decision that replaces a hundred, made — and reversible from the same place. */
export const WorkspaceDefaultSet: Story = {
	args: (() => {
		const fixture = buildAutonomyFixture({
			workspaceDefault: "PROPOSE",
			feedbackReach: "CONVERSATION",
			areas: [
				{
					slug: "pull-request-hygiene",
					name: "Pull request hygiene",
					practices: [{ name: "States the motivation" }, { name: "Links the issue it closes" }],
				},
			],
		});
		return { settings: fixture.settings, rollup: fixture.rollup, practices: fixture.practices };
	})(),
	play: async ({ args, canvas, userEvent }) => {
		await expect(
			within(
				canvas.getByRole("radiogroup", { name: "How far Hephaestus may go without you" }),
			).getByRole("radio", { name: "Propose" }),
		).toBeChecked();
		await expect(
			within(canvas.getByRole("radiogroup", { name: "Where feedback may go" })).getByRole("radio", {
				name: /In the mentor conversation/,
			}),
		).toBeChecked();

		const resets = canvas.getAllByRole("button", { name: /^Use the default/ });
		await userEvent.click(resets[0]);
		await expect(args.onClearWorkspaceDefault).toHaveBeenCalled();
	},
};

/**
 * An area that decided for itself. Its rung is not muted, its heading counts the practices it now
 * governs, and the summary above still answers for the whole workspace.
 */
export const AreaOverride: Story = {
	play: async ({ args, canvas, userEvent }) => {
		const testing = canvas.getByRole("radiogroup", {
			name: "How far Hephaestus may go in Testing",
		});
		await expect(within(testing).getByRole("radio", { name: "Off" })).toBeChecked();
		await userEvent.click(within(testing).getByRole("radio", { name: "Deliver" }));
		await expect(args.onSetAreaTier).toHaveBeenCalledWith("testing", "DELIVER");

		// The bucket for practices in no area holds no tier of its own — it is not a row anywhere.
		const unassigned = canvas.getByText("Not in an area").closest('[data-slot="accordion-item"]');
		if (!(unassigned instanceof HTMLElement)) throw new Error("No-area group not rendered");
		await expect(within(unassigned).getByText("Follows the workspace default")).toBeVisible();
		await expect(
			within(unassigned).queryByRole("radiogroup", { name: /How far Hephaestus may go in/ }),
		).not.toBeInTheDocument();
	},
};

/** One practice set by hand, and the way back to inheriting from the same row. */
export const PracticeOverrideWithReset: Story = {
	play: async ({ args, canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: /Pull request hygiene/ }));
		const row = canvas.getByText("Links the issue it closes").closest("li");
		if (!(row instanceof HTMLElement)) throw new Error("Practice row not rendered");

		await expect(within(row).getByText("Set here")).toBeVisible();
		await expect(
			within(row).getByRole("radiogroup", {
				name: "How far Hephaestus may go on Links the issue it closes",
			}),
		).toBeVisible();

		const sibling = canvas.getByText("States the motivation").closest("li");
		if (!(sibling instanceof HTMLElement)) throw new Error("Sibling row not rendered");
		await expect(within(sibling).getByText(/^Follows/)).toBeVisible();

		await userEvent.click(
			within(row).getByRole("button", { name: /Use the default for Links the issue it closes/ }),
		);
		await expect(args.onClearPracticeTier).toHaveBeenCalledWith(
			"pull-request-hygiene-links-the-issue-it-closes",
		);
	},
};

/**
 * The highest-value control on a hundred-row page: what is left is the handful somebody changed —
 * including an area whose own tier was set even though none of its practices were.
 */
export const OverridesOnly: Story = {
	args: { overridesOnly: true },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Links the issue it closes")).toBeVisible();
		await expect(canvas.queryByText("States the motivation")).not.toBeInTheDocument();
		// Testing set its own tier, so it stays — with a line saying why its rows are not listed.
		await expect(canvas.getByRole("button", { name: /^Testing/ })).toBeVisible();
		await expect(canvas.getByText("No practices here were set by hand.")).toBeVisible();
		await expect(canvas.queryByText("Handles the error state")).not.toBeInTheDocument();
	},
};

export const OverridesOnlyEmpty: Story = {
	args: (() => {
		const fixture = buildAutonomyFixture({
			areas: [
				{
					slug: "testing",
					name: "Testing",
					practices: [{ name: "Covers the new branch" }],
				},
			],
		});
		return {
			overridesOnly: true,
			settings: fixture.settings,
			rollup: fixture.rollup,
			practices: fixture.practices,
		};
	})(),
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Nothing was set by hand")).toBeVisible();
	},
};

/** Moving an area's worth in one action, which is the task the screen exists for. */
export const BulkSet: Story = {
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ args, canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: /Pull request hygiene/ }));
		await userEvent.click(
			canvas.getByRole("button", { name: /Select all 2 practices in Pull request hygiene/ }),
		);
		await expect(canvas.getByText("2 practices selected")).toBeVisible();

		await userEvent.click(canvas.getByRole("button", { name: "Change the selected" }));
		const menu = within(await screen.findByRole("menu"));
		await userEvent.click(await menu.findByRole("menuitem", { name: "Propose" }));
		await expect(args.onBulkSetTier).toHaveBeenCalledWith(
			[
				"pull-request-hygiene-states-the-motivation",
				"pull-request-hygiene-links-the-issue-it-closes",
			],
			"PROPOSE",
		);
	},
};

export const BulkClearToInherited: Story = {
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ args, canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: /Pull request hygiene/ }));
		await userEvent.click(
			canvas.getByRole("checkbox", { name: "Select Links the issue it closes" }),
		);
		await expect(canvas.getByText("1 practice selected")).toBeVisible();
		await userEvent.click(canvas.getByRole("button", { name: "Change the selected" }));
		const menu = within(await screen.findByRole("menu"));
		await userEvent.click(await menu.findByRole("menuitem", { name: "Use the inherited setting" }));
		await expect(args.onBulkSetTier).toHaveBeenCalledWith(
			["pull-request-hygiene-links-the-issue-it-closes"],
			null,
		);
	},
};

export const BulkInFlight: Story = {
	args: { pending: { ...idle, bulk: { done: 7, total: 24 } } },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Changing 7 of 24…")).toBeVisible();
		await expect(canvas.getByRole("button", { name: "Change the selected" })).toBeDisabled();
	},
};

/**
 * A hundred practices across twenty-five areas — the size the old one-row-at-a-time screen could not
 * be used at, and the case nobody tests.
 *
 * <p>The assertions here are about scale rather than about any one row: the summary answers without
 * scrolling and comes from the rollup rather than from counting the rows on screen, and a shut area
 * renders none of its practices, so the page carries twenty-five tier controls rather than a hundred
 * and twenty-five.
 */
export const AtScale: Story = {
	args: (() => {
		const fixture = scaleFixture();
		return { settings: fixture.settings, rollup: fixture.rollup, practices: fixture.practices };
	})(),
	play: async ({ canvas }) => {
		await expect(canvas.getByText("100 practices: 6 off, 89 propose and 5 deliver.")).toBeVisible();
		await expect(canvas.getAllByRole("radiogroup")).toHaveLength(27);
		await expect(canvas.queryByRole("checkbox", { name: /^Select / })).not.toBeInTheDocument();
		await expectNoPageOverflow();
	},
};

export const AtScaleOverridesOnly: Story = {
	args: (() => {
		const fixture = scaleFixture();
		return {
			overridesOnly: true,
			settings: fixture.settings,
			rollup: fixture.rollup,
			practices: fixture.practices,
		};
	})(),
	play: async ({ canvas }) => {
		// Two areas set their own tier, three practices did (one of them because Hephaestus cannot
		// review it). Out of a hundred rows, that is the list.
		await expect(canvas.getAllByRole("checkbox", { name: /^Select / })).toHaveLength(3);
		await expect(canvas.getByText(/^Observability: keeps the change/)).toBeVisible();
		await expectNoPageOverflow();
	},
};

/** A practice Hephaestus cannot review is pinned to Off, and says why rather than failing on click. */
export const NotReviewable: Story = {
	args: (() => {
		const fixture = buildAutonomyFixture({
			areas: [
				{
					slug: "observability",
					name: "Observability",
					practices: [{ name: "Alerts a human on failure", reviewable: false }],
				},
			],
		});
		return { settings: fixture.settings, rollup: fixture.rollup, practices: fixture.practices };
	})(),
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: /Observability/ }));
		await expect(
			canvas.getByText("Hephaestus can't review this practice, so it stays off."),
		).toBeVisible();
		await expect(canvas.getByRole("checkbox", { name: /^Select / })).toHaveAttribute(
			"aria-disabled",
			"true",
		);
	},
};

import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, within } from "storybook/test";
import { withWidePage } from "@/stories/decorators";
import { Stateful } from "@/stories/stateful";
import { expectSettledVisible } from "@/test/overlay";
import { expectNoPageOverflow } from "@/test/reflow";
import { PracticeAutonomyPage } from "./PracticeAutonomyPage";
import { type AutonomyFixture, buildAutonomyFixture, scaleFixture } from "./story-mock-data";

const from = ({ settings, rollup, practices }: AutonomyFixture) => ({
	settings,
	rollup,
	practices,
});

const small = buildAutonomyFixture({
	groups: [
		{
			slug: "pull-request-hygiene",
			name: "Pull request hygiene",
			practices: [
				{
					name: "States the motivation",
					whyItMatters:
						"Your reviewer was not in your head when you wrote the code. A sentence on why you made the change spares them from reverse-engineering your intent from the diff.",
				},
				{ name: "Links the issue it closes", override: "HUMAN_APPROVAL" },
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

const chosenByTheWorkspace = buildAutonomyFixture({
	workspaceDefault: "HUMAN_APPROVAL",
	groups: [
		{
			slug: "pull-request-hygiene",
			name: "Pull request hygiene",
			practices: [{ name: "States the motivation" }, { name: "Links the issue it closes" }],
		},
	],
});

const oneDescribedAndOneBare = buildAutonomyFixture({
	groups: [
		{
			slug: "documentation",
			name: "Documentation",
			practices: [
				{
					name: "Explains the trade-off it chose",
					artifactKind: "docs.document",
					whyItMatters:
						"A decision without its alternatives reads as arbitrary six months later. Naming what you did not do is what lets the next person tell a considered choice from an accident.",
				},
				{ name: "Written by hand, and says nothing more" },
			],
		},
	],
});

const oneDescribedPractice = buildAutonomyFixture({
	groups: [
		{
			slug: "documentation",
			name: "Documentation",
			practices: [
				{
					name: "Explains the trade-off it chose",
					artifactKind: "docs.document",
					whyItMatters: "A decision without its alternatives reads as arbitrary six months later.",
				},
			],
		},
	],
});

const nothingSetByHand = buildAutonomyFixture({
	groups: [{ slug: "testing", name: "Testing", practices: [{ name: "Covers the new branch" }] }],
});

const oneUnreviewablePractice = buildAutonomyFixture({
	groups: [
		{
			slug: "observability",
			name: "Observability",
			practices: [{ name: "Alerts a human on failure", reviewable: false }],
		},
	],
});

const atScale = scaleFixture();

const idle = {
	workspace: false,
	groupSlugs: new Set<string>(),
	practiceSlugs: new Set<string>(),
	bulk: null,
};

const meta = {
	title: "Workspace admin/Practices/Review/How much",
	component: PracticeAutonomyPage,
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
		onSetGroupAutonomy: fn(),
		onClearGroupAutonomy: fn(),
		onSetPracticeAutonomy: fn(),
		onClearPracticeAutonomy: fn(),
		onBulkSetAutonomy: fn(),
	},
	decorators: [withWidePage],
	tags: ["autodocs"],
	render: (args) => (
		<Stateful initial={args.overridesOnly}>
			{(overridesOnly, setOverridesOnly) => (
				<PracticeAutonomyPage
					{...args}
					overridesOnly={overridesOnly}
					onOverridesOnlyChange={(next) => {
						args.onOverridesOnlyChange(next);
						setOverridesOnly(next);
					}}
				/>
			)}
		</Stateful>
	),
} satisfies Meta<typeof PracticeAutonomyPage>;

export default meta;
type Story = StoryObj<typeof meta>;

export const WorkspaceDefaultUnset: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("heading", { name: "Workspace default" })).toBeVisible();
		await expect(
			canvas.getByText("Not chosen yet, so Review before sending applies."),
		).toBeVisible();
		await expect(
			canvas.getByRole("radiogroup", { name: "How far reviews go without you" }),
		).toBeVisible();
		await expect(canvas.queryByText(/feedback may go/i)).not.toBeInTheDocument();
		await expect(canvas.queryByText(/mentor conversation/i)).not.toBeInTheDocument();
		await expectNoPageOverflow();
	},
};

export const WorkspaceDefaultSet: Story = {
	args: from(chosenByTheWorkspace),
	play: async ({ args, canvas, userEvent }) => {
		await expect(
			within(canvas.getByRole("radiogroup", { name: "How far reviews go without you" })).getByRole(
				"radio",
				{ name: "Review before sending" },
			),
		).toBeChecked();

		await userEvent.click(
			canvas.getByRole("button", { name: "Use the default for how far reviews go without you" }),
		);
		await expect(args.onClearWorkspaceDefault).toHaveBeenCalled();
	},
};

export const GroupOverride: Story = {
	play: async ({ args, canvas, userEvent }) => {
		const testing = canvas.getByRole("radiogroup", {
			name: "How far reviews go in Testing",
		});
		await expect(within(testing).getByRole("radio", { name: "Off" })).toBeChecked();
		await userEvent.click(within(testing).getByRole("radio", { name: "Send automatically" }));
		const dialog = within(await screen.findByRole("alertdialog"));
		await expect(args.onSetGroupAutonomy).not.toHaveBeenCalled();
		await userEvent.click(dialog.getByRole("button", { name: "Start sending automatically" }));
		await expect(args.onSetGroupAutonomy).toHaveBeenCalledWith("testing", "AUTOMATIC");

		const unassigned = canvas.getByText("Unassigned").closest('[data-slot="accordion-item"]');
		if (!(unassigned instanceof HTMLElement)) throw new Error("No-group group not rendered");
		await expect(within(unassigned).getByText("Follows the workspace default")).toBeVisible();
		await expect(
			within(unassigned).queryByRole("radiogroup", { name: /How far reviews go in/ }),
		).not.toBeInTheDocument();
	},
};

export const WorkspaceAutomaticCanBeCancelled: Story = {
	play: async ({ args, canvas, userEvent }) => {
		const workspace = canvas.getByRole("radiogroup", {
			name: "How far reviews go without you",
		});
		await userEvent.click(within(workspace).getByRole("radio", { name: "Send automatically" }));
		const dialog = within(await screen.findByRole("alertdialog"));
		await userEvent.click(dialog.getByRole("button", { name: "Cancel" }));
		await expect(args.onSetWorkspaceDefault).not.toHaveBeenCalled();
	},
};

export const PracticeAutomaticRequiresConfirmation: Story = {
	play: async ({ args, canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: /Pull request hygiene/ }));
		const row = canvas.getByText("States the motivation").closest("li");
		if (!(row instanceof HTMLElement)) throw new Error("Practice row not rendered");
		await userEvent.click(within(row).getByRole("radio", { name: "Send automatically" }));
		const dialog = within(await screen.findByRole("alertdialog"));
		await expect(args.onSetPracticeAutonomy).not.toHaveBeenCalled();
		await userEvent.click(dialog.getByRole("button", { name: "Start sending automatically" }));
		await expect(args.onSetPracticeAutonomy).toHaveBeenCalledWith(
			"pull-request-hygiene-states-the-motivation",
			"AUTOMATIC",
		);
	},
};

export const PracticeOverrideWithReset: Story = {
	play: async ({ args, canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: /Pull request hygiene/ }));
		const row = canvas.getByText("Links the issue it closes").closest("li");
		if (!(row instanceof HTMLElement)) throw new Error("Practice row not rendered");

		await expect(within(row).getByText("Set here.")).toBeVisible();
		await expect(
			within(row).getByRole("radiogroup", {
				name: "How far reviews go on Links the issue it closes",
			}),
		).toBeVisible();

		const sibling = canvas.getByText("States the motivation").closest("li");
		if (!(sibling instanceof HTMLElement)) throw new Error("Sibling row not rendered");
		await expect(within(sibling).getByText(/^Follows/)).toBeVisible();

		await userEvent.click(
			within(row).getByRole("button", { name: /Use the default for Links the issue it closes/ }),
		);
		await expect(args.onClearPracticeAutonomy).toHaveBeenCalledWith(
			"pull-request-hygiene-links-the-issue-it-closes",
		);
	},
};

export const PracticeContext: Story = {
	args: from(oneDescribedAndOneBare),
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: /Documentation/ }));

		const described = canvas.getByText("Explains the trade-off it chose").closest("li");
		if (!(described instanceof HTMLElement)) throw new Error("Practice row not rendered");
		await expect(within(described).getByText("Document")).toBeVisible();
		await expect(
			within(described).queryByText(/reads as arbitrary six months later/),
		).not.toBeInTheDocument();

		await userEvent.hover(
			within(described).getByRole("link", { name: "Explains the trade-off it chose" }),
		);
		await expectSettledVisible(await screen.findByText(/reads as arbitrary six months later/));

		const bare = canvas.getByText("Written by hand, and says nothing more").closest("li");
		if (!(bare instanceof HTMLElement)) throw new Error("Bare row not rendered");
		await expect(within(bare).getByText("Pull or merge request")).toBeVisible();
		await expect(
			within(bare).getByRole("link", { name: "Written by hand, and says nothing more" }),
		).not.toHaveAttribute("data-slot", "hover-card-trigger");
		await expectNoPageOverflow();
	},
};

export const PracticeDetailOnKeyboardFocus: Story = {
	parameters: { chromatic: { disableSnapshot: true } },
	args: from(oneDescribedPractice),
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: /Documentation/ }));
		const link = canvas.getByRole("link", { name: "Explains the trade-off it chose" });

		await expect(screen.queryByText(/reads as arbitrary six months later/)).not.toBeInTheDocument();
		for (let step = 0; step < 12 && document.activeElement !== link; step++) {
			await userEvent.tab();
		}
		await expect(link).toHaveFocus();
		await expectSettledVisible(await screen.findByText(/reads as arbitrary six months later/));
	},
};

export const OverridesOnly: Story = {
	args: { overridesOnly: true },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Links the issue it closes")).toBeVisible();
		await expect(canvas.queryByText("States the motivation")).not.toBeInTheDocument();
		await expect(canvas.getByRole("button", { name: /^Testing/ })).toBeVisible();
		await expect(canvas.getByText("No practices here were set by hand.")).toBeVisible();
		await expect(canvas.queryByText("Handles the error state")).not.toBeInTheDocument();
	},
};

export const OverridesOnlyEmpty: Story = {
	args: { overridesOnly: true, ...from(nothingSetByHand) },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Nothing was set by hand")).toBeVisible();
	},
};

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
		await userEvent.click(await menu.findByRole("menuitem", { name: "Review before sending" }));
		await expect(args.onBulkSetAutonomy).toHaveBeenCalledWith(
			[
				"pull-request-hygiene-states-the-motivation",
				"pull-request-hygiene-links-the-issue-it-closes",
			],
			"HUMAN_APPROVAL",
		);
	},
};

export const BulkAutomaticRequiresConfirmation: Story = {
	play: async ({ args, canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: /Pull request hygiene/ }));
		await userEvent.click(
			canvas.getByRole("button", { name: /Select all 2 practices in Pull request hygiene/ }),
		);
		await userEvent.click(canvas.getByRole("button", { name: "Change the selected" }));
		await userEvent.click(
			within(await screen.findByRole("menu")).getByRole("menuitem", {
				name: "Send automatically",
			}),
		);

		const dialog = within(await screen.findByRole("alertdialog"));
		await expect(args.onBulkSetAutonomy).not.toHaveBeenCalled();

		await userEvent.click(dialog.getByRole("button", { name: "Start sending automatically" }));
		await expect(args.onBulkSetAutonomy).toHaveBeenCalledWith(
			[
				"pull-request-hygiene-states-the-motivation",
				"pull-request-hygiene-links-the-issue-it-closes",
			],
			"AUTOMATIC",
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
		await expect(args.onBulkSetAutonomy).toHaveBeenCalledWith(
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

export const AtScale: Story = {
	args: from(atScale),
	play: async ({ canvas }) => {
		await expect(
			canvas.getByText(
				/^100 practices: 6 off, 89 review before sending and 5 send automatically\. \d+ practices and \d+ groups set by hand\.$/,
			),
		).toBeVisible();
		await expect(canvas.getAllByRole("radiogroup")).toHaveLength(26);
		await expect(canvas.queryByRole("checkbox", { name: /^Select / })).not.toBeInTheDocument();
		await expectNoPageOverflow();
	},
};

/** Text and role assertions cannot detect column drift, so this story verifies geometry. */
export const DecisionsShareOneColumn: Story = {
	args: from(atScale),
	globals: { viewport: { value: "desktop" } },
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ canvas, userEvent }) => {
		await expect(window.innerWidth).toBeGreaterThanOrEqual(640);

		await userEvent.click(canvas.getByRole("button", { name: /^Pull request hygiene/ }));

		const lefts = canvas
			.getAllByRole("radiogroup", { name: /^How far reviews go (in|on) / })
			.map((group) => Math.round(group.getBoundingClientRect().left));

		await expect(lefts.length).toBeGreaterThan(25);
		await expect(Math.max(...lefts) - Math.min(...lefts)).toBeLessThanOrEqual(2);
	},
};

export const AtScaleOverridesOnly: Story = {
	args: { overridesOnly: true, ...from(atScale) },
	play: async ({ canvas }) => {
		await expect(canvas.getAllByRole("checkbox", { name: /^Select / })).toHaveLength(3);
		await expect(canvas.getByText(/^Observability: keeps the change/)).toBeVisible();
		await expectNoPageOverflow();
	},
};

export const NotReviewable: Story = {
	args: from(oneUnreviewablePractice),
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: /Observability/ }));
		await expect(
			canvas.getByText("This practice can't be reviewed automatically, so it stays off."),
		).toBeVisible();
		await expect(canvas.getByRole("checkbox", { name: /^Select / })).toHaveAttribute(
			"aria-disabled",
			"true",
		);
	},
};

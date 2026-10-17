import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, userEvent, within } from "storybook/test";

import { DetailDrawerStack } from "@/components/layout/detail-drawer/DetailDrawerStack";
import { withPageBehind } from "@/stories/decorators";
import { expectSettledVisible, settledDrawerPanel } from "@/stories/overlay";
import {
	MANY_PRACTICES,
	NO_OUTCOMES,
	PACKAGING_GROUP,
} from "@/stories/practices-across-the-workspace-story-data";
import { expectNoPanelOverflow } from "@/stories/reflow";
import { Stateful } from "@/stories/stateful";

import { ACROSS_THE_WORKSPACE } from "./across-workspace-copy";
import { CAME_FROM_YOUR_PROFILE } from "./WorkspaceComparisonTable";
import { WorkspaceGroupLevel } from "./WorkspaceGroupLevel";

/**
 * A practice group's practices over Practices across the workspace. The level has no page of its
 * own, so every story mounts a real drawer over a page.
 */
const meta = {
	component: WorkspaceGroupLevel,
	parameters: { layout: "fullscreen" },
	decorators: [withPageBehind],
	args: {
		path: { behind: [{ label: ACROSS_THE_WORKSPACE, depth: 0 }], onClose: fn() },
		state: { status: "ready", group: PACKAGING_GROUP },
		onGoToPractice: fn(),
	},
	argTypes: { path: { control: false } },
	render: (args) => (
		<Stateful initial={[{ kind: "practice-group", id: PACKAGING_GROUP.slug }]}>
			{(stack, setStack) => (
				<DetailDrawerStack stack={stack} onClose={(depth) => setStack(stack.slice(0, depth))}>
					{(_entry, level) => (
						<WorkspaceGroupLevel
							{...args}
							nested={level.nested}
							path={{
								behind: args.path.behind,
								onClose: (depth) => {
									args.path.onClose(depth);
									setStack(stack.slice(0, depth));
								},
							}}
						/>
					)}
				</DetailDrawerStack>
			)}
		</Stateful>
	),
	tags: ["autodocs"],
} satisfies Meta<typeof WorkspaceGroupLevel>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	play: async ({ args }) => {
		const panel = await settledDrawerPanel();
		const level = within(panel);
		await expectSettledVisible(level.getByRole("heading", { name: "Packaging work for review" }));
		const table = within(
			level.getByRole("table", { name: "Practices of Packaging work for review" }),
		);
		await expect(
			table.getAllByRole("button", { name: /^Open in your Practice profile /u }),
		).toHaveLength(5);
		await userEvent.click(
			table.getByRole("button", {
				name: "Open in your Practice profile Scope the change to one concern",
			}),
		);
		await expect(args.onGoToPractice).toHaveBeenCalledWith("scope-to-one-concern");
		const diff = table.getByRole("row", { name: /Keep the diff reviewable in one sitting/u });
		await expect(diff).toHaveTextContent("21 developers");
		await expect(diff).toHaveTextContent("Not met 9");
	},
};

export const NoResultsYet: Story = {
	args: {
		state: {
			status: "ready",
			group: {
				...PACKAGING_GROUP,
				practices: PACKAGING_GROUP.practices.map((one) => ({
					...one,
					developers: 0,
					outcomes: NO_OUTCOMES,
				})),
			},
		},
	},
	play: async () => {
		const level = within(await settledDrawerPanel());
		await expect(level.getAllByText("No current results")).toHaveLength(5);
		await expect(
			level.getByRole("button", {
				name: "Open in your Practice profile Scope the change to one concern",
			}),
		).toBeVisible();
	},
};

/**
 * More practices than the panel holds: the table keeps its full height and the level's body
 * scrolls, so the last rows stay in reach.
 */
export const ManyPractices: Story = {
	args: {
		state: {
			status: "ready",
			group: { ...PACKAGING_GROUP, practices: MANY_PRACTICES },
		},
	},
	play: async () => {
		const level = within(await settledDrawerPanel());
		const table = level.getByRole("table", { name: "Practices of Packaging work for review" });
		const frame = table.closest(".rounded-xl");
		if (!(frame instanceof HTMLElement)) {
			throw new Error("The table sits in its frame");
		}
		// The frame clips what overflows it, so it must be as tall as its rows.
		await expect(frame.scrollHeight).toBeLessThanOrEqual(frame.clientHeight + 1);
		// Every practice is listed, with no paging, as the Practice profile lists them.
		await expect(
			within(table).getAllByRole("button", { name: /^Open in your Practice profile /u }),
		).toHaveLength(24);
	},
};

export const Loading: Story = {
	args: { state: { status: "loading" } },
	play: async () => {
		await settledDrawerPanel();
		await expect(screen.getByRole("table")).toHaveAttribute("aria-busy", "true");
		await expect(screen.getByRole("table").closest("[data-slot=drawer-body]")).toHaveAttribute(
			"aria-busy",
			"true",
		);
	},
};

export const LoadingDark: Story = {
	args: Loading.args,
	globals: { theme: "dark" },
};

/** The overview failed: the level says so and retries, rather than closing over a stale address. */
const onRetryOverview = fn();

export const LoadError: Story = {
	args: { state: { status: "error", error: new Error("Network down"), onRetry: onRetryOverview } },
	play: async () => {
		const level = within(await settledDrawerPanel());
		await expect(level.getByText("We could not load the practice groups")).toBeVisible();
		await userEvent.click(level.getByRole("button", { name: "Retry" }));
		await expect(onRetryOverview).toHaveBeenCalledOnce();
	},
};

export const NoPractices: Story = {
	args: {
		state: { status: "ready", group: { ...PACKAGING_GROUP, practices: [] } },
	},
	play: async () => {
		const level = within(await settledDrawerPanel());
		await expect(level.getByText("No practices yet")).toBeVisible();
	},
};

/** A slug the page does not list, such as a stale address. */
export const UnknownGroup: Story = {
	args: { state: { status: "missing" } },
	play: async () => {
		const level = within(await settledDrawerPanel());
		await expect(level.getByText("We could not find this practice group")).toBeVisible();
	},
};

/** The crumb back to the page closes the level through the path the host hands it. */
export const ClosesToThePage: Story = {
	play: async ({ args }) => {
		const level = within(await settledDrawerPanel());
		await userEvent.click(level.getByRole("button", { name: /Across the workspace/u }));
		await expect(args.path.onClose).toHaveBeenCalledWith(0);
	},
};

export const Reflow: Story = {
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
	play: async () => {
		await expectNoPanelOverflow(await settledDrawerPanel());
	},
};

/**
 * The practice the reader came from in their Practice profile
 * keeps the bar on its leading edge, and says so to a screen reader: `aria-current` on its row and
 * a hidden sentence after its name. No level is open, so the row is not marked open.
 */
export const MarkedPractice: Story = {
	args: { focusPracticeSlug: PACKAGING_GROUP.practices[0]?.practiceSlug },
	play: async () => {
		const panel = await settledDrawerPanel();
		const marked = within(panel)
			.getAllByRole("row")
			.filter((row) => row.getAttribute("aria-current") === "true");
		await expect(marked).toHaveLength(1);
		const [row] = marked;
		await expect(row).toHaveTextContent(CAME_FROM_YOUR_PROFILE);
		await expect(row).not.toHaveAttribute("data-state", "open");
	},
};

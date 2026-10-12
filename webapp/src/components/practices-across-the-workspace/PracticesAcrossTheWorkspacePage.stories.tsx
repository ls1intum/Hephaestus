import type { Meta, StoryObj } from "@storybook/react-vite";
import { useState } from "react";
import { expect, fn, screen, userEvent, waitFor, within } from "storybook/test";

import {
	ACROSS_WORKSPACE,
	ACROSS_WORKSPACE_TILES,
	EMPTY_WORKSPACE,
	MANY_GROUPS_WORKSPACE,
	NOBODY_TILES,
	NOBODY_WORKSPACE,
} from "@/stories/practices-across-the-workspace-story-data";
import { expectNoPageOverflow } from "@/stories/reflow";

import {
	FOCUS_FOOTER_COPY,
	PracticeFocusFooter,
} from "@/components/practice-focus/PracticeFocusFooter";

import { LOADING_STATUS, SLOW_LOAD_AFTER_MS, SLOW_LOAD_SHORT } from "./across-workspace-copy";
import {
	PracticesAcrossTheWorkspacePage,
	type PracticesAcrossTheWorkspacePageProps,
} from "./PracticesAcrossTheWorkspacePage";

const meta = {
	component: PracticesAcrossTheWorkspacePage,
	tags: ["autodocs"],
	parameters: { layout: "fullscreen" },
	args: {
		state: { status: "ready", overview: ACROSS_WORKSPACE },
		tiles: { status: "ready", tiles: ACROSS_WORKSPACE_TILES },
		window: "DAYS_30",
		onWindowChange: fn(),
		onOpenGroup: fn(),
	},
} satisfies Meta<typeof PracticesAcrossTheWorkspacePage>;

export default meta;
type Story = StoryObj<typeof meta>;

const groupsTable = (canvas: {
	getByRole: (role: "table", options: { name: string }) => HTMLElement;
}) => within(canvas.getByRole("table", { name: "All practice groups" }));

const figuresSection = (canvas: {
	getByRole: (role: "heading", options: { level: number; name: string }) => HTMLElement;
}) => canvas.getByRole("heading", { level: 2, name: "Your figures" }).closest("section");

/** Presses an (i) and returns its panel, which opens in a portal outside the canvas. */
async function openTip(canvas: { getByRole: typeof screen.getByRole }, name: string) {
	await userEvent.click(canvas.getByRole("button", { name }));
	const panel = await screen.findByRole("dialog", { name });
	await waitFor(async () => {
		await expect(panel).toBeVisible();
	});
	return panel;
}

async function closeTip() {
	await userEvent.keyboard("{Escape}");
}

export const Default: Story = {
	play: async ({ canvas, args }) => {
		// A standing is an AI reading of the work, never a grade: the page says so before any count,
		// in one line, and its (i) says why.
		await expect(canvas.getByText(/Not a grade\.$/u)).toBeVisible();
		const about = within(await openTip(canvas, "About this page"));
		await expect(about.getByText("Not a grade:")).toBeVisible();
		await expect(
			about.getByText(/each standing comes from AI review of their work and can be wrong\./u),
		).toBeVisible();
		await closeTip();
		// The long explanations wait behind an (i): none of them is on the page itself.
		await expect(canvas.queryByText("Grey band:")).toBeNull();
		await expect(canvas.queryByText(/Every bar shows all its counts/u)).toBeNull();
		// The tiles' (i) says plainly what grey and lower mean, each line led by its term in bold, and
		// holds no count and no window, so it reads the same for every filter.
		const figures = within(await openTip(canvas, "About your figures"));
		await expect(figures.getByText("Grey band:")).toBeVisible();
		await expect(figures.getByText("Grey band:").tagName).toBe("STRONG");
		// Four short groups, each a labelled group under its own heading.
		for (const heading of [
			"What you see",
			"How to read it",
			"When the band is missing",
			"What counts",
		]) {
			await expect(figures.getByRole("group", { name: heading })).toBeVisible();
		}
		await expect(
			figures.getByText("lower than for most developers.", { exact: false }),
		).toBeVisible();
		const figuresPanel = await screen.findByRole("dialog", { name: "About your figures" });
		await expect(figuresPanel.textContent).not.toMatch(/\d|last 30 days/u);
		await closeTip();
		// The legend's (i) says what grey means, and that small counts show too.
		const bars = within(await openTip(canvas, "About the bars"));
		await expect(bars.getByText("Grey, None yet:")).toBeVisible();
		await expect(bars.getByText(/^no standing yet\./u)).toBeVisible();
		await expect(
			bars.getByText(
				/Every bar shows all its counts, however small\. So a small count can let others tell where you stand\./u,
			),
		).toBeVisible();
		await closeTip();
		// A group with parts of one, two and none draws like any other.
		await expect(
			groupsTable(canvas).getByRole("img", {
				name: "28 developers with a current standing in this workspace: 1 Needs attention, 0 Mixed feedback, 2 Going well, 25 none yet. The You marker is on none yet.",
			}),
		).toBeVisible();
		// Only the tiles read the window: the toggle sits in their section, and the bars name none.
		await expect(figuresSection(canvas)).toContainElement(
			canvas.getByRole("toolbar", { name: "Time range" }),
		);
		// The window is the section's description, not its heading.
		await expect(canvas.getByText("Last 30 days", { selector: "p" })).toBeVisible();
		await expect(
			canvas.getByRole("heading", { level: 2, name: "All practice groups" }).closest("section")
				?.textContent,
		).not.toMatch(/30 days|90 days|All time/u);
		const table = groupsTable(canvas);
		await expect(
			table.getByRole("img", {
				name: "28 developers with a current standing in this workspace: 7 Needs attention, 6 Mixed feedback, 8 Going well, 7 none yet. The You marker is on Needs attention.",
			}),
		).toBeVisible();
		await userEvent.click(
			table.getByRole("button", { name: "Open group Packaging work for review" }),
		);
		await expect(args.onOpenGroup).toHaveBeenCalledWith("review-ready-work");
	},
};

/** The row whose practices are open over the page keeps a bar on its leading edge. */
export const GroupOpen: Story = {
	args: { openGroupSlug: "review-ready-work" },
	play: async ({ canvas }) => {
		const row = groupsTable(canvas)
			.getAllByRole("row")
			.find((each) => each.textContent.startsWith("Packaging work for review"));
		await expect(row).toHaveAttribute("data-state", "open");
	},
};

/** Nobody has a standing yet: no tile has a range, and no bar counts anybody. */
export const NobodyYet: Story = {
	args: {
		state: { status: "ready", overview: NOBODY_WORKSPACE },
		tiles: { status: "ready", tiles: NOBODY_TILES },
	},
	play: async ({ canvas }) => {
		await expect(
			within(await openTip(canvas, "About your figures")).getByText(
				"no developer has a standing yet.",
				{ exact: false },
			),
		).toBeVisible();
		await closeTip();
		await expect(canvas.getAllByText("No developer is counted here yet.")).toHaveLength(3);
		// Nobody has open feedback, so that tile says so in place of a band at nought.
		await expect(canvas.getByText("Most developers here have no open feedback.")).toBeVisible();
		// Once for the whole table, not once per group.
		await expect(canvas.getAllByText("No developer has a standing yet.")).toHaveLength(1);
	},
};

/** Many groups: the table lists every one, as the Practice profile does, with no paging. */
export const ManyGroups: Story = {
	args: { state: { status: "ready", overview: MANY_GROUPS_WORKSPACE } },
	play: async ({ canvas }) => {
		const table = groupsTable(canvas);
		await expect(table.getAllByRole("button", { name: /^Open group /u })).toHaveLength(26);
	},
};

/** Another window on its way: the last figures stay, and only their section is busy. */
export const SwitchingWindow: Story = {
	args: {
		tiles: { status: "ready", tiles: ACROSS_WORKSPACE_TILES, stale: true },
		window: "DAYS_90",
	},
	play: async ({ canvas }) => {
		const table = canvas.getByRole("table", { name: "All practice groups" });
		await expect(table).toBeVisible();
		await expect(figuresSection(canvas)).toHaveAttribute("aria-busy", "true");
		await expect(canvas.getByText("Pieces of work reviewed")).toBeVisible();
		await expect(
			canvas.getByRole("heading", { level: 2, name: "All practice groups" }).closest("section"),
		).not.toHaveAttribute("aria-busy");
	},
};

export const Empty: Story = {
	args: { state: { status: "ready", overview: EMPTY_WORKSPACE } },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("No practices set up yet")).toBeVisible();
	},
};

const groupsSection = (canvas: {
	getByRole: (role: "heading", options: { level: number; name: string }) => HTMLElement;
}) => canvas.getByRole("heading", { level: 2, name: "All practice groups" }).closest("section");

/** The drawn loading line, found by the (i) it ends with. */
async function loadingLine(canvas: { findByRole: typeof screen.findByRole }) {
	const tip = await canvas.findByRole("button", { name: "About the wait" });
	const line = tip.closest<HTMLElement>("[data-slot=loading-line]");
	if (line === null) {
		throw new Error("The (i) About the wait sits outside the loading line");
	}
	return line;
}

const LOADING_ARGS = {
	state: { status: "loading" },
	tiles: { status: "loading" },
} satisfies Partial<PracticesAcrossTheWorkspacePageProps>;

/**
 * While the server counts: one calm line with a pulsing dot over skeletons in the loaded page's
 * shape, every region busy, and the page's one announcement in its live region.
 */
export const Loading: Story = {
	args: LOADING_ARGS,
	play: async ({ canvas }) => {
		await expect(figuresSection(canvas)).toHaveAttribute("aria-busy", "true");
		await expect(groupsSection(canvas)).toHaveAttribute("aria-busy", "true");
		await expect(canvas.getByRole("table", { name: "All practice groups" })).toHaveAttribute(
			"aria-busy",
			"true",
		);
		await canvas.findByText(LOADING_STATUS, { selector: "[role=status]" });
		// The drawn line repeats the live region's words, so it is hidden from screen readers.
		const line = await loadingLine(canvas);
		// A slow runner may already show the slow wording, so either counts here.
		await expect(within(line).getByText(/^Counting /u)).toHaveAttribute("aria-hidden", "true");
		await expect(canvas.getByRole("button", { name: "About the wait" })).toBeVisible();
	},
};

/** The counts take longer than usual: the line says so, and the live region does not speak again. */
export const LoadingSlow: Story = {
	args: LOADING_ARGS,
	play: async ({ canvas }) => {
		await canvas.findByText(SLOW_LOAD_SHORT, undefined, { timeout: SLOW_LOAD_AFTER_MS + 2000 });
		canvas.getByText(LOADING_STATUS, { selector: "[role=status]" });
		await expect(canvas.queryByText(SLOW_LOAD_SHORT, { selector: "[role=status]" })).toBeNull();
		const wait = within(await openTip(canvas, "About the wait"));
		await expect(wait.getByText("A large course:")).toBeVisible();
		await closeTip();
	},
};

export const LoadingDark: Story = {
	args: LOADING_ARGS,
	globals: { theme: "dark" },
};

/** At 390px the line stays on one line in the gap under the header, and nothing overflows. */
export const LoadingPhone: Story = {
	args: LOADING_ARGS,
	globals: { viewport: { value: "phone390" } },
	play: async ({ canvas }) => {
		await expectNoPageOverflow();
		const line = await loadingLine(canvas);
		await expect(line.getBoundingClientRect().height).toBeLessThanOrEqual(24);
		const heading = canvas.getByRole("heading", { level: 2, name: "Your figures" });
		await expect(line.getBoundingClientRect().bottom).toBeLessThanOrEqual(
			heading.getBoundingClientRect().top,
		);
	},
};

/** The page as the route draws it: loading until the counts arrive. The button stands in for them. */
function CountsArrive(props: PracticesAcrossTheWorkspacePageProps) {
	const [arrived, setArrived] = useState(false);
	return (
		<>
			<PracticesAcrossTheWorkspacePage {...props} {...(arrived ? {} : LOADING_ARGS)} />
			<button type="button" className="sr-only" onClick={() => setArrived(true)}>
				Counts arrive
			</button>
		</>
	);
}

/** Where each region sits and how tall it is, to compare the loading page with the loaded one. */
function layoutOf(canvas: Parameters<NonNullable<Story["play"]>>[0]["canvas"]) {
	const figures = figuresSection(canvas);
	const groups = groupsSection(canvas);
	const tiles = figures === null ? [] : [...figures.querySelectorAll("li")];
	const firstRow = canvas
		.getByRole("table", { name: "All practice groups" })
		.querySelector("tbody tr");
	return {
		figuresHeight: figures?.getBoundingClientRect().height,
		tileHeights: tiles.map((tile) => tile.getBoundingClientRect().height),
		groupsTop: groups?.getBoundingClientRect().top,
		rowHeight: firstRow?.getBoundingClientRect().height,
	};
}

/** When the counts arrive the regions stop being busy, and nothing above the rows moves or resizes. */
export const LoadingToLoaded: Story = {
	render: (args) => <CountsArrive {...args} />,
	play: async ({ canvas }) => {
		await expect(figuresSection(canvas)).toHaveAttribute("aria-busy", "true");
		await expect(groupsSection(canvas)).toHaveAttribute("aria-busy", "true");
		const loading = layoutOf(canvas);
		await userEvent.click(canvas.getByRole("button", { name: "Counts arrive" }));
		await canvas.findByRole("button", { name: "Open group Packaging work for review" });
		await expect(figuresSection(canvas)).not.toHaveAttribute("aria-busy");
		await expect(groupsSection(canvas)).not.toHaveAttribute("aria-busy");
		await expect(layoutOf(canvas)).toStrictEqual(loading);
	},
};

/**
 * On a phone, two tiles share a row and a title wraps: the figures keep their height as the counts
 * arrive. The press that loads them scrolls the page, so only the figures are compared.
 */
export const LoadingToLoadedPhone: Story = {
	render: (args) => <CountsArrive {...args} />,
	globals: { viewport: { value: "phone390" } },
	play: async ({ canvas }) => {
		const figures = () => {
			const { figuresHeight, tileHeights } = layoutOf(canvas);
			return { figuresHeight, tileHeights };
		};
		const loading = figures();
		await userEvent.click(canvas.getByRole("button", { name: "Counts arrive" }));
		await canvas.findByRole("button", { name: "Open group Packaging work for review" });
		await expect(figures()).toStrictEqual(loading);
	},
};

const onRetryOverview = fn();

/** Every region reads the overview, so its failure takes the page, with no window toggle to press. */
export const LoadError: Story = {
	args: { state: { status: "error", error: new Error("Network down"), onRetry: onRetryOverview } },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("We could not load the practice groups")).toBeVisible();
		await expect(canvas.queryByRole("toolbar", { name: "Time range" })).toBeNull();
		await userEvent.click(canvas.getByRole("button", { name: "Retry" }));
		await expect(onRetryOverview).toHaveBeenCalledOnce();
	},
};

const onRetryTiles = fn();

/** The window's figures failed: their section says so, and the bars, which read no window, stay. */
export const TilesLoadError: Story = {
	args: { tiles: { status: "error", error: new Error("Network down"), onRetry: onRetryTiles } },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("We could not load your figures")).toBeVisible();
		await expect(canvas.getByRole("table", { name: "All practice groups" })).toBeVisible();
		await userEvent.click(canvas.getByRole("button", { name: "Retry" }));
		await expect(onRetryTiles).toHaveBeenCalledOnce();
	},
};

export const Reflow: Story = {
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
	play: async (context) => {
		await expectNoPageOverflow();
		// The page cannot overflow past a table's own frame, so each table is checked inside it.
		for (const table of context.canvas.getAllByRole("table")) {
			await expect(table.offsetWidth).toBeLessThanOrEqual(table.parentElement?.clientWidth ?? 0);
		}
	},
};

const onSwitchToProfile = fn();

/**
 * Under the table of every group, the way to the same list in
 * the reader's own Practice profile, as large as a drawer's own action.
 */
export const FocusFooter: Story = {
	args: {
		focusFooter: (
			<PracticeFocusFooter
				{...FOCUS_FOOTER_COPY.workspaceAllGroups}
				placement="page"
				accessibleLabel={FOCUS_FOOTER_COPY.workspaceAllGroups.label}
				onSwitch={onSwitchToProfile}
			/>
		),
	},
	play: async ({ canvas }) => {
		const button = canvas.getByRole("button", { name: "See your own standing" });
		await expect(button).toBeVisible();
		await expect(button.getBoundingClientRect().height).toBeGreaterThanOrEqual(32);
		await userEvent.click(button);
		await expect(onSwitchToProfile).toHaveBeenCalledOnce();
	},
};

export const FocusFooterDark: Story = {
	args: FocusFooter.args,
	globals: { theme: "dark" },
};

export const FocusFooterPhone: Story = {
	args: FocusFooter.args,
	globals: { viewport: { value: "phone390" } },
	play: async ({ canvas }) => {
		await expectNoPageOverflow();
		const button = canvas.getByRole("button", { name: "See your own standing" });
		await expect(button.getBoundingClientRect().width).toBeGreaterThan(300);
	},
};

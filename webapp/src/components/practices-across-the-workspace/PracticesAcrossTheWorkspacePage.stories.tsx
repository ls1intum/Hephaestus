import type { Meta, StoryObj } from "@storybook/react-vite";
import { useState } from "react";
import { expect, fn, screen, userEvent, waitFor, within } from "storybook/test";

import {
	ACROSS_WORKSPACE,
	EMPTY_WORKSPACE,
	MANY_GROUPS_WORKSPACE,
	NO_RESULTS_WORKSPACE,
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
		onOpenGroup: fn(),
	},
} satisfies Meta<typeof PracticesAcrossTheWorkspacePage>;

export default meta;
type Story = StoryObj<typeof meta>;

const groupsTable = (canvas: {
	getByRole: (role: "table", options: { name: string }) => HTMLElement;
}) => within(canvas.getByRole("table", { name: "All practice groups" }));

const groupRow = (canvas: Parameters<typeof groupsTable>[0], name: string) =>
	groupsTable(canvas)
		.getAllByRole("row")
		.find((each) => each.textContent.startsWith(name));

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
		// A result is an AI reading of the work, never a grade: the page says so before any count,
		// in one line, and its (i) says why.
		await expect(canvas.getByText(/Not a grade\.$/u)).toBeVisible();
		const about = within(await openTip(canvas, "About this page"));
		await expect(about.getByText("Not a grade:")).toBeVisible();
		await expect(about.getByText("No names:")).toBeVisible();
		await closeTip();
		await expect(
			canvas.getByText("Results for the 28 developers whose work this workspace reviews."),
		).toBeVisible();
		// Groups in the order the server sends their practices; a practice in no group is not listed.
		await expect(
			groupsTable(canvas)
				.getAllByRole("row")
				.slice(1)
				.map((each) => each.textContent.split(/\d/u)[0]),
		).toStrictEqual([
			"Acting on review feedback",
			"Communicating in the open",
			"Packaging work for review",
			"Testing your changes",
		]);
		await expect(canvas.queryByText("Sign your commits")).toBeNull();
		// A group's results add up its practices' results; developers do not add up, so none is shown.
		const packaging = groupRow(canvas, "Packaging work for review");
		await expect(packaging).toHaveTextContent("5 practices");
		await expect(packaging).toHaveTextContent("Met 43");
		await expect(packaging).toHaveTextContent("Not met 25");
		await expect(packaging).toHaveTextContent("Does not apply 5");
		await expect(packaging).toHaveTextContent("Could not determine 3");
		await expect(packaging?.textContent).not.toMatch(/developer/u);
		await userEvent.click(
			groupsTable(canvas).getByRole("button", { name: "Open group Packaging work for review" }),
		);
		await expect(args.onOpenGroup).toHaveBeenCalledWith("review-ready-work");
	},
};

/** The row whose practices are open over the page keeps a bar on its leading edge. */
export const GroupOpen: Story = {
	args: { openGroupSlug: "review-ready-work" },
	play: async ({ canvas }) => {
		await expect(groupRow(canvas, "Packaging work for review")).toHaveAttribute(
			"data-state",
			"open",
		);
	},
};

/** Practices set up, but no result yet: every group says so instead of four zeros. */
export const NoResultsYet: Story = {
	args: { state: { status: "ready", overview: NO_RESULTS_WORKSPACE } },
	play: async ({ canvas }) => {
		await expect(groupsTable(canvas).getAllByText("No results yet")).toHaveLength(4);
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
} satisfies Partial<PracticesAcrossTheWorkspacePageProps>;

/**
 * While the server counts: one calm line with a pulsing dot over skeletons in the loaded page's
 * shape, every region busy, and the page's one announcement in its live region.
 */
export const Loading: Story = {
	args: LOADING_ARGS,
	play: async ({ canvas }) => {
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
		const heading = canvas.getByRole("heading", { level: 2, name: "All practice groups" });
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
	const groups = groupsSection(canvas);
	const firstRow = canvas
		.getByRole("table", { name: "All practice groups" })
		.querySelector("tbody tr");
	return {
		groupsTop: groups?.getBoundingClientRect().top,
		rowHeight: firstRow?.getBoundingClientRect().height,
	};
}

/** When the counts arrive the regions stop being busy, and nothing above the rows moves or resizes. */
export const LoadingToLoaded: Story = {
	render: (args) => <CountsArrive {...args} />,
	play: async ({ canvas }) => {
		await expect(groupsSection(canvas)).toHaveAttribute("aria-busy", "true");
		const loading = layoutOf(canvas);
		await userEvent.click(canvas.getByRole("button", { name: "Counts arrive" }));
		await canvas.findByRole("button", { name: "Open group Packaging work for review" });
		await expect(groupsSection(canvas)).not.toHaveAttribute("aria-busy");
		await expect(layoutOf(canvas)).toStrictEqual(loading);
	},
};

const onRetryOverview = fn();

/** Every region reads the overview, so its failure takes the page. */
export const LoadError: Story = {
	args: { state: { status: "error", error: new Error("Network down"), onRetry: onRetryOverview } },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("We could not load the practice groups")).toBeVisible();
		await expect(canvas.queryByRole("table")).toBeNull();
		await userEvent.click(canvas.getByRole("button", { name: "Retry" }));
		await expect(onRetryOverview).toHaveBeenCalledOnce();
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

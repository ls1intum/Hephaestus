import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, within } from "storybook/test";

import { withStandardPage } from "@/stories/decorators";

import { QUEUED_SCAN, SCANS } from "./fixtures";

import { ScansPage } from "./ScansPage";

const meta = {
	component: ScansPage,
	parameters: { layout: "fullscreen" },
	decorators: [withStandardPage],
	args: {
		workspaceSlug: "aet",
		scans: { status: "ready", scans: SCANS, page: 0, totalPages: 1, onPageChange: fn() },
		filters: { status: undefined, repositoryItemId: undefined },
		onFiltersChange: fn(),
	},
	argTypes: { scans: { control: false } },
	tags: ["autodocs"],
} satisfies Meta<typeof ScansPage>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	play: async ({ canvas }) => {
		const table = canvas.getByRole("table", { name: "Scans, newest first" });
		await expect(table).toBeVisible();
		const [running] = canvas.getAllByRole("link", { name: "ls1intum/hephaestus" });
		await expect(running).toHaveAttribute(
			"href",
			"/w/aet/admin/practices/scans/a0000000-0000-4000-8000-000000000002",
		);
		// The rows' statuses, apart from the status filter's words.
		const rows = within(table);
		await expect(rows.getByText("Running")).toBeVisible();
		await expect(rows.getByText("Partial")).toBeVisible();
		await expect(rows.getByText("Cancelled")).toBeVisible();
		await expect(rows.getByText("Scheduled")).toBeVisible();
		await expect(rows.getAllByText("4 of 6")).toHaveLength(2);
	},
};

export const Queued: Story = {
	args: {
		scans: { status: "ready", scans: [QUEUED_SCAN], page: 0, totalPages: 1, onPageChange: fn() },
	},
	play: async ({ canvas }) => {
		await expect(
			within(canvas.getByRole("table", { name: "Scans, newest first" })).getByText("Queued"),
		).toBeVisible();
	},
};

export const Empty: Story = {
	args: { scans: { status: "ready", scans: [], page: 0, totalPages: 0, onPageChange: fn() } },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("heading", { name: "No scans yet" })).toBeVisible();
		await expect(
			canvas.getByText(
				"Scans appear here after a repository schedule starts one. Set up a repository schedule in review settings.",
			),
		).toBeVisible();
		// Straight to the schedule, not to the settings page's first section.
		await expect(
			canvas.getByRole("link", { name: "Set up a repository schedule" }),
		).toHaveAttribute(
			"href",
			"/w/aet/admin/practices/review?section=automated-review#schedule-heading",
		);
		await expect(canvas.queryByRole("link", { name: "Start scan" })).not.toBeInTheDocument();
		await expect(canvas.queryByRole("table")).not.toBeInTheDocument();
	},
};

/** Filters that match nothing are not "no scans yet": the list offers to clear them. */
export const FilteredToNothing: Story = {
	args: {
		scans: { status: "ready", scans: [], page: 0, totalPages: 0, onPageChange: fn() },
		filters: { status: "FAILED", repositoryItemId: "42" },
	},
	play: async ({ args, canvas, userEvent }) => {
		await expect(
			canvas.getByRole("heading", { name: "No scans match these filters" }),
		).toBeVisible();
		await expect(canvas.queryByRole("heading", { name: "No scans yet" })).not.toBeInTheDocument();
		// No scan names the repository, so the filter says its item ID rather than a guessed name.
		await expect(canvas.getByText("Repository: item 42")).toBeVisible();
		await userEvent.click(canvas.getByRole("button", { name: "Clear filters" }));
		await expect(args.onFiltersChange).toHaveBeenCalledWith({
			status: undefined,
			repositoryItemId: undefined,
		});
	},
};

/** One repository's scans: the filter names it as its scans do, and removing it lists every scan. */
export const OneRepository: Story = {
	args: { filters: { status: undefined, repositoryItemId: SCANS[0]?.repository.id } },
	play: async ({ args, canvas, userEvent }) => {
		await expect(
			canvas.getByText(`Repository: ${String(SCANS[0]?.repository.label)}`),
		).toBeVisible();
		await userEvent.click(canvas.getByRole("button", { name: "Show scans of every repository" }));
		await expect(args.onFiltersChange).toHaveBeenCalledWith({
			status: undefined,
			repositoryItemId: undefined,
		});
	},
};

/** The status filter keeps the repository filter and asks for one status. */
export const FilterByStatus: Story = {
	play: async ({ args, canvas, userEvent }) => {
		const toolbar = canvas.getByRole("toolbar", { name: "Status" });
		await userEvent.click(within(toolbar).getByRole("button", { name: "Failed" }));
		await expect(args.onFiltersChange).toHaveBeenCalledWith({
			status: "FAILED",
			repositoryItemId: undefined,
		});
	},
};

export const Loading: Story = {
	args: { scans: { status: "loading" } },
	play: async ({ canvas }) => {
		const table = canvas.getByRole("table", { name: "Scans, newest first" });
		await expect(table).toHaveAttribute("aria-busy", "true");
		await expect(canvas.getByRole("columnheader", { name: "Reviewed or reused" })).toBeVisible();
	},
};

export const Error: Story = {
	args: {
		scans: {
			status: "error",
			error: { status: 500, title: "Internal Server Error" },
			onRetry: fn(),
		},
	},
	play: async ({ canvas, userEvent, args }) => {
		await expect(canvas.getByText("We could not load scans")).toBeVisible();
		await expect(canvas.queryByRole("table")).not.toBeInTheDocument();
		await userEvent.click(canvas.getByRole("button", { name: "Retry" }));
		if (args.scans.status === "error") {
			await expect(args.scans.onRetry).toHaveBeenCalledOnce();
		}
	},
};

export const Reflow: Story = {
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
};

export const RefreshFailed: Story = {
	args: {
		scans: {
			status: "ready",
			scans: SCANS,
			page: 0,
			totalPages: 1,
			onPageChange: fn(),
			refreshFailure: { error: { status: 503 }, pending: false, onRetry: fn() },
		},
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("table", { name: "Scans, newest first" })).toBeVisible();
		await expect(canvas.getByText("Scans may be out of date")).toBeVisible();
		await expect(canvas.getByRole("button", { name: "Retry" })).toBeEnabled();
	},
};

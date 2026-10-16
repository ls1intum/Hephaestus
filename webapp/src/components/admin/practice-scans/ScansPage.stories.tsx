import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn } from "storybook/test";

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
		await expect(canvas.getByText("Running")).toBeVisible();
		await expect(canvas.getByText("Partial")).toBeVisible();
		await expect(canvas.getByText("Cancelled")).toBeVisible();
		await expect(canvas.getByText("Scheduled")).toBeVisible();
		await expect(canvas.getAllByText("4 of 6")).toHaveLength(2);
	},
};

export const Queued: Story = {
	args: {
		scans: { status: "ready", scans: [QUEUED_SCAN], page: 0, totalPages: 1, onPageChange: fn() },
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Queued")).toBeVisible();
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
		await expect(canvas.getByRole("link", { name: "Open review settings" })).toHaveAttribute(
			"href",
			"/w/aet/admin/practices/review",
		);
		await expect(canvas.queryByRole("link", { name: "Start scan" })).not.toBeInTheDocument();
		await expect(canvas.queryByRole("table")).not.toBeInTheDocument();
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

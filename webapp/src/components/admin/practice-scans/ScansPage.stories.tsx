import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn } from "storybook/test";

import { withStandardPage } from "@/stories/decorators";

import { levelsOpenedBy } from "@/test/detail-stack";
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
	argTypes: { scans: { control: false }, children: { control: false } },
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
		await expect(levelsOpenedBy(canvas.getByRole("link", { name: "Start scan" }))).toEqual([
			"scan-new:draft",
		]);
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
			canvas.getByText("Start a scan to review the files in a repository."),
		).toBeVisible();
		await expect(canvas.getAllByRole("link", { name: "Start scan" })).toHaveLength(2);
		await expect(canvas.queryByRole("table")).not.toBeInTheDocument();
	},
};

export const Loading: Story = {
	args: { scans: { status: "loading" } },
	play: async ({ canvas }) => {
		const table = canvas.getByRole("table", { name: "Scans, newest first" });
		await expect(table).toHaveAttribute("aria-busy", "true");
		await expect(canvas.getByRole("columnheader", { name: "Reviewed" })).toBeVisible();
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
	globals: { viewport: { value: "reflow" } },
};

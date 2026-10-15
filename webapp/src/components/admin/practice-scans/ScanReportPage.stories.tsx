import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn } from "storybook/test";

import type { ScanReport, ScanSummary } from "@/api/types.gen";
import { withStandardPage } from "@/stories/decorators";
import { StatefulPatch } from "@/stories/stateful";

import {
	CANCELLED_REPORT,
	CANCELLED_SCAN,
	COMPLETE_REPORT,
	COMPLETE_SCAN,
	FIRST_BASELINE_REPORT,
	ITEM_ENTRIES,
	METHOD_CHANGED_REPORT,
	PARTIAL_REPORT,
	PARTIAL_SCAN,
	PROBLEM_ENTRIES,
	QUEUED_SCAN,
	RUNNING_REPORT,
	RUNNING_SCAN,
	scanDetail,
	scanReport,
	SUSPECT_LISTING_REPORT,
	WITHHELD_NO_PROBLEMS_REPORT,
} from "./fixtures";
import { ScanReportPage } from "./ScanReportPage";

const list = <T,>(entries: readonly T[]) => ({
	status: "ready" as const,
	entries,
	hasNext: false,
	loadingMore: false,
	onLoadMore: fn(),
});

const loaded = (scan: ScanSummary, report: ScanReport) => ({
	status: "ready" as const,
	scan: scanDetail(scan),
	report,
});

const meta = {
	component: ScanReportPage,
	parameters: { layout: "fullscreen" },
	decorators: [withStandardPage],
	args: {
		workspaceSlug: "aet",
		state: loaded(COMPLETE_SCAN, COMPLETE_REPORT),
		tab: "problems",
		onTabChange: fn(),
		problems: list(PROBLEM_ENTRIES),
		problemFilter: undefined,
		onProblemFilterChange: fn(),
		files: list(ITEM_ENTRIES),
		fileFilter: undefined,
		onFileFilterChange: fn(),
		cancel: { pending: false, onRun: fn() },
		exportSarif: { pending: false, onRun: fn() },
	},
	argTypes: {
		state: { control: false },
		problems: { control: false },
		files: { control: false },
		cancel: { control: false },
		exportSarif: { control: false },
	},
	render: (args) => (
		<StatefulPatch initial={{ tab: args.tab }}>
			{(value, patch) => (
				<ScanReportPage
					{...args}
					tab={value.tab}
					onTabChange={(tab) => {
						patch({ tab });
						args.onTabChange(tab);
					}}
				/>
			)}
		</StatefulPatch>
	),
	tags: ["autodocs"],
} satisfies Meta<typeof ScanReportPage>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Complete: Story = {
	play: async ({ canvas, userEvent, args }) => {
		await expect(canvas.getByRole("heading", { name: "ls1intum/hephaestus" })).toBeVisible();
		await expect(canvas.getByText("Started by Ada Lovelace")).toBeVisible();
		await expect(canvas.queryByRole("button", { name: "Cancel scan" })).not.toBeInTheDocument();
		await userEvent.click(canvas.getByRole("button", { name: "Export SARIF" }));
		await expect(args.exportSarif.onRun).toHaveBeenCalledOnce();

		await userEvent.click(canvas.getByRole("tab", { name: "Practices" }));
		await expect(args.onTabChange).toHaveBeenCalledWith("practices");
		await expect(canvas.getByRole("table", { name: "Practices in this scan" })).toBeVisible();
	},
};

export const Queued: Story = {
	args: {
		state: loaded(QUEUED_SCAN, scanReport(QUEUED_SCAN)),
		problems: list([]),
		files: list([]),
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("button", { name: "Cancel scan" })).toBeVisible();
		await expect(canvas.queryByRole("button", { name: "Export SARIF" })).not.toBeInTheDocument();
	},
};

export const Running: Story = {
	args: { state: loaded(RUNNING_SCAN, RUNNING_REPORT) },
	play: async ({ canvas, userEvent, args }) => {
		await expect(
			canvas.getByText(
				"2 practice and file pairs finished so far. The total is known once every file is listed. 2 running, 0 waiting.",
			),
		).toBeVisible();
		await expect(canvas.queryByText(/%/u)).not.toBeInTheDocument();
		await userEvent.click(canvas.getByRole("button", { name: "Cancel scan" }));
		await expect(args.cancel.onRun).toHaveBeenCalledOnce();
	},
};

export const Cancelling: Story = {
	args: {
		state: loaded(RUNNING_SCAN, RUNNING_REPORT),
		cancel: { pending: true, onRun: fn() },
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("button", { name: "Cancelling scan…" })).toBeDisabled();
	},
};

export const CancelFailed: Story = {
	args: {
		state: loaded(RUNNING_SCAN, RUNNING_REPORT),
		cancel: {
			pending: false,
			error: { status: 500, detail: "The server had a problem." },
			onRun: fn(),
		},
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByText("We could not cancel the scan")).toBeVisible();
		await expect(canvas.getByRole("button", { name: "Cancel scan" })).toBeEnabled();
	},
};

export const Partial: Story = {
	args: { state: loaded(PARTIAL_SCAN, PARTIAL_REPORT) },
	play: async ({ canvas }) => {
		await expect(
			canvas.getByText(
				/The scan did not finish\. These counts cover only the work it reached\. The scan’s budget was used up\./u,
			),
		).toBeVisible();
		await expect(canvas.getByText("Not comparable")).toBeVisible();
		await expect(canvas.getByRole("button", { name: "Export SARIF" })).toBeEnabled();
	},
};

export const Cancelled: Story = {
	args: { state: loaded(CANCELLED_SCAN, CANCELLED_REPORT), problems: list([]), files: list([]) },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Started by Scheduled")).toBeVisible();
		await expect(canvas.getByText(/A workspace admin cancelled it\./u)).toBeVisible();
	},
};

export const FirstBaseline: Story = {
	args: {
		state: loaded(COMPLETE_SCAN, FIRST_BASELINE_REPORT),
		problems: list(PROBLEM_ENTRIES.slice(0, 1)),
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByText("First scan")).toBeVisible();
		await expect(canvas.getByRole("button", { name: "New (3)" })).toBeVisible();
	},
};

export const MethodChanged: Story = {
	args: {
		state: loaded(COMPLETE_SCAN, METHOD_CHANGED_REPORT),
		problems: list(PROBLEM_ENTRIES.slice(0, 1).map((entry) => ({ ...entry, baselineState: null }))),
	},
	play: async ({ canvas }) => {
		await expect(
			canvas.getByText(
				"The review method changed. This scan cannot show which problems were fixed.",
			),
		).toBeVisible();
		await expect(canvas.queryByText("Now met")).not.toBeInTheDocument();
	},
};

export const SuspectListing: Story = {
	args: { state: loaded(COMPLETE_SCAN, SUSPECT_LISTING_REPORT) },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Listing not trusted")).toBeVisible();
		await expect(canvas.getByText(/12 of 40 earlier files were missing\./u)).toBeVisible();
	},
};

export const WithheldNoProblems: Story = {
	args: {
		state: loaded(WITHHELD_NO_PROBLEMS_REPORT.scan, WITHHELD_NO_PROBLEMS_REPORT),
		problems: list([]),
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("region", { name: "Why pairs were withheld" })).toHaveTextContent(
			"2 · The assigned AI model was unavailable.",
		);
		await expect(canvas.queryByText(/SOMETHING_NEW/u)).not.toBeInTheDocument();
		await expect(
			canvas.getByText(
				"That does not mean every practice is met: 4 practice and file pairs have no result in this scan.",
			),
		).toBeVisible();
	},
};

export const Exporting: Story = {
	args: { exportSarif: { pending: true, onRun: fn() } },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("button", { name: "Exporting SARIF…" })).toBeDisabled();
	},
};

export const ExportFailed: Story = {
	args: {
		exportSarif: {
			pending: false,
			error: { status: 410, detail: "This scan’s details are past their retention." },
			onRun: fn(),
		},
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByText("We could not export the SARIF file")).toBeVisible();
		await expect(canvas.getByText("This scan’s details are past their retention.")).toBeVisible();
		await expect(canvas.getByRole("region", { name: "Counts" })).toBeVisible();
		await expect(canvas.getByRole("button", { name: "Export SARIF" })).toBeEnabled();
	},
};

export const FilesTab: Story = {
	args: { tab: "files" },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("table", { name: "Files and practices" })).toBeVisible();
	},
};

export const Loading: Story = {
	args: { state: { status: "loading" } },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Loading scan report")).toHaveClass("sr-only");
		await expect(canvas.queryByRole("heading")).not.toBeInTheDocument();
	},
};

export const Error: Story = {
	args: { state: { status: "error", error: { status: 500 }, onRetry: fn() } },
	play: async ({ canvas, userEvent, args }) => {
		await expect(canvas.getByText("We could not load this scan")).toBeVisible();
		await userEvent.click(canvas.getByRole("button", { name: "Retry" }));
		if (args.state.status === "error") {
			await expect(args.state.onRetry).toHaveBeenCalledOnce();
		}
	},
};

export const NotFound: Story = {
	args: { state: { status: "error", error: { status: 404 }, onRetry: fn() } },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("We could not find this scan")).toBeVisible();
		await expect(canvas.queryByRole("button", { name: "Retry" })).not.toBeInTheDocument();
		await expect(canvas.getByRole("link", { name: "Scans" })).toBeVisible();
	},
};

export const Reflow: Story = {
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
};

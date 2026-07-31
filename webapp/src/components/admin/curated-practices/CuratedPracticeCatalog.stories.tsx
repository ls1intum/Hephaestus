import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen } from "storybook/test";
import { expectNoPageOverflow } from "@/test/reflow";
import { CuratedPracticeCatalog } from "./CuratedPracticeCatalog";

const practices = [
	{
		slug: "clear-pr-description",
		name: "Write a clear pull request description",
		artifactType: "PULL_REQUEST" as const,
		areaSlug: "communication",
		revisionNumber: 3,
		revisionCreatedAt: "2026-07-30T12:00:00Z",
		version: 4,
		status: "AVAILABLE" as const,
		sourceKind: "BUNDLED" as const,
		syncStatus: "UPDATE_AVAILABLE" as const,
		latestBundledCatalogRevision: 4,
	},
	{
		slug: "focused-commits",
		name: "Keep commits focused",
		artifactType: "PULL_REQUEST" as const,
		areaSlug: "version-control",
		revisionNumber: 1,
		revisionCreatedAt: "2026-06-20T08:00:00Z",
		version: 1,
		status: "AVAILABLE" as const,
		sourceKind: "INSTANCE" as const,
		syncStatus: "INSTANCE" as const,
	},
	{
		slug: "actionable-issues",
		name: "Create actionable issues",
		artifactType: "ISSUE" as const,
		revisionNumber: 2,
		revisionCreatedAt: "2026-05-01T10:00:00Z",
		version: 2,
		status: "RETIRED" as const,
		sourceKind: "BUNDLED" as const,
		syncStatus: "SOURCE_REMOVED" as const,
		latestBundledCatalogRevision: 2,
	},
];

const meta = {
	title: "Instance admin/Curated catalog",
	component: CuratedPracticeCatalog,
	parameters: {
		layout: "padded",
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 1440] },
	},
	args: {
		areas: [
			{ slug: "communication", name: "Communication", displayOrder: 0 },
			{ slug: "version-control", name: "Version control", displayOrder: 1 },
		],
		practices,
		search: {},
		onSearchChange: fn(),
		onStatusChange: fn(),
	},
	decorators: [
		(Story) => (
			<div className="mx-auto w-full max-w-5xl">
				<Story />
			</div>
		),
	],
	tags: ["autodocs"],
} satisfies Meta<typeof CuratedPracticeCatalog>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Available: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Write a clear pull request description")).toBeInTheDocument();
		await expect(canvas.queryByText("Create actionable issues")).not.toBeInTheDocument();
		await expect(canvas.getByText("Hephaestus update available")).toBeInTheDocument();
		await expect(canvas.getByText("Instance-created")).toBeInTheDocument();
		await expectNoPageOverflow();
	},
};

export const Retired: Story = {
	args: { search: { status: "RETIRED" } },
	play: async ({ args, canvas, userEvent }) => {
		await expect(canvas.getByText("Create actionable issues")).toBeInTheDocument();
		await userEvent.click(canvas.getByRole("button", { name: "Restore Create actionable issues" }));
		await expect(args.onStatusChange).toHaveBeenCalledWith(practices[2], "AVAILABLE");
	},
};

export const RetirementConfirmation: Story = {
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ args, canvas, userEvent }) => {
		await userEvent.click(
			canvas.getByRole("button", { name: "Retire Write a clear pull request description" }),
		);
		await expect(screen.getByText(/Existing workspace copies are unaffected/)).toBeInTheDocument();
		await userEvent.click(screen.getByRole("button", { name: "Retire practice" }));
		await expect(args.onStatusChange).toHaveBeenCalledWith(practices[0], "RETIRED");
	},
};

export const Pending: Story = {
	args: { pendingSlugs: new Set(["clear-pr-description"]) },
};

export const Empty: Story = {
	args: { practices: [] },
};

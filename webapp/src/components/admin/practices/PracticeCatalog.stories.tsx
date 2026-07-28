import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, waitFor, within } from "storybook/test";
import { expectNoPageOverflow } from "@/test/reflow";
import { PracticeCatalog } from "./PracticeCatalog";
import {
	mockAreas,
	mockPracticeLongText,
	mockPractices,
	mockUnassignedPractice,
} from "./story-mock-data";

const areas = [
	{
		...mockAreas[0],
		name: "Packaging work so reviewers can understand its purpose without reconstructing context",
		active: false,
	},
	mockAreas[1],
];

const practices = [
	{
		...mockPracticeLongText,
		areaSlug: areas[0].slug,
		precomputeScript: "export default {};",
	},
	{
		...mockPractices[2],
		areaSlug: areas[0].slug,
		displayOrder: 1,
	},
	{
		...mockUnassignedPractice,
		artifactType: "ISSUE" as const,
	},
];

const idlePending = {
	areaSlugs: new Set<string>(),
	practiceSlugs: new Set<string>(),
	areaStructure: false,
	blockedMoveDestinationSlugs: new Set<string>(),
	blockedPracticeOrderBuckets: new Set<string>(),
	creatingArea: false,
};

const meta = {
	title: "Admin/Practices/Catalog",
	component: PracticeCatalog,
	parameters: {
		a11y: { test: "error" },
		layout: "padded",
		chromatic: { viewports: [320, 1440] },
		viewport: { defaultViewport: "reflow" },
	},
	args: {
		workspaceSlug: "demo",
		areas,
		practices,
		pending: idlePending,
		focusFilter: "ALL",
		onFocusFilterChange: fn(),
		onCreateArea: fn(async () => true),
		onRenameArea: fn(async () => true),
		onToggleAreaActive: fn(),
		onDeleteArea: fn(),
		onReorderAreas: fn(),
		onSetAreaVisual: fn(),
		onSetPracticeActive: fn(),
		onDeletePractice: fn(),
		onPlacePractice: fn(),
	},
	decorators: [
		(Story) => (
			<div className="mx-auto w-full max-w-5xl">
				<Story />
			</div>
		),
	],
	tags: ["autodocs"],
} satisfies Meta<typeof PracticeCatalog>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Populated: Story = {
	play: async ({ canvas }) => {
		const filters = canvas.getAllByLabelText("Filter by reviewed work");
		await waitFor(() =>
			expect(filters.filter((filter) => filter.getClientRects().length > 0)).toHaveLength(1),
		);
		await expect(canvas.getByText("Dashboard hidden")).toBeVisible();
		await expect(canvas.getByText("Paused")).toBeVisible();
		await expect(canvas.getByText(mockUnassignedPractice.name)).toBeVisible();
		await expectNoPageOverflow();
	},
};

export const Filtered: Story = {
	args: { focusFilter: "ISSUE" },
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Clear the filter to move practices.")).toBeVisible();
		await expect(canvas.queryByRole("button", { name: /Move practice/ })).not.toBeInTheDocument();
	},
};

export const MoveToUnassigned: Story = {
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ args, canvas, userEvent }) => {
		await userEvent.click(
			canvas.getByRole("button", { name: `More actions for ${mockPracticeLongText.name}` }),
		);
		const moveTo = await screen.findByRole("menuitem", { name: "Move to" });
		moveTo.focus();
		await userEvent.keyboard("{ArrowRight}");
		await userEvent.click(await screen.findByRole("menuitemradio", { name: "Unassigned" }));
		await expect(args.onPlacePractice).toHaveBeenCalledWith(mockPracticeLongText.slug, null, 1);
		await userEvent.keyboard("{Escape}");
		await waitFor(() =>
			expect(screen.queryByRole("menuitemradio", { name: "Unassigned" })).not.toBeInTheDocument(),
		);
		await userEvent.keyboard("{Escape}");
		await waitFor(() => expect(screen.queryAllByRole("menu")).toHaveLength(0));
	},
};

export const Reordering: Story = {
	args: {
		pending: {
			...idlePending,
			blockedPracticeOrderBuckets: new Set([areas[0].slug]),
		},
	},
	play: async ({ canvas }) => {
		await expect(
			canvas.getByRole("button", {
				name: `Move practice ${mockPracticeLongText.name}`,
			}),
		).toBeDisabled();
		await expect(
			canvas.getByRole("button", {
				name: `Move practice ${mockUnassignedPractice.name}`,
			}),
		).toBeEnabled();
		await expect(canvas.getByRole("button", { name: "Add area" })).toBeEnabled();
		await expect(canvas.getByRole("link", { name: "New practice" })).toBeEnabled();
	},
};

export const DeletingArea: Story = {
	args: {
		pending: {
			...idlePending,
			areaSlugs: new Set([areas[0].slug]),
			areaStructure: true,
			blockedMoveDestinationSlugs: new Set([areas[0].slug]),
			blockedPracticeOrderBuckets: new Set([areas[0].slug, "__unassigned__"]),
		},
	},
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(
			canvas.getByRole("button", { name: `More actions for ${mockUnassignedPractice.name}` }),
		);
		const moveTo = await screen.findByRole("menuitem", { name: "Move to" });
		moveTo.focus();
		await userEvent.keyboard("{ArrowRight}");
		await expect(
			screen.queryByRole("menuitemradio", { name: areas[0].name }),
		).not.toBeInTheDocument();
		await expect(screen.getByRole("menuitemradio", { name: areas[1].name })).toBeEnabled();
		await userEvent.keyboard("{Escape}{Escape}");
		await waitFor(() => expect(screen.queryAllByRole("menu")).toHaveLength(0));
	},
};

export const Empty: Story = {
	args: { areas: [], practices: [] },
	parameters: { chromatic: { viewports: [1440] } },
};

export const EmptyDestinations: Story = {
	args: {
		areas: mockAreas,
		practices: [],
	},
	play: async ({ canvas }) => {
		await expect(canvas.getAllByText("No practices in this area.")).toHaveLength(mockAreas.length);
		await expect(canvas.getByText("No unassigned practices.")).toBeVisible();
		await expectNoPageOverflow();
	},
};

export const CrossAreaDrag: Story = {
	args: {
		areas: mockAreas,
		practices: [{ ...mockPractices[0], areaSlug: mockAreas[0].slug }],
	},
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ args, canvas, userEvent }) => {
		const practice = mockPractices[0];
		const handle = canvas.getByRole("button", { name: `Move practice ${practice.name}` });
		const destinationArea = canvas
			.getByText(mockAreas[1].name)
			.closest('[data-slot="accordion-item"]');
		if (!(destinationArea instanceof HTMLElement)) throw new Error("Destination area not rendered");
		const destination = within(destinationArea).getByText("No practices in this area.");
		const sourceRow = handle.closest('[data-slot="item"]');
		if (!(sourceRow instanceof HTMLElement)) throw new Error("Practice row not rendered");
		const start = handle.getBoundingClientRect();
		const end = destination.getBoundingClientRect();

		await userEvent.pointer([
			{
				target: handle,
				coords: { x: start.left + start.width / 2, y: start.top + start.height / 2 },
				keys: "[MouseLeft>]",
			},
			{
				target: handle,
				coords: { x: start.left + start.width / 2, y: start.top + start.height / 2 + 10 },
			},
		]);
		await expect(handle).toBeVisible();
		const preview = await waitFor(() => {
			const rows = screen
				.getAllByText(practice.name)
				.map((name) => name.closest('[data-slot="item"]'))
				.filter((row): row is HTMLElement => row instanceof HTMLElement && row !== sourceRow);
			expect(rows).toHaveLength(1);
			return rows[0];
		});
		expect(
			Math.abs(preview.getBoundingClientRect().width - sourceRow.getBoundingClientRect().width),
		).toBeLessThan(1);
		await userEvent.pointer([
			{
				target: destination,
				coords: { x: end.left + end.width / 2, y: end.top + end.height / 2 },
			},
			{ target: destination, keys: "[/MouseLeft]" },
		]);

		await expect(args.onPlacePractice).toHaveBeenCalledWith(practice.slug, mockAreas[1].slug, 0);
	},
};

export const BetweenRowsDrag: Story = {
	args: {
		areas: mockAreas,
		practices: [
			{ ...mockPractices[0], areaSlug: mockAreas[0].slug },
			{ ...mockPractices[1], areaSlug: mockAreas[1].slug, displayOrder: 0 },
			{ ...mockPractices[2], areaSlug: mockAreas[1].slug, displayOrder: 1 },
		],
	},
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ args, canvas, userEvent }) => {
		const source = mockPractices[0];
		const anchor = mockPractices[2];
		const handle = canvas.getByRole("button", { name: `Move practice ${source.name}` });
		const anchorRow = canvas.getByText(anchor.name).closest<HTMLElement>('[data-slot="item"]');
		if (!anchorRow) throw new Error("Destination practice row not rendered");
		const start = handle.getBoundingClientRect();
		const end = anchorRow.getBoundingClientRect();

		await userEvent.pointer([
			{
				target: handle,
				coords: { x: start.left + start.width / 2, y: start.top + start.height / 2 },
				keys: "[MouseLeft>]",
			},
			{
				target: handle,
				coords: { x: start.left + start.width / 2, y: start.top + start.height / 2 + 10 },
			},
			{
				target: anchorRow,
				coords: { x: end.left + end.width / 2, y: end.top + end.height * 0.25 },
			},
			{ target: anchorRow, keys: "[/MouseLeft]" },
		]);

		await expect(args.onPlacePractice).toHaveBeenCalledWith(source.slug, mockAreas[1].slug, 1);
	},
};

export const BlockedDestinationDrag: Story = {
	args: {
		areas: mockAreas,
		practices: [{ ...mockPractices[0], areaSlug: undefined }],
		pending: {
			...idlePending,
			blockedMoveDestinationSlugs: new Set([mockAreas[1].slug]),
		},
	},
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ args, canvas, userEvent }) => {
		const practice = mockPractices[0];
		const handle = canvas.getByRole("button", { name: `Move practice ${practice.name}` });
		const destinationArea = canvas
			.getByText(mockAreas[1].name)
			.closest<HTMLElement>('[data-slot="accordion-item"]');
		if (!destinationArea) throw new Error("Blocked destination area not rendered");
		const destination = within(destinationArea).getByText("No practices in this area.");
		const start = handle.getBoundingClientRect();
		const end = destination.getBoundingClientRect();

		await userEvent.pointer([
			{
				target: handle,
				coords: { x: start.left + start.width / 2, y: start.top + start.height / 2 },
				keys: "[MouseLeft>]",
			},
			{
				target: handle,
				coords: { x: start.left + start.width / 2, y: start.top + start.height / 2 + 10 },
			},
			{
				target: destination,
				coords: { x: end.left + end.width / 2, y: end.top + end.height / 2 },
			},
			{ target: destination, keys: "[/MouseLeft]" },
		]);

		await expect(args.onPlacePractice).not.toHaveBeenCalled();
	},
};

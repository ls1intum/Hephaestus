import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, waitFor, within } from "storybook/test";
import { mockPracticeDefinitionOptions } from "@/mocks/fixtures/practice";
import { expectNoPageOverflow } from "@/test/reflow";
import { PracticeCatalog } from "./PracticeCatalog";
import {
	chosenTier,
	inheritedTier,
	mockAreas,
	mockPracticeLongText,
	mockPractices,
	mockUnassignedPractice,
} from "./story-mock-data";

const areas = [
	{
		...mockAreas[0],
		name: "Packaging work so reviewers can understand its purpose without reconstructing context",
		visibleInPracticeDashboards: false,
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
		artifactKind: "scm.issue" as const,
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
	title: "Workspace admin/Practices/Practice setup",
	component: PracticeCatalog,
	parameters: {
		layout: "padded",
		chromatic: { viewports: [320, 1440] },
		viewport: { defaultViewport: "reflow" },
	},
	args: {
		workspaceSlug: "demo",
		areas,
		practices,
		definitionOptions: mockPracticeDefinitionOptions,
		pending: idlePending,
		focusFilter: "ALL",
		onFocusFilterChange: fn(),
		onCreateArea: fn(async () => true),
		onRenameArea: fn(async () => true),
		onSetAreaDashboardVisibility: fn(),
		onDeleteArea: fn(),
		onReorderAreas: fn(),
		onSetAreaVisual: fn(),
		onSetPracticeReviewTier: fn(),
		onClearPracticeReviewTier: fn(),
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
	play: async () => {
		await expectNoPageOverflow();
	},
};

export const Filtered: Story = {
	args: { focusFilter: "scm.issue" },
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Clear the filter to reorder practices.")).toBeVisible();
		await expect(canvas.queryByRole("button", { name: /Move practice/ })).not.toBeInTheDocument();
	},
};

export const MoveToUnassigned: Story = {
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ args, canvas, userEvent }) => {
		await userEvent.click(
			canvas.getByRole("button", { name: `More actions for ${mockPracticeLongText.name}` }),
		);
		await userEvent.click(await screen.findByRole("menuitemradio", { name: "Unassigned" }));
		await expect(args.onPlacePractice).toHaveBeenCalledWith(mockPracticeLongText.slug, null, 1);
	},
};

export const MoveWithoutDragging: Story = {
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ args, canvas, userEvent }) => {
		await userEvent.click(
			canvas.getByRole("button", { name: `More actions for ${mockPracticeLongText.name}` }),
		);
		await userEvent.click(await screen.findByRole("menuitem", { name: "Move down" }));
		await expect(args.onPlacePractice).toHaveBeenCalledWith(
			mockPracticeLongText.slug,
			areas[0].slug,
			1,
		);
	},
};

export const MoveAreaWithoutDragging: Story = {
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ args, canvas, userEvent }) => {
		await userEvent.click(
			canvas.getByRole("button", { name: `More actions for ${areas[0].name}` }),
		);
		await userEvent.click(await screen.findByRole("menuitem", { name: "Move down" }));
		await expect(args.onReorderAreas).toHaveBeenCalledWith([areas[1].slug, areas[0].slug]);
	},
};

export const KeyboardReordering: Story = {
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ args, canvas }) => {
		const handle = canvas.getByRole("button", {
			name: `Reorder ${mockPracticeLongText.name}`,
		});
		handle.focus();
		handle.dispatchEvent(new KeyboardEvent("keydown", { key: " ", code: "Space", bubbles: true }));
		await screen.findByText(`Picked up ${mockPracticeLongText.name}.`);
		handle.dispatchEvent(
			new KeyboardEvent("keydown", { key: "ArrowDown", code: "ArrowDown", bubbles: true }),
		);
		await screen.findByText(new RegExp(`Moving ${mockPracticeLongText.name}`));
		handle.dispatchEvent(new KeyboardEvent("keydown", { key: " ", code: "Space", bubbles: true }));
		await expect(args.onPlacePractice).toHaveBeenCalledWith(
			mockPracticeLongText.slug,
			areas[0].slug,
			1,
		);
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
				name: `Reorder ${mockPracticeLongText.name}`,
			}),
		).toBeDisabled();
		await expect(
			canvas.getByRole("button", {
				name: `Reorder ${mockUnassignedPractice.name}`,
			}),
		).toBeEnabled();
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
	play: async ({ canvas, userEvent }) => {
		const actions = canvas.getByRole("button", {
			name: `More actions for ${mockUnassignedPractice.name}`,
		});
		await userEvent.click(actions);
		await expect(await screen.findByRole("menuitemradio", { name: areas[1].name })).toBeEnabled();
		await expect(screen.getByRole("menuitemradio", { name: areas[0].name })).toHaveAttribute(
			"aria-disabled",
			"true",
		);
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
		for (const area of mockAreas) {
			const areaSection = canvas.getByText(area.name).closest('[data-slot="accordion-item"]');
			if (!(areaSection instanceof HTMLElement)) throw new Error(`Area ${area.name} not rendered`);
			await expect(within(areaSection).getByText("No practices in this area.")).toBeVisible();
		}
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
		const handle = canvas.getByRole("button", { name: `Reorder ${practice.name}` });
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
		const handle = canvas.getByRole("button", { name: `Reorder ${source.name}` });
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
		const handle = canvas.getByRole("button", { name: `Reorder ${practice.name}` });
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

export const AutonomyTiers: Story = {
	args: {
		areas: mockAreas,
		practices: [
			{ ...mockPractices[0], slug: "loud", name: "Deliver", reviewTier: inheritedTier("DELIVER") },
			{ ...mockPractices[0], slug: "quiet", name: "Observe", reviewTier: chosenTier("OBSERVE") },
			{ ...mockPractices[0], slug: "silent", name: "Off", reviewTier: chosenTier("OFF") },
		].map((practice) => ({ ...practice, areaSlug: mockAreas[0].slug })),
	},
	play: async ({ canvas, userEvent }) => {
		for (const tier of ["Deliver", "Observe", "Off"]) {
			await expect(canvas.getByLabelText(`How far Hephaestus may go on ${tier}`)).toHaveTextContent(
				tier,
			);
		}
		// Deliver is what a practice nobody has configured inherits, so it carries no badge; the quieter
		// tiers each announce themselves, because those are the rows where a developer will see less
		// than the practice's name suggests.
		const badges = canvas.getAllByText(/^(Off|Observe)$/, {
			selector: '[data-slot="badge"]',
		});
		await expect(badges).toHaveLength(2);
		await expectNoPageOverflow();

		// Propose is a rung with nothing behind it yet — no queue for a human to approve what it would
		// prepare — and the server refuses it at every write boundary. It is offered and disabled rather
		// than omitted: dropping it leaves a gap between "records it" and "says it unasked" that the
		// ladder has no word for, and offering it plainly would fail after the click.
		await userEvent.click(canvas.getByLabelText("How far Hephaestus may go on Deliver"));
		const options = within(await screen.findByRole("listbox"));
		await expect(options.getByRole("option", { name: "Propose" })).toHaveAttribute(
			"aria-disabled",
			"true",
		);
		await expect(options.getByRole("option", { name: "Observe" })).not.toHaveAttribute(
			"aria-disabled",
			"true",
		);
	},
};

/**
 * A tier chosen on the practice itself can be handed back to the area or the workspace. Without this
 * the chain is write-once from the catalogue: the first pick pins a tier and nothing takes it off.
 */
export const ClearingAnOverride: Story = {
	args: {
		areas: mockAreas,
		practices: [
			{ ...mockPractices[0], slug: "held", name: "Set here", reviewTier: chosenTier("OBSERVE") },
			{
				...mockPractices[0],
				slug: "free",
				name: "Inheriting",
				reviewTier: inheritedTier("DELIVER"),
			},
		].map((practice) => ({ ...practice, areaSlug: mockAreas[0].slug })),
	},
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ args, canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: "More actions for Inheriting" }));
		await expect(await screen.findByRole("menuitem", { name: "Use the default" })).toHaveAttribute(
			"aria-disabled",
			"true",
		);
		await userEvent.keyboard("{Escape}");

		await userEvent.click(canvas.getByRole("button", { name: "More actions for Set here" }));
		await userEvent.click(await screen.findByRole("menuitem", { name: "Use the default" }));
		await expect(args.onClearPracticeReviewTier).toHaveBeenCalledWith("held");
	},
};

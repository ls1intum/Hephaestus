import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, waitFor, within } from "storybook/test";
import { mockPracticeDefinitionOptions } from "@/mocks/fixtures/practice";
import { StatefulPatch } from "@/stories/stateful";
import { expectSettledVisible } from "@/test/overlay";
import { expectNoPageOverflow } from "@/test/reflow";
import { PracticeCatalog } from "./PracticeCatalog";
import {
	areaAutonomy,
	chosenAutonomy,
	inheritedAutonomy,
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

/** The size a workspace installs the shipped catalogue at. */
const scaleAreas = [
	"Submitting review-ready work",
	"Writing issues a maintainer can act on",
	"Reviewing other people's work",
	"Testing what changed",
	"Documentation",
	"Keeping the build green",
	"Talking about work in the open",
	"Dependencies and supply chain",
].map((name, index) => ({
	...mockAreas[0],
	id: 100 + index,
	slug: `scale-area-${index}`,
	name,
	displayOrder: index,
}));

const scalePractices = scaleAreas.flatMap((area, areaIndex) =>
	[
		"states the motivation",
		"links the issue it closes",
		"lists the steps a reviewer ran",
		"keeps the change reviewable in one sitting",
	].map((suffix, practiceIndex) => ({
		...mockPractices[0],
		id: 1000 + areaIndex * 10 + practiceIndex,
		slug: `${area.slug}-${practiceIndex}`,
		name: `${area.name}: ${suffix}`,
		areaSlug: area.slug,
		displayOrder: practiceIndex,
	})),
);

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
	render: (args) => (
		<StatefulPatch initial={{ focusFilter: args.focusFilter, areas: args.areas }}>
			{(view, patch) => (
				<PracticeCatalog
					{...args}
					focusFilter={view.focusFilter}
					areas={view.areas}
					onFocusFilterChange={(focusFilter) => {
						args.onFocusFilterChange(focusFilter);
						patch({ focusFilter });
					}}
					onSetAreaVisual={(slug, visual) => {
						args.onSetAreaVisual(slug, visual);
						patch({
							areas: view.areas.map((area) => (area.slug === slug ? { ...area, ...visual } : area)),
						});
					}}
					onSetAreaDashboardVisibility={(slug, visibleInPracticeDashboards) => {
						args.onSetAreaDashboardVisibility(slug, visibleInPracticeDashboards);
						patch({
							areas: view.areas.map((area) =>
								area.slug === slug ? { ...area, visibleInPracticeDashboards } : area,
							),
						});
					}}
				/>
			)}
		</StatefulPatch>
	),
} satisfies Meta<typeof PracticeCatalog>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Populated: Story = {
	play: async () => {
		await expectNoPageOverflow();
	},
};

/**
 * The move menu lists every area in the workspace, so it is the one control whose height grows with
 * the catalogue rather than with the row it belongs to — which is what the narrow viewport here is
 * watching.
 */
export const AtScale: Story = {
	args: { areas: scaleAreas, practices: scalePractices },
	parameters: { chromatic: { viewports: [320, 1440] } },
	play: async ({ canvas }) => {
		await expect(canvas.getAllByRole("button", { name: /^Reorder / })).toHaveLength(40);
		await expectNoPageOverflow();
	},
};

/**
 * A locally written practice usually carries no prose, and a popup that appears empty under the
 * pointer is worse than no popup.
 */
export const PracticeDetailOnHover: Story = {
	args: {
		practices: [
			{ ...mockPractices[0], areaSlug: areas[0].slug, displayOrder: 0 },
			{ ...mockUnassignedPractice, areaSlug: areas[0].slug, displayOrder: 1 },
		],
	},
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ canvas, userEvent }) => {
		await expect(screen.queryByText(/reverse-engineering the diff/)).not.toBeInTheDocument();

		// Asserted on the element rather than by hovering and waiting for nothing, which would pass just
		// as well if the card were merely slow.
		await expect(
			canvas.getByRole("link", { name: mockUnassignedPractice.name }),
		).not.toHaveAttribute("data-slot", "hover-card-trigger");

		const trigger = canvas.getByRole("link", { name: mockPractices[0].name });
		await expect(trigger).toHaveAttribute("data-slot", "hover-card-trigger");
		await userEvent.hover(trigger);
		await expectSettledVisible(await screen.findByText(/reverse-engineering the diff/));
		await expect(screen.getByText(/lists the exact steps a reviewer ran/)).toBeVisible();
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

export const AutonomyLevels: Story = {
	args: {
		areas: mockAreas,
		practices: [
			{
				...mockPractices[0],
				slug: "from-workspace",
				name: "Nobody has touched this one",
				autonomy: inheritedAutonomy("AUTOMATIC"),
			},
			{
				...mockPractices[0],
				slug: "from-area",
				name: "Its area decided",
				autonomy: areaAutonomy("HUMAN_APPROVAL"),
			},
			{
				...mockPractices[0],
				slug: "held",
				name: "Singled out",
				autonomy: chosenAutonomy("OFF"),
			},
		].map((practice) => ({ ...practice, areaSlug: mockAreas[0].slug })),
	},
	play: async ({ canvas }) => {
		// Scoped row by row: asserting that all three sentences exist somewhere on the page would pass
		// just as well if every row carried the same one, which is the mix-up this story is about.
		const expected = [
			["Nobody has touched this one", "Send automatically", "Follows the workspace default"],
			["Its area decided", "Review before sending", `Follows ${mockAreas[0].name}`],
			["Singled out", "Off", "Set for this practice"],
		];

		for (const [name, autonomy, decidedBy] of expected) {
			const listitem = canvas.getByText(name).closest('[role="listitem"]');
			if (!(listitem instanceof HTMLElement)) throw new Error(`No row for ${name}`);
			const row = within(listitem);
			await expect(row.getByText(autonomy).closest('[data-slot="badge"]')).toBeVisible();
			await expect(row.getByText(decidedBy)).toBeVisible();
		}
		await expectNoPageOverflow();
	},
};

export const AutonomyIsReadOnlyHere: Story = {
	args: {
		areas: mockAreas,
		practices: [
			{
				...mockPractices[0],
				slug: "held",
				name: "Set here",
				autonomy: chosenAutonomy("HUMAN_APPROVAL"),
			},
		].map((practice) => ({ ...practice, areaSlug: mockAreas[0].slug })),
	},
	parameters: {
		chromatic: { disableSnapshot: true },
		// The actions menu holds every area in the workspace, so on a short viewport Base UI caps it at
		// the available height and the a11y check rejects the scrollable region that makes — a property
		// of the menu, not of the link this story is about.
		viewport: { defaultViewport: "desktop" },
	},
	play: async ({ canvas, userEvent }) => {
		await expect(canvas.queryByRole("radiogroup")).not.toBeInTheDocument();

		await userEvent.click(canvas.getByRole("button", { name: "More actions for Set here" }));
		const menu = within(await screen.findByRole("menu"));
		await expect(menu.getByRole("menuitem", { name: "Change on Review" })).toHaveAttribute(
			"href",
			"/w/demo/admin/practices/review",
		);
		await expect(menu.queryByRole("menuitem", { name: "Use the default" })).not.toBeInTheDocument();
	},
};

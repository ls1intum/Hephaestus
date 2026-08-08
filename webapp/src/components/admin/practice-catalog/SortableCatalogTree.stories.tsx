import type { Meta, StoryObj } from "@storybook/react-vite";
import { useState } from "react";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import { Button } from "@/components/ui/button";
import {
	DropdownMenu,
	DropdownMenuContent,
	DropdownMenuItem,
	DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
	type SortableCatalogArea,
	type SortableCatalogEntry,
	SortableCatalogTree,
} from "./SortableCatalogTree";

const areas: SortableCatalogArea[] = [
	{ slug: "delivery", name: "Delivery", displayOrder: 0 },
	{ slug: "quality", name: "Quality", displayOrder: 1 },
];

const entries: SortableCatalogEntry[] = [
	{
		slug: "small-changes",
		name: "Small, reviewable changes",
		areaSlug: "delivery",
		displayOrder: 0,
	},
	{
		slug: "explain-why",
		name: "Explain what changed and why",
		areaSlug: "delivery",
		displayOrder: 1,
	},
	{
		slug: "no-stale-drafts",
		name: "Drafts are not left open",
		areaSlug: "delivery",
		displayOrder: 2,
	},
	{
		slug: "tests-with-changes",
		name: "Tests ship with the change",
		areaSlug: "quality",
		displayOrder: 0,
	},
	{ slug: "orphan", name: "Not filed anywhere yet", displayOrder: 0 },
];

interface HarnessProps {
	/** Areas whose rows may not be reordered and which no row may be moved into. */
	blocked?: readonly string[];
	/** When set, only these rows are listed — the shape a search box produces. */
	visible?: readonly string[];
	onPlaceEntry: (entrySlug: string, areaSlug: string | null, position: number) => void;
	onReorderAreas: (orderedSlugs: string[]) => void;
}

/**
 * Stands in for `PracticeCatalog` and `CuratedCatalogTree`: it applies the moves the tree asks for,
 * which is what makes focus restoration and the badge counts observable at all.
 *
 * <p>Its menu items are `aria-disabled` and still clickable rather than natively `disabled`. That is
 * the a11y-preferred shape for a menu item, and it is also what lets one story ask both halves of the
 * question the tree answers twice over — *is* this destination offered, and does asking for it anyway
 * do nothing.
 */
function CatalogTreeHarness({ blocked = [], visible, onPlaceEntry, onReorderAreas }: HarnessProps) {
	const [rows, setRows] = useState(entries);
	const blockedSlugs = new Set(blocked);

	const place = (entrySlug: string, areaSlug: string | null, position: number) => {
		onPlaceEntry(entrySlug, areaSlug, position);
		setRows((previous) => {
			const moved = previous.find((row) => row.slug === entrySlug);
			if (!moved) return previous;
			const rest = previous.filter((row) => row.slug !== entrySlug);
			const destination = rest
				.filter((row) => (row.areaSlug ?? null) === areaSlug)
				.sort((a, b) => a.displayOrder - b.displayOrder);
			destination.splice(position, 0, { ...moved, areaSlug: areaSlug ?? undefined });
			const renumbered = new Map(destination.map((row, index) => [row.slug, index]));
			return [...rest, moved].map((row) => {
				const order = renumbered.get(row.slug);
				return order == null
					? row
					: { ...row, areaSlug: areaSlug ?? undefined, displayOrder: order };
			});
		});
	};

	return (
		<SortableCatalogTree
			areas={areas}
			entries={rows}
			visibleEntrySlugs={visible ? new Set(visible) : undefined}
			areaReorderDisabled={false}
			disabledAreaSlugs={new Set()}
			disabledEntrySlugs={new Set()}
			blockedEntryOrderBuckets={blockedSlugs}
			blockedMoveDestinationSlugs={blockedSlugs}
			showEntryReorderHandles
			onReorderAreas={onReorderAreas}
			onPlaceEntry={place}
			renderAreaActions={(area, move) => (
				<Button
					variant="outline"
					size="sm"
					ref={move.actionTriggerRef}
					aria-disabled={!move.canMoveDown}
					aria-label={`Move ${area.name} down`}
					onClick={move.moveDown}
				>
					Area down
				</Button>
			)}
			renderEntryContent={(entry) => <span className="min-w-0 truncate">{entry.name}</span>}
			renderEntryActions={(entry, move) => (
				<DropdownMenu>
					<DropdownMenuTrigger
						render={
							<Button
								ref={move.actionTriggerRef}
								variant="ghost"
								size="sm"
								aria-label={`More actions for ${entry.name}`}
							/>
						}
					>
						Actions
					</DropdownMenuTrigger>
					<DropdownMenuContent align="end">
						<DropdownMenuItem aria-disabled={!move.canMoveUp} onClick={move.moveUp}>
							Move up
						</DropdownMenuItem>
						<DropdownMenuItem aria-disabled={!move.canMoveDown} onClick={move.moveDown}>
							Move down
						</DropdownMenuItem>
						<DropdownMenuItem
							aria-disabled={move.currentAreaSlug !== null && !move.canMoveTo(null)}
							onClick={() => move.moveTo(null)}
						>
							Move to Unassigned
						</DropdownMenuItem>
						{areas.map((area) => (
							<DropdownMenuItem
								key={area.slug}
								aria-disabled={move.currentAreaSlug !== area.slug && !move.canMoveTo(area.slug)}
								onClick={() => move.moveTo(area.slug)}
							>
								Move to {area.name}
							</DropdownMenuItem>
						))}
					</DropdownMenuContent>
				</DropdownMenu>
			)}
			getEmptyLabel={(_areaSlug, total) =>
				total > 0 ? "No practice here matches the search." : "Nothing filed here yet."
			}
		/>
	);
}

const meta = {
	title: "Workspace admin/Practices/Catalog tree",
	component: CatalogTreeHarness,
	parameters: { layout: "padded" },
	args: { onPlaceEntry: fn(), onReorderAreas: fn() },
	tags: ["autodocs"],
} satisfies Meta<typeof CatalogTreeHarness>;

export default meta;
type Story = StoryObj<typeof meta>;

const openActions = async (canvas: ReturnType<typeof within>, name: string) => {
	await userEvent.click(canvas.getByRole("button", { name: `More actions for ${name}` }));
	return within(await screen.findByRole("menu"));
};

/**
 * A pointer drag from a row's grip, ending `dy` pixels lower. The sensor arms after 6px, so the
 * first step is always past that even when the drag ends where it started.
 */
const dragBy = async (handle: HTMLElement, dy: number) => {
	const box = handle.getBoundingClientRect();
	const clientX = Math.round(box.left + box.width / 2);
	const top = Math.round(box.top + box.height / 2);
	const settle = () => new Promise((resolve) => requestAnimationFrame(resolve));
	const send = (type: string, clientY: number, target: EventTarget = document) =>
		target.dispatchEvent(
			new PointerEvent(type, {
				bubbles: true,
				cancelable: true,
				button: 0,
				isPrimary: true,
				pointerId: 1,
				clientX,
				clientY,
			}),
		);

	send("pointerdown", top, handle);
	await settle();
	// The sensor arms on this one; dnd-kit measures and re-renders over the next two frames.
	send("pointermove", top + 12);
	await settle();
	await settle();
	send("pointermove", top + dy);
	await settle();
	await settle();
	send("pointerup", top + dy);
	await settle();
	await settle();
};

export const Default: Story = {};

/**
 * A move driven from the row's menu leaves the reader where they were.
 *
 * <p>Moving a row to another area unmounts it from one bucket and mounts it in the other, so the
 * menu's own focus restoration has nothing left to return to and focus falls to the document. Without
 * the tree re-focusing the moved row's trigger, a keyboard reader is dropped at the top of the page
 * after every move (WCAG 2.2 SC 2.4.3).
 */
export const MovingBetweenAreasKeepsFocusOnTheRow: Story = {
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		const menu = await openActions(canvas, "Small, reviewable changes");

		await userEvent.click(menu.getByRole("menuitem", { name: "Move to Quality" }));

		await expect(args.onPlaceEntry).toHaveBeenCalledWith("small-changes", "quality", 1);
		await expect(
			await canvas.findByRole("button", { name: "More actions for Small, reviewable changes" }),
		).toHaveFocus();
	},
};

/**
 * While a move is in flight the area it would land in is off limits, and the tree says so on the
 * control rather than accepting the click and being refused a round trip later.
 */
export const AnAreaWithAMoveInFlightIsNotADestination: Story = {
	args: { blocked: ["quality"] },
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		const menu = await openActions(canvas, "Small, reviewable changes");
		const blocked = menu.getByRole("menuitem", { name: "Move to Quality" });

		await expect(blocked).toHaveAttribute("aria-disabled", "true");
		// Unassigned is not in flight, so the row can still go there — this is one blocked area and
		// not a row that has stopped moving.
		await expect(menu.getByRole("menuitem", { name: "Move to Unassigned" })).toHaveAttribute(
			"aria-disabled",
			"false",
		);

		await userEvent.click(blocked);
		await expect(args.onPlaceEntry).not.toHaveBeenCalled();
	},
};

/**
 * The ends of a list. Both the offer and the move itself have to refuse, because the caller renders
 * the control and may leave it reachable — as this harness does, and as `aria-disabled` menu items
 * generally are.
 */
export const TheEndsOfAnAreaHaveNowhereToGo: Story = {
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		const first = await openActions(canvas, "Small, reviewable changes");
		const up = first.getByRole("menuitem", { name: "Move up" });
		await expect(up).toHaveAttribute("aria-disabled", "true");
		await userEvent.click(up);
		await expect(args.onPlaceEntry).not.toHaveBeenCalled();

		const last = await openActions(canvas, "Drafts are not left open");
		const down = last.getByRole("menuitem", { name: "Move down" });
		await expect(down).toHaveAttribute("aria-disabled", "true");
		await userEvent.click(down);
		await expect(args.onPlaceEntry).not.toHaveBeenCalled();
	},
};

/** The middle of the same list still moves, so the refusals above are about the ends. */
export const ReorderingInsideAnArea: Story = {
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		const menu = await openActions(canvas, "Explain what changed and why");

		await userEvent.click(menu.getByRole("menuitem", { name: "Move down" }));

		await expect(args.onPlaceEntry).toHaveBeenCalledWith("explain-why", "delivery", 2);
	},
};

/**
 * The badge counts the area, not the search.
 *
 * <p>A filtered tree hides rows; it does not shrink the areas. "Delivery 3" next to one visible row
 * is what tells the reader the other two are behind their filter rather than gone.
 */
export const FilteringHidesRowsWithoutShrinkingTheCounts: Story = {
	args: { visible: ["explain-why", "tests-with-changes"] },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);

		const delivery = canvas.getByRole("button", { name: /^Delivery/ });
		await expect(delivery).toHaveTextContent("3");
		await expect(canvas.getByText("Explain what changed and why")).toBeVisible();
		await expect(canvas.queryByText("Small, reviewable changes")).toBeNull();

		// Unassigned is a bucket rather than an area, and counts the same way.
		await expect(canvas.getByText("Unassigned").parentElement).toHaveTextContent("1");
		await expect(canvas.getByText("No practice here matches the search.")).toBeVisible();
	},
};

/**
 * Picking a row up and putting it back down is not an edit.
 *
 * <p>The keyboard path through drag-and-drop ends in a drop wherever it ends, including on the row's
 * own position — and every drop that reaches the server costs a round trip and a re-render of the
 * whole catalog. Nothing above this tree can tell that drop apart from a real one.
 */
export const DroppingARowWhereItAlreadyIsIsNotAMove: Story = {
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		const first = canvas.getByRole("button", { name: "Reorder Small, reviewable changes" });
		const second = canvas.getByRole("button", { name: "Reorder Explain what changed and why" });
		const rowHeight = second.getBoundingClientRect().top - first.getBoundingClientRect().top;

		// Onto the next row but short of its midpoint, which resolves to "before it" — the position
		// the row is already in.
		await dragBy(first, rowHeight - 10);

		// The drop resolved a destination — this is the guard refusing a no-op, not a drag that never
		// found anywhere to land and would refuse everything.
		await expect(
			await screen.findByText("Moved Small, reviewable changes to position 1 of 3 in Delivery."),
		).toBeInTheDocument();
		await expect(args.onPlaceEntry).not.toHaveBeenCalled();
	},
};

/** …and the same drag ending one row lower is a move, so the guard above is not a mute. */
export const DraggingARowOntoTheNext: Story = {
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		const first = canvas.getByRole("button", { name: "Reorder Small, reviewable changes" });
		const second = canvas.getByRole("button", { name: "Reorder Explain what changed and why" });
		const rowHeight = second.getBoundingClientRect().top - first.getBoundingClientRect().top;

		// Past the next row's midpoint, which is what makes this an "after" rather than a "before".
		await dragBy(first, rowHeight + 10);

		await expect(args.onPlaceEntry).toHaveBeenCalledWith("small-changes", "delivery", 1);
	},
};

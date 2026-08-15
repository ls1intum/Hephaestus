import type { Meta, StoryObj } from "@storybook/react-vite";
import { useState } from "react";
import { expect, fn, screen, userEvent, waitFor, within } from "storybook/test";
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
	/** Areas whose own rows may not be reordered. Kept apart from {@link blockedDestinations} so a
	 * story can tell which of the tree's two independent guards it exercised. */
	blockedOrderBuckets?: readonly string[];
	blockedDestinations?: readonly string[];
	/** Areas that may not change their own position, while everything inside them still moves. */
	disabledAreas?: readonly string[];
	/** Rows that may not move at all — not up, not down, and not into another area. */
	disabledEntries?: readonly string[];
	/** When set, only these rows are listed — the shape a search box produces. */
	visible?: readonly string[];
	onPlaceEntry: (entrySlug: string, areaSlug: string | null, position: number) => void;
	onReorderAreas: (orderedSlugs: string[]) => void;
}

/**
 * Applies the moves the tree asks for, which is what makes focus restoration and the badge counts
 * observable at all.
 *
 * Its menu items are `aria-disabled` and still clickable rather than natively `disabled` — the
 * a11y-preferred shape for a menu item, and what lets these stories ask both halves of the question:
 * *is* the destination offered, and does asking for it anyway do nothing.
 */
function CatalogTreeHarness({
	blockedOrderBuckets = [],
	blockedDestinations = [],
	disabledAreas = [],
	disabledEntries = [],
	visible,
	onPlaceEntry,
	onReorderAreas,
}: HarnessProps) {
	const [rows, setRows] = useState(entries);

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
			disabledAreaSlugs={new Set(disabledAreas)}
			disabledEntrySlugs={new Set(disabledEntries)}
			blockedEntryOrderBuckets={new Set(blockedOrderBuckets)}
			blockedMoveDestinationSlugs={new Set(blockedDestinations)}
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

/**
 * No `autodocs`: the component under test is `SortableCatalogTree`, whose API is twelve props and
 * five render callbacks, and what the stories render is a four-prop harness around it. An
 * auto-generated page here would publish the harness's props as if they were the component's, which
 * is worse than no page. The harness's own props are still Controls, because they are what these
 * stories vary.
 */
const meta = {
	title: "Shared/Practice catalog/Catalog tree",
	component: CatalogTreeHarness,
	parameters: { layout: "padded" },
	args: { onPlaceEntry: fn(), onReorderAreas: fn() },
} satisfies Meta<typeof CatalogTreeHarness>;

export default meta;
type Story = StoryObj<typeof meta>;

const openActions = async (canvas: ReturnType<typeof within>, name: string) => {
	// A menu left over from a previous step outlives the click that dismissed it by a frame, and
	// `findByRole("menu")` would hand back that one.
	await waitFor(() => expect(screen.queryByRole("menu")).toBeNull());
	await userEvent.click(canvas.getByRole("button", { name: `More actions for ${name}` }));
	return within(await screen.findByRole("menu"));
};

/** What dnd-kit is telling a screen reader right now. Also how this file waits for it. */
const announcement = () =>
	document.querySelector('[id^="DndLiveRegion"]')?.textContent?.trim() ?? "";

const rowOf = (canvas: ReturnType<typeof within>, name: string) => {
	const row = canvas.getByRole("button", { name: `Reorder ${name}` }).closest('[role="listitem"]');
	if (!(row instanceof HTMLElement)) throw new Error(`No row for ${name}`);
	return row;
};

/**
 * A pointer drag from a row's grip, stepping past the sensor's activation distance and waiting on
 * each announcement rather than on a frame count — under a full suite run there is no fixed number
 * of frames that is both enough and not wasteful.
 */
const dragTo = async (handle: HTMLElement, clientY: number) => {
	const box = handle.getBoundingClientRect();
	const clientX = Math.round(box.left + box.width / 2);
	const startY = Math.round(box.top + box.height / 2);
	const send = (type: string, y: number, target: EventTarget = document) =>
		target.dispatchEvent(
			new PointerEvent(type, {
				bubbles: true,
				cancelable: true,
				button: 0,
				isPrimary: true,
				pointerId: 1,
				clientX,
				clientY: y,
			}),
		);

	send("pointerdown", startY, handle);
	send("pointermove", startY + (clientY < startY ? -12 : 12));
	await waitFor(() => expect(announcement()).toMatch(/^Picked up/));
	send("pointermove", clientY);
	await waitFor(() => expect(announcement()).toMatch(/^Moving/));
	send("pointerup", clientY);
	await waitFor(() => expect(announcement()).toMatch(/^(Moved|Move cancelled)/));
};

export const Default: Story = {};

/**
 * A move unmounts the row from one bucket and mounts it in the other, so the menu's own focus
 * restoration has nothing left to return to and focus falls to the document. Without the tree
 * re-focusing the moved row's trigger, a keyboard reader is dropped at the top of the page after
 * every move (WCAG 2.2 SC 2.4.3 Focus Order).
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
	args: { blockedDestinations: ["quality"] },
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		const menu = await openActions(canvas, "Explain what changed and why");
		const blocked = menu.getByRole("menuitem", { name: "Move to Quality" });

		await expect(blocked).toHaveAttribute("aria-disabled", "true");
		// Unassigned is not in flight, so the row can still go there: this is one blocked area and not
		// a row that has stopped moving.
		await expect(menu.getByRole("menuitem", { name: "Move to Unassigned" })).toHaveAttribute(
			"aria-disabled",
			"false",
		);
		// Blocking a destination says nothing about ordering inside the row's own area, which is the
		// other prop. A mid-list row can still move within Delivery.
		await expect(menu.getByRole("menuitem", { name: "Move up" })).toHaveAttribute(
			"aria-disabled",
			"false",
		);

		await userEvent.click(blocked);
		await expect(args.onPlaceEntry).not.toHaveBeenCalled();
	},
};

/**
 * The mirror image, and the reason the two are separate props: a bucket whose order is in flight
 * refuses its own up and down while remaining a destination anything may be moved into.
 */
export const AnAreaWithAReorderInFlightStillAcceptsArrivals: Story = {
	args: { blockedOrderBuckets: ["delivery"] },
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		const menu = await openActions(canvas, "Explain what changed and why");

		await expect(menu.getByRole("menuitem", { name: "Move up" })).toHaveAttribute(
			"aria-disabled",
			"true",
		);
		await expect(menu.getByRole("menuitem", { name: "Move down" })).toHaveAttribute(
			"aria-disabled",
			"true",
		);
		await expect(menu.getByRole("menuitem", { name: "Move to Quality" })).toHaveAttribute(
			"aria-disabled",
			"false",
		);

		await userEvent.click(menu.getByRole("menuitem", { name: "Move up" }));
		await expect(args.onPlaceEntry).not.toHaveBeenCalled();
	},
};

/**
 * Both the offer and the move itself have to refuse, because the caller renders the control and may
 * leave it reachable.
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
 * The third guard, and the narrowest: one area cannot change its own position while everything
 * inside it still moves normally.
 *
 * <p>This is not `areaReorderDisabled`, which stops every area at once, and not
 * `blockedEntryOrderBuckets`, which stops the rows. The screen sets it for the single area whose
 * write is in flight, so the assertion that matters is the one about the *other* area and about this
 * area's own rows — a guard that quietly froze the whole tree would satisfy the first assertion
 * alone.
 */
export const AnAreaWithItsOwnMoveInFlightHoldsItsPosition: Story = {
	args: { disabledAreas: ["delivery"] },
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);

		await expect(canvas.getByRole("button", { name: "Move Delivery down" })).toHaveAttribute(
			"aria-disabled",
			"true",
		);
		await expect(canvas.getByRole("button", { name: "Reorder Delivery" })).toBeDisabled();
		// Quality's own grip, not its "Area down": it is the last area, so down is refused by the
		// ends-of-list arithmetic whatever this prop does, and asserting that would prove nothing.
		await expect(canvas.getByRole("button", { name: "Reorder Quality" })).toBeEnabled();

		// Its contents are untouched by it, which is the whole point of the prop being per-area.
		const menu = await openActions(canvas, "Explain what changed and why");
		await userEvent.click(menu.getByRole("menuitem", { name: "Move down" }));
		await expect(args.onPlaceEntry).toHaveBeenCalledWith("explain-why", "delivery", 2);
	},
};

/**
 * The fourth guard: one row is pinned, and unlike a blocked bucket it has no way out either.
 *
 * <p>`blockedEntryOrderBuckets` stops a row reordering while leaving it free to leave its area;
 * this stops the row itself, so the "Move to …" items go with the up and down. The sibling row is
 * asserted too, because a guard applied to the bucket rather than to the slug would look identical
 * on the pinned row alone.
 */
export const ARowWithItsOwnMoveInFlightCannotLeaveEither: Story = {
	args: { disabledEntries: ["explain-why"] },
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		const pinned = await openActions(canvas, "Explain what changed and why");

		for (const name of ["Move up", "Move down", "Move to Quality", "Move to Unassigned"]) {
			await expect(pinned.getByRole("menuitem", { name })).toHaveAttribute("aria-disabled", "true");
		}
		await userEvent.click(pinned.getByRole("menuitem", { name: "Move to Quality" }));
		await expect(args.onPlaceEntry).not.toHaveBeenCalled();

		await expect(
			canvas.getByRole("button", { name: "Reorder Explain what changed and why" }),
		).toBeDisabled();

		const sibling = await openActions(canvas, "Drafts are not left open");
		await expect(sibling.getByRole("menuitem", { name: "Move to Quality" })).toHaveAttribute(
			"aria-disabled",
			"false",
		);
	},
};

/**
 * A filtered tree hides rows; it does not shrink the areas. The count beside one visible row is what
 * tells the reader the rest are behind the filter rather than gone.
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
 * Picking a row up and putting it back down is not an edit. The keyboard path through drag-and-drop
 * ends in a drop wherever it ends, including on the row's own position, and every drop that reaches
 * the server costs a round trip and a re-render of the whole catalog.
 */
export const DroppingARowWhereItAlreadyIsIsNotAMove: Story = {
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		const next = rowOf(canvas, "Explain what changed and why").getBoundingClientRect();

		// Onto the next row but short of its midpoint, which resolves to "before it" — the position
		// the dragged row is already in.
		await dragTo(
			canvas.getByRole("button", { name: "Reorder Small, reviewable changes" }),
			Math.round(next.top + 2),
		);

		// The drop resolved a destination — this is the guard refusing a no-op, not a drag that never
		// found anywhere to land and would refuse everything.
		await expect(announcement()).toBe(
			"Moved Small, reviewable changes to position 1 of 3 in Delivery.",
		);
		await expect(args.onPlaceEntry).not.toHaveBeenCalled();
	},
};

/** …and the same drag carried past that midpoint is a move, so the guard above is not a mute. */
export const DraggingARowOntoTheNext: Story = {
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		const next = rowOf(canvas, "Explain what changed and why").getBoundingClientRect();

		await dragTo(
			canvas.getByRole("button", { name: "Reorder Small, reviewable changes" }),
			Math.round(next.bottom - 2),
		);

		await expect(args.onPlaceEntry).toHaveBeenCalledWith("small-changes", "delivery", 1);
	},
};

import {
	type CollisionDetection,
	closestCenter,
	DndContext,
	type DragEndEvent,
	type DragOverEvent,
	DragOverlay,
	type DragStartEvent,
	KeyboardSensor,
	PointerSensor,
	pointerWithin,
	useDroppable,
	useSensor,
	useSensors,
} from "@dnd-kit/core";
import { restrictToVerticalAxis } from "@dnd-kit/modifiers";
import {
	arrayMove,
	SortableContext,
	sortableKeyboardCoordinates,
	useSortable,
	verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { GripVertical } from "lucide-react";
import { type ReactNode, useRef, useState } from "react";
import {
	Accordion,
	AccordionContent,
	AccordionItem,
	AccordionTrigger,
} from "@/components/ui/accordion";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Item, ItemActions, ItemGroup } from "@/components/ui/item";
import { cn } from "@/lib/utils";
import { type CatalogDropTarget, getCatalogDropTarget } from "./catalog-tree-dnd";

export const UNASSIGNED_CATALOG_BUCKET = "__unassigned__";

export interface SortableCatalogArea {
	displayOrder: number;
	name: string;
	slug: string;
}

export interface SortableCatalogEntry {
	areaSlug?: string;
	displayOrder: number;
	moveSourceAreaSlug?: string;
	name: string;
	slug: string;
}

/** Restores focus to the row's action trigger after a reorder moves it. */
export type ActionTriggerRef = (node: HTMLButtonElement | null) => void;

export interface CatalogMoveActions {
	canMoveDown: boolean;
	canMoveUp: boolean;
	moveDown: () => void;
	moveUp: () => void;
}

export interface CatalogEntryMoveActions extends CatalogMoveActions {
	canMoveTo: (areaSlug: string | null) => boolean;
	currentAreaSlug: string | null;
	moveTo: (areaSlug: string | null) => void;
}

export interface SortableCatalogTreeProps<
	TArea extends SortableCatalogArea,
	TEntry extends SortableCatalogEntry,
> {
	areas: readonly TArea[];
	entries: readonly TEntry[];
	visibleEntrySlugs?: ReadonlySet<string>;
	forceOpenAreaSlugs?: ReadonlySet<string>;
	areaReorderDisabled: boolean;
	disabledAreaSlugs: ReadonlySet<string>;
	disabledEntrySlugs: ReadonlySet<string>;
	blockedEntryOrderBuckets: ReadonlySet<string>;
	blockedMoveDestinationSlugs: ReadonlySet<string>;
	showEntryReorderHandles: boolean;
	onReorderAreas: (orderedSlugs: string[]) => void;
	onPlaceEntry: (entrySlug: string, areaSlug: string | null, position: number) => void;
	renderAreaLeading?: (area: TArea) => ReactNode;
	renderAreaMeta?: (area: TArea) => ReactNode;
	renderAreaActions: (
		area: TArea,
		actions: CatalogMoveActions,
		actionTriggerRef: ActionTriggerRef,
	) => ReactNode;
	renderEntryContent: (entry: TEntry) => ReactNode;
	renderEntryActions: (
		entry: TEntry,
		actions: CatalogEntryMoveActions,
		actionTriggerRef: ActionTriggerRef,
	) => ReactNode;
	renderEntryPreview?: (entry: TEntry) => ReactNode;
	getEmptyLabel: (areaSlug: string | null, total: number) => ReactNode;
	unassignedLabel?: string;
}

type CatalogDndData =
	| { type: "area"; areaSlug: string; label: string }
	| { type: "bucket"; areaSlug: string | null; label: string }
	| { type: "entry"; areaSlug: string | null; entrySlug: string; label: string };

interface ActiveDrag {
	type: "area" | "entry";
	slug: string;
}

interface ActiveEntryDrop extends CatalogDropTarget {
	overType: CatalogDndData["type"];
}

/** dnd-kit types `data.current` as an open record, so the payload is checked rather than asserted. */
function isCatalogDndData(data: unknown): data is CatalogDndData {
	if (typeof data !== "object" || data === null || !("type" in data)) return false;
	return data.type === "area" || data.type === "bucket" || data.type === "entry";
}

const catalogDndData = (data: unknown): CatalogDndData | undefined =>
	isCatalogDndData(data) ? data : undefined;

const areaDndId = (slug: string) => `area:${slug}`;
const bucketDndId = (areaSlug: string | null) => `bucket:${areaSlug ?? UNASSIGNED_CATALOG_BUCKET}`;
const entryDndId = (slug: string) => `entry:${slug}`;

function byDisplayOrder<T extends { displayOrder: number; name: string }>(a: T, b: T) {
	return a.displayOrder - b.displayOrder || a.name.localeCompare(b.name);
}

export function SortableCatalogTree<
	TArea extends SortableCatalogArea,
	TEntry extends SortableCatalogEntry,
>({
	areas,
	entries,
	visibleEntrySlugs,
	forceOpenAreaSlugs,
	areaReorderDisabled,
	disabledAreaSlugs,
	disabledEntrySlugs,
	blockedEntryOrderBuckets,
	blockedMoveDestinationSlugs,
	showEntryReorderHandles,
	onReorderAreas,
	onPlaceEntry,
	renderAreaLeading,
	renderAreaMeta,
	renderAreaActions,
	renderEntryContent,
	renderEntryActions,
	renderEntryPreview,
	getEmptyLabel,
	unassignedLabel = "Unassigned",
}: SortableCatalogTreeProps<TArea, TEntry>) {
	const [activeDrag, setActiveDrag] = useState<ActiveDrag | null>(null);
	const [dropTarget, setDropTarget] = useState<ActiveEntryDrop | null>(null);
	const [collapsedAreas, setCollapsedAreas] = useState<readonly string[]>([]);
	const focusAfterMove = useRef<string | null>(null);
	const sortedAreas = [...areas].sort(byDisplayOrder);
	const visibleEntries = visibleEntrySlugs
		? entries.filter((entry) => visibleEntrySlugs.has(entry.slug))
		: [...entries];
	const byArea = new Map<string, TEntry[]>();
	for (const entry of visibleEntries) {
		const key = entry.areaSlug ?? UNASSIGNED_CATALOG_BUCKET;
		const bucket = byArea.get(key);
		if (bucket) bucket.push(entry);
		else byArea.set(key, [entry]);
	}
	const totalByArea = new Map<string, number>();
	for (const entry of entries) {
		const key = entry.areaSlug ?? UNASSIGNED_CATALOG_BUCKET;
		totalByArea.set(key, (totalByArea.get(key) ?? 0) + 1);
	}

	const sensors = useSensors(
		useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
		useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
	);

	const collisionDetection: CollisionDetection = (args) => {
		const activeType = catalogDndData(args.active.data.current)?.type;
		const droppableContainers = args.droppableContainers.filter(({ data }) => {
			const target = catalogDndData(data.current);
			if (
				activeType === "entry" &&
				target &&
				blockedMoveDestinationSlugs.has(target.areaSlug ?? UNASSIGNED_CATALOG_BUCKET)
			) {
				return false;
			}
			return activeType === "area"
				? target?.type === "area"
				: target?.type === "entry" || target?.type === "bucket" || target?.type === "area";
		});
		if (activeType === "entry" && args.pointerCoordinates) {
			const collisions = pointerWithin({ ...args, droppableContainers });
			for (const type of ["entry", "bucket", "area"] as const) {
				const collision = collisions.find(
					({ id }) =>
						catalogDndData(
							droppableContainers.find((container) => container.id === id)?.data.current,
						)?.type === type,
				);
				if (collision) return [collision];
			}
			return [];
		}
		return closestCenter({ ...args, droppableContainers });
	};

	const resolveEntryDropTarget = ({
		active,
		over,
	}: Pick<DragOverEvent, "active" | "over">): CatalogDropTarget | null => {
		const activeData = catalogDndData(active.data.current);
		const overData = catalogDndData(over?.data.current);
		if (activeData?.type !== "entry" || !over || !overData) return null;
		const areaSlug = overData.areaSlug;
		if (blockedMoveDestinationSlugs.has(areaSlug ?? UNASSIGNED_CATALOG_BUCKET)) return null;
		if (overData.type !== "entry") {
			return getCatalogDropTarget(entries, activeData.entrySlug, areaSlug);
		}
		const translated = active.rect.current.translated;
		const afterAnchor = translated
			? translated.top + translated.height / 2 > over.rect.top + over.rect.height / 2
			: false;
		return getCatalogDropTarget(
			entries,
			activeData.entrySlug,
			areaSlug,
			overData.entrySlug,
			afterAnchor,
		);
	};

	const resetDrag = () => {
		setActiveDrag(null);
		setDropTarget(null);
	};

	const handleDragStart = ({ active }: DragStartEvent) => {
		const data = catalogDndData(active.data.current);
		if (data?.type === "area") setActiveDrag({ type: "area", slug: data.areaSlug });
		if (data?.type === "entry") setActiveDrag({ type: "entry", slug: data.entrySlug });
	};

	const handleDragOver = (event: DragOverEvent) => {
		const target = resolveEntryDropTarget(event);
		const overData = catalogDndData(event.over?.data.current);
		setDropTarget(target && overData ? { ...target, overType: overData.type } : null);
	};

	const handleDragEnd = (event: DragEndEvent) => {
		const activeData = catalogDndData(event.active.data.current);
		const overData = catalogDndData(event.over?.data.current);
		const target = resolveEntryDropTarget(event);
		resetDrag();
		if (!event.over || !activeData || !overData) return;
		if (activeData.type === "area" && overData.type === "area") {
			const ids = sortedAreas.map((area) => area.slug);
			const from = ids.indexOf(activeData.areaSlug);
			const to = ids.indexOf(overData.areaSlug);
			if (from !== to) onReorderAreas(arrayMove(ids, from, to));
			return;
		}
		if (activeData.type !== "entry" || !target) return;
		const currentAreaSlug = activeData.areaSlug;
		const currentPosition = entries
			.filter((entry) => (entry.areaSlug ?? null) === currentAreaSlug)
			.sort(byDisplayOrder)
			.findIndex((entry) => entry.slug === activeData.entrySlug);
		if (currentAreaSlug !== target.areaSlug || currentPosition !== target.position) {
			onPlaceEntry(activeData.entrySlug, target.areaSlug, target.position);
		}
	};

	const areaName = (slug: string | null) =>
		slug === null ? unassignedLabel : (areas.find((area) => area.slug === slug)?.name ?? "area");
	const describeTarget = (target: CatalogDropTarget, movingSlug: string) => {
		const count = entries.filter(
			(entry) => entry.slug !== movingSlug && (entry.areaSlug ?? null) === target.areaSlug,
		).length;
		return `position ${target.position + 1} of ${count + 1} in ${areaName(target.areaSlug)}`;
	};
	const announcements = {
		onDragStart: ({ active }: Pick<DragStartEvent, "active">) => {
			const data = catalogDndData(active.data.current);
			return data ? `Picked up ${data.label}.` : undefined;
		},
		onDragOver: ({ active, over }: Pick<DragOverEvent, "active" | "over">) => {
			const data = catalogDndData(active.data.current);
			const overData = catalogDndData(over?.data.current);
			if (data?.type === "area") {
				return overData?.type === "area"
					? `Moving ${data.label}, position ${sortedAreas.findIndex(({ slug }) => slug === overData.areaSlug) + 1} of ${sortedAreas.length}.`
					: undefined;
			}
			if (data?.type !== "entry") return undefined;
			const target = resolveEntryDropTarget({ active, over });
			return target
				? `Moving ${data.label}, ${describeTarget(target, data.entrySlug)}.`
				: undefined;
		},
		onDragEnd: ({ active, over }: Pick<DragEndEvent, "active" | "over">) => {
			const data = catalogDndData(active.data.current);
			if (data?.type === "entry") {
				const target = resolveEntryDropTarget({ active, over });
				return target
					? `Moved ${data.label} to ${describeTarget(target, data.entrySlug)}.`
					: "Move cancelled.";
			}
			const overData = catalogDndData(over?.data.current);
			return data && overData?.type === "area"
				? `Moved ${data.label} to position ${sortedAreas.findIndex(({ slug }) => slug === overData.areaSlug) + 1} of ${sortedAreas.length}.`
				: "Move cancelled.";
		},
		onDragCancel: () => "Move cancelled.",
	};

	const registerActionTrigger = (key: string) => (node: HTMLButtonElement | null) => {
		if (node && !node.disabled && focusAfterMove.current === key) {
			node.focus();
			if (document.activeElement === node) focusAfterMove.current = null;
		}
	};
	const prepareFocus = (key: string) => {
		focusAfterMove.current = key;
	};
	const areaMoveActions = (area: TArea): CatalogMoveActions => {
		const focusKey = areaDndId(area.slug);
		const index = sortedAreas.findIndex((candidate) => candidate.slug === area.slug);
		const disabled = areaReorderDisabled || disabledAreaSlugs.has(area.slug);
		const move = (offset: number) => {
			if (disabled) return;
			const target = index + offset;
			if (target < 0 || target >= sortedAreas.length) return;
			prepareFocus(focusKey);
			onReorderAreas(
				arrayMove(
					sortedAreas.map(({ slug }) => slug),
					index,
					target,
				),
			);
		};
		return {
			canMoveUp: !disabled && index > 0,
			canMoveDown: !disabled && index < sortedAreas.length - 1,
			moveUp: () => move(-1),
			moveDown: () => move(1),
		};
	};
	const entryMoveActions = (entry: TEntry): CatalogEntryMoveActions => {
		const focusKey = entryDndId(entry.slug);
		const currentAreaSlug = entry.moveSourceAreaSlug ?? entry.areaSlug ?? null;
		const bucketKey = currentAreaSlug ?? UNASSIGNED_CATALOG_BUCKET;
		const ordered = entries
			.filter((candidate) => (candidate.areaSlug ?? null) === currentAreaSlug)
			.sort(byDisplayOrder);
		const index = ordered.findIndex((candidate) => candidate.slug === entry.slug);
		const movesDisabled = disabledEntrySlugs.has(entry.slug) || !showEntryReorderHandles;
		const reorderDisabled = movesDisabled || blockedEntryOrderBuckets.has(bucketKey);
		const moveToPosition = (position: number) => {
			if (reorderDisabled || position < 0 || position >= ordered.length) return;
			prepareFocus(focusKey);
			onPlaceEntry(entry.slug, currentAreaSlug, position);
		};
		return {
			canMoveUp: !reorderDisabled && index > 0,
			canMoveDown: !reorderDisabled && index >= 0 && index < ordered.length - 1,
			moveUp: () => moveToPosition(index - 1),
			moveDown: () => moveToPosition(index + 1),
			currentAreaSlug,
			canMoveTo: (areaSlug) =>
				areaSlug !== currentAreaSlug &&
				!movesDisabled &&
				!blockedMoveDestinationSlugs.has(areaSlug ?? UNASSIGNED_CATALOG_BUCKET),
			moveTo: (areaSlug) => {
				if (
					areaSlug === currentAreaSlug ||
					movesDisabled ||
					blockedMoveDestinationSlugs.has(areaSlug ?? UNASSIGNED_CATALOG_BUCKET)
				) {
					return;
				}
				const target = getCatalogDropTarget(entries, entry.slug, areaSlug);
				if (!target) return;
				if (target.areaSlug) {
					setCollapsedAreas((collapsed) => collapsed.filter((slug) => slug !== target.areaSlug));
				}
				prepareFocus(focusKey);
				onPlaceEntry(entry.slug, target.areaSlug, target.position);
			},
		};
	};

	const draggedEntry =
		activeDrag?.type === "entry"
			? entries.find((entry) => entry.slug === activeDrag.slug)
			: undefined;

	return (
		<DndContext
			sensors={sensors}
			collisionDetection={collisionDetection}
			modifiers={[restrictToVerticalAxis]}
			accessibility={{
				announcements,
				screenReaderInstructions: {
					draggable:
						"Press space to pick up an item. Use the arrow keys to move it, then press space to drop or escape to cancel. You can also use the item's actions menu to move it without dragging.",
				},
			}}
			onDragStart={handleDragStart}
			onDragOver={handleDragOver}
			onDragEnd={handleDragEnd}
			onDragCancel={resetDrag}
		>
			<SortableContext
				items={sortedAreas.map((area) => areaDndId(area.slug))}
				strategy={verticalListSortingStrategy}
			>
				<Accordion
					className="space-y-2"
					multiple
					value={sortedAreas
						.map((area) => area.slug)
						.filter(
							(slug) => (forceOpenAreaSlugs?.has(slug) ?? false) || !collapsedAreas.includes(slug),
						)}
					onValueChange={(open) => {
						// Only the areas currently rendered are re-derived. Recomputing the whole set from
						// `sortedAreas` drops every area a filter is hiding, so collapsing one row while a
						// search is active would silently expand everything the search hid.
						const rendered = new Set(sortedAreas.map((area) => area.slug));
						setCollapsedAreas([
							...collapsedAreas.filter((slug) => !rendered.has(slug)),
							...sortedAreas.map((area) => area.slug).filter((slug) => !open.includes(slug)),
						]);
					}}
				>
					{sortedAreas.map((area) => (
						<SortableAreaSection
							key={area.slug}
							area={area}
							entries={(byArea.get(area.slug) ?? []).sort(byDisplayOrder)}
							total={totalByArea.get(area.slug) ?? 0}
							collapseDisabled={forceOpenAreaSlugs?.has(area.slug) ?? false}
							reorderDisabled={areaReorderDisabled || disabledAreaSlugs.has(area.slug)}
							entryReorderDisabled={blockedEntryOrderBuckets.has(area.slug)}
							blockedMoveDestinationSlugs={blockedMoveDestinationSlugs}
							disabledEntrySlugs={disabledEntrySlugs}
							dropTarget={dropTarget}
							showEntryReorderHandles={showEntryReorderHandles}
							renderLeading={renderAreaLeading}
							renderMeta={renderAreaMeta}
							renderActions={(candidate) =>
								renderAreaActions(
									candidate,
									areaMoveActions(candidate),
									registerActionTrigger(areaDndId(candidate.slug)),
								)
							}
							renderEntryContent={renderEntryContent}
							renderEntryActions={(candidate) =>
								renderEntryActions(
									candidate,
									entryMoveActions(candidate),
									registerActionTrigger(entryDndId(candidate.slug)),
								)
							}
							emptyLabel={getEmptyLabel(area.slug, totalByArea.get(area.slug) ?? 0)}
						/>
					))}
				</Accordion>
			</SortableContext>

			{(entries.length > 0 || sortedAreas.length > 0) && (
				<div className="rounded-lg border border-dashed">
					<div className="flex items-center gap-2 border-b border-dashed px-3 py-2">
						<span className="font-semibold text-muted-foreground text-sm">{unassignedLabel}</span>
						<Badge variant="secondary">{totalByArea.get(UNASSIGNED_CATALOG_BUCKET) ?? 0}</Badge>
					</div>
					<div className="px-2 py-1">
						<EntryBucket
							areaSlug={null}
							areaName={unassignedLabel}
							entries={(byArea.get(UNASSIGNED_CATALOG_BUCKET) ?? []).sort(byDisplayOrder)}
							reorderDisabled={blockedEntryOrderBuckets.has(UNASSIGNED_CATALOG_BUCKET)}
							blockedMoveDestinationSlugs={blockedMoveDestinationSlugs}
							disabledEntrySlugs={disabledEntrySlugs}
							dropTarget={dropTarget}
							showEntryReorderHandles={showEntryReorderHandles}
							renderContent={renderEntryContent}
							renderActions={(entry) =>
								renderEntryActions(
									entry,
									entryMoveActions(entry),
									registerActionTrigger(entryDndId(entry.slug)),
								)
							}
							emptyLabel={getEmptyLabel(null, totalByArea.get(UNASSIGNED_CATALOG_BUCKET) ?? 0)}
						/>
					</div>
				</div>
			)}

			<DragOverlay adjustScale={false}>
				{draggedEntry && renderEntryPreview ? renderEntryPreview(draggedEntry) : null}
			</DragOverlay>
		</DndContext>
	);
}

interface SortableAreaSectionProps<
	TArea extends SortableCatalogArea,
	TEntry extends SortableCatalogEntry,
> {
	area: TArea;
	entries: TEntry[];
	total: number;
	collapseDisabled: boolean;
	reorderDisabled: boolean;
	entryReorderDisabled: boolean;
	blockedMoveDestinationSlugs: ReadonlySet<string>;
	disabledEntrySlugs: ReadonlySet<string>;
	dropTarget: ActiveEntryDrop | null;
	showEntryReorderHandles: boolean;
	renderLeading?: (area: TArea) => ReactNode;
	renderMeta?: (area: TArea) => ReactNode;
	renderActions: (area: TArea) => ReactNode;
	renderEntryContent: (entry: TEntry) => ReactNode;
	renderEntryActions: (entry: TEntry) => ReactNode;
	emptyLabel: ReactNode;
}

function SortableAreaSection<
	TArea extends SortableCatalogArea,
	TEntry extends SortableCatalogEntry,
>({
	area,
	entries,
	total,
	collapseDisabled,
	reorderDisabled,
	entryReorderDisabled,
	blockedMoveDestinationSlugs,
	disabledEntrySlugs,
	dropTarget,
	showEntryReorderHandles,
	renderLeading,
	renderMeta,
	renderActions,
	renderEntryContent,
	renderEntryActions,
	emptyLabel,
}: SortableAreaSectionProps<TArea, TEntry>) {
	const {
		active,
		attributes,
		listeners,
		setActivatorNodeRef,
		setDraggableNodeRef,
		setDroppableNodeRef,
		transform,
		transition,
		isDragging,
		isOver,
	} = useSortable({
		id: areaDndId(area.slug),
		data: {
			type: "area",
			areaSlug: area.slug,
			label: `${area.name} area`,
		} satisfies CatalogDndData,
		disabled: reorderDisabled,
	});
	return (
		<AccordionItem
			ref={setDraggableNodeRef}
			value={area.slug}
			style={{ transform: CSS.Transform.toString(transform), transition }}
			className={cn("rounded-lg border bg-card", isDragging && "z-10 opacity-40")}
		>
			<div
				ref={setDroppableNodeRef}
				className={cn(
					"flex items-center gap-2 rounded-lg px-2 transition-colors",
					isOver && active?.data.current?.type === "entry" && "bg-accent/70",
				)}
			>
				<Button
					ref={setActivatorNodeRef}
					type="button"
					variant="ghost"
					size="icon"
					className="touch-none shrink-0 cursor-grab text-muted-foreground active:cursor-grabbing disabled:cursor-default"
					aria-label={`Reorder ${area.name}`}
					disabled={reorderDisabled}
					{...attributes}
					{...listeners}
				>
					<GripVertical className="size-4" />
				</Button>
				{renderLeading?.(area)}
				<AccordionTrigger
					disabled={collapseDisabled}
					className="py-2.5 hover:no-underline disabled:opacity-100"
				>
					<span className="flex min-w-0 flex-wrap items-center gap-2">
						<span className="min-w-0 break-words font-medium">{area.name}</span>
						<Badge variant="secondary" className="shrink-0">
							{total}
						</Badge>
						{renderMeta?.(area)}
					</span>
				</AccordionTrigger>
				<div className="ml-auto flex items-center gap-2">{renderActions(area)}</div>
			</div>
			<AccordionContent className="px-2 pb-2 sm:pl-9">
				<EntryBucket
					areaSlug={area.slug}
					areaName={area.name}
					entries={entries}
					reorderDisabled={entryReorderDisabled}
					blockedMoveDestinationSlugs={blockedMoveDestinationSlugs}
					disabledEntrySlugs={disabledEntrySlugs}
					dropTarget={dropTarget}
					showEntryReorderHandles={showEntryReorderHandles}
					renderContent={renderEntryContent}
					renderActions={renderEntryActions}
					emptyLabel={emptyLabel}
				/>
			</AccordionContent>
		</AccordionItem>
	);
}

interface EntryBucketProps<TEntry extends SortableCatalogEntry> {
	areaSlug: string | null;
	areaName: string;
	entries: TEntry[];
	reorderDisabled: boolean;
	blockedMoveDestinationSlugs: ReadonlySet<string>;
	disabledEntrySlugs: ReadonlySet<string>;
	dropTarget: ActiveEntryDrop | null;
	showEntryReorderHandles: boolean;
	renderContent: (entry: TEntry) => ReactNode;
	renderActions: (entry: TEntry) => ReactNode;
	emptyLabel: ReactNode;
}

function EntryBucket<TEntry extends SortableCatalogEntry>({
	areaSlug,
	areaName,
	entries,
	reorderDisabled,
	blockedMoveDestinationSlugs,
	disabledEntrySlugs,
	dropTarget,
	showEntryReorderHandles,
	renderContent,
	renderActions,
	emptyLabel,
}: EntryBucketProps<TEntry>) {
	const { active, isOver, setNodeRef } = useDroppable({
		id: bucketDndId(areaSlug),
		data: { type: "bucket", areaSlug, label: areaName } satisfies CatalogDndData,
		disabled:
			reorderDisabled || blockedMoveDestinationSlugs.has(areaSlug ?? UNASSIGNED_CATALOG_BUCKET),
	});
	const activeData = catalogDndData(active?.data.current);
	const isEntryDrag = activeData?.type === "entry";
	const activeEntrySlug = activeData?.type === "entry" ? activeData.entrySlug : undefined;
	const destinationEntries = entries.filter((entry) => entry.slug !== activeEntrySlug);
	const destinationPositionBySlug = new Map(
		destinationEntries.map((entry, position) => [entry.slug, position]),
	);
	const isTargeted = dropTarget?.areaSlug === areaSlug && dropTarget.overType !== "area";
	return (
		<div
			ref={setNodeRef}
			className={cn(
				"relative min-h-12 rounded-md border border-transparent transition-[background-color,border-color,box-shadow]",
				isEntryDrag && "border-dashed border-border bg-muted/20",
				isTargeted && isEntryDrag && "border-solid border-primary/60 bg-accent/50 shadow-sm",
			)}
		>
			<SortableContext
				items={entries.map((entry) => entryDndId(entry.slug))}
				strategy={verticalListSortingStrategy}
			>
				<ItemGroup className="gap-0.5">
					{entries.map((entry) => (
						<div key={entry.slug} role="presentation" className="relative">
							<DropIndicator
								visible={
									isTargeted && dropTarget.position === destinationPositionBySlug.get(entry.slug)
								}
							/>
							<SortableEntryRow
								entry={entry}
								showReorderHandle={showEntryReorderHandles}
								reorderDisabled={reorderDisabled || disabledEntrySlugs.has(entry.slug)}
								renderContent={renderContent}
								renderActions={renderActions}
							/>
						</div>
					))}
				</ItemGroup>
				<DropIndicator
					visible={isTargeted && dropTarget.position === destinationEntries.length}
					atEnd
				/>
			</SortableContext>
			{entries.length === 0 && (
				<p className="flex min-h-12 items-center px-2 py-3 text-muted-foreground text-sm">
					{isOver && isEntryDrag ? "Release to move here." : emptyLabel}
				</p>
			)}
		</div>
	);
}

function SortableEntryRow<TEntry extends SortableCatalogEntry>({
	entry,
	showReorderHandle,
	reorderDisabled,
	renderContent,
	renderActions,
}: {
	entry: TEntry;
	showReorderHandle: boolean;
	reorderDisabled: boolean;
	renderContent: (entry: TEntry) => ReactNode;
	renderActions: (entry: TEntry) => ReactNode;
}) {
	const {
		attributes,
		listeners,
		setActivatorNodeRef,
		setNodeRef,
		transform,
		transition,
		isDragging,
	} = useSortable({
		id: entryDndId(entry.slug),
		data: {
			type: "entry",
			areaSlug: entry.moveSourceAreaSlug ?? entry.areaSlug ?? null,
			entrySlug: entry.slug,
			label: entry.name,
		} satisfies CatalogDndData,
		disabled: !showReorderHandle || reorderDisabled,
	});
	return (
		<Item
			ref={setNodeRef}
			role="listitem"
			style={{ transform: CSS.Transform.toString(transform), transition }}
			size="xs"
			className={cn("flex-nowrap hover:bg-muted/60", isDragging && "opacity-30")}
		>
			{showReorderHandle && (
				<Button
					ref={setActivatorNodeRef}
					type="button"
					variant="ghost"
					size="icon-sm"
					className="touch-none shrink-0 cursor-grab text-muted-foreground active:cursor-grabbing disabled:cursor-default"
					aria-label={`Reorder ${entry.name}`}
					disabled={reorderDisabled}
					{...attributes}
					{...listeners}
				>
					<GripVertical className="size-4" />
				</Button>
			)}
			{renderContent(entry)}
			<ItemActions className="ml-auto">{renderActions(entry)}</ItemActions>
		</Item>
	);
}

function DropIndicator({ visible, atEnd = false }: { visible: boolean; atEnd?: boolean }) {
	if (!visible) return null;
	return (
		<div
			aria-hidden="true"
			className={cn(
				"pointer-events-none absolute right-2 left-2 z-20 h-0.5 rounded-full bg-primary",
				atEnd ? "bottom-0" : "-top-px",
			)}
		>
			<span className="absolute top-1/2 -left-1 size-2 -translate-y-1/2 rounded-full bg-primary ring-2 ring-background" />
		</div>
	);
}

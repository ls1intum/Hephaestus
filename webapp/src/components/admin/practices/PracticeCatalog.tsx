import {
	type CollisionDetection,
	closestCenter,
	DndContext,
	type DragCancelEvent,
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
import { Link } from "@tanstack/react-router";
import { GripVertical, MoreHorizontal, Plus } from "lucide-react";
import { type ReactNode, useState } from "react";
import type { Practice, PracticeArea } from "@/api/types.gen";
import { CatalogOriginBadge } from "@/components/admin/practices/CatalogOriginBadge";
import {
	Accordion,
	AccordionContent,
	AccordionItem,
	AccordionTrigger,
} from "@/components/ui/accordion";
import { Badge } from "@/components/ui/badge";
import { Button, buttonVariants } from "@/components/ui/button";
import {
	Dialog,
	DialogContent,
	DialogFooter,
	DialogHeader,
	DialogTitle,
} from "@/components/ui/dialog";
import {
	DropdownMenu,
	DropdownMenuContent,
	DropdownMenuGroup,
	DropdownMenuItem,
	DropdownMenuLabel,
	DropdownMenuRadioGroup,
	DropdownMenuRadioItem,
	DropdownMenuSeparator,
	DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty";
import { Input } from "@/components/ui/input";
import {
	Item,
	ItemActions,
	ItemContent,
	ItemDescription,
	ItemGroup,
	ItemTitle,
} from "@/components/ui/item";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import {
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { cn } from "@/lib/utils";
import { AreaVisualPicker } from "./AreaVisualPicker";
import { getPracticeDropTarget, type PracticeDropTarget } from "./practice-catalog-dnd";

export type FocusFilter = "ALL" | Practice["artifactType"];

export interface PracticeCatalogPendingState {
	areaSlugs: ReadonlySet<string>;
	practiceSlugs: ReadonlySet<string>;
	areaStructure: boolean;
	blockedMoveDestinationSlugs: ReadonlySet<string>;
	blockedPracticeOrderBuckets: ReadonlySet<string>;
	creatingArea: boolean;
}

export interface PracticeCatalogProps {
	workspaceSlug: string;
	areas: PracticeArea[];
	practices: Practice[];
	pending: PracticeCatalogPendingState;
	focusFilter: FocusFilter;
	onFocusFilterChange: (f: FocusFilter) => void;
	onCreateArea: (name: string) => Promise<boolean>;
	onRenameArea: (slug: string, name: string) => Promise<boolean>;
	onToggleAreaActive: (slug: string, active: boolean) => void;
	onDeleteArea: (slug: string) => void;
	onReorderAreas: (orderedSlugs: string[]) => void;
	onSetAreaVisual: (slug: string, patch: { icon?: string; color?: string }) => void;
	onSetPracticeActive: (slug: string, active: boolean) => void;
	onDeletePractice: (practice: Practice) => void;
	onPlacePractice: (practiceSlug: string, areaSlug: string | null, position: number) => void;
}

const UNASSIGNED = "__unassigned__";
const areaDndId = (slug: string) => `area:${slug}`;
const bucketDndId = (areaSlug: string | null) => `bucket:${areaSlug ?? UNASSIGNED}`;
const practiceDndId = (slug: string) => `practice:${slug}`;

type CatalogDndData =
	| { type: "area"; areaSlug: string; label: string }
	| { type: "bucket"; areaSlug: string | null; label: string }
	| { type: "practice"; areaSlug: string | null; label: string; practiceSlug: string };

interface ActiveDrag {
	type: "area" | "practice";
	slug: string;
}

interface ActivePracticeDrop extends PracticeDropTarget {
	overType: CatalogDndData["type"];
}

const FOCUS_FILTERS = [
	{ value: "ALL", label: "All work types" },
	{ value: "PULL_REQUEST", label: "Pull or merge requests" },
	{ value: "ISSUE", label: "Issues" },
	{ value: "CONVERSATION_THREAD", label: "Conversations" },
] satisfies Array<{ value: FocusFilter; label: string }>;

const ARTIFACT_LABELS: Record<Practice["artifactType"], string> = {
	PULL_REQUEST: "Pull or merge request",
	ISSUE: "Issue",
	CONVERSATION_THREAD: "Conversation",
};

export function PracticeCatalog({
	workspaceSlug,
	areas,
	practices,
	pending,
	focusFilter,
	onFocusFilterChange,
	onCreateArea,
	onRenameArea,
	onToggleAreaActive,
	onDeleteArea,
	onReorderAreas,
	onSetAreaVisual,
	onSetPracticeActive,
	onDeletePractice,
	onPlacePractice,
}: PracticeCatalogProps) {
	const [renamingArea, setRenamingArea] = useState<PracticeArea | null>(null);
	const [activeDrag, setActiveDrag] = useState<ActiveDrag | null>(null);
	const [dropTarget, setDropTarget] = useState<ActivePracticeDrop | null>(null);
	const sortedAreas = [...areas].sort(
		(a, b) => a.displayOrder - b.displayOrder || a.name.localeCompare(b.name),
	);
	const visible =
		focusFilter === "ALL" ? practices : practices.filter((p) => p.artifactType === focusFilter);
	const byArea = new Map<string, Practice[]>();
	for (const p of visible) {
		const key = p.areaSlug ?? UNASSIGNED;
		const list = byArea.get(key);
		if (list) list.push(p);
		else byArea.set(key, [p]);
	}
	const unassigned = byArea.get(UNASSIGNED) ?? [];
	const totalUnassignedCount = practices.filter((practice) => practice.areaSlug == null).length;
	const showPracticeHandles = focusFilter === "ALL";

	const sensors = useSensors(
		useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
		useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
	);

	const collisionDetection: CollisionDetection = (args) => {
		const activeType = args.active.data.current?.type;
		const droppableContainers = args.droppableContainers.filter(({ data }) => {
			const target = data.current as CatalogDndData | undefined;
			const targetType = target?.type;
			if (
				activeType === "practice" &&
				target &&
				pending.blockedMoveDestinationSlugs.has(target.areaSlug ?? UNASSIGNED)
			) {
				return false;
			}
			return activeType === "area"
				? targetType === "area"
				: targetType === "practice" || targetType === "bucket" || targetType === "area";
		});
		if (activeType === "practice" && args.pointerCoordinates) {
			const collisions = pointerWithin({ ...args, droppableContainers });
			for (const type of ["practice", "bucket", "area"] as const) {
				const collision = collisions.find(
					({ id }) =>
						(
							droppableContainers.find((container) => container.id === id)?.data.current as
								| CatalogDndData
								| undefined
						)?.type === type,
				);
				if (collision) return [collision];
			}
			return [];
		}
		return closestCenter({ ...args, droppableContainers });
	};

	const resolvePracticeDropTarget = ({
		active,
		over,
	}: Pick<DragOverEvent, "active" | "over">): PracticeDropTarget | null => {
		const activeData = active.data.current as CatalogDndData | undefined;
		const overData = over?.data.current as CatalogDndData | undefined;
		if (activeData?.type !== "practice" || !over || !overData) return null;
		const areaSlug = overData.areaSlug;
		if (pending.blockedMoveDestinationSlugs.has(areaSlug ?? UNASSIGNED)) return null;
		if (overData.type !== "practice") {
			return getPracticeDropTarget(practices, activeData.practiceSlug, areaSlug);
		}
		const translated = active.rect.current.translated;
		const afterAnchor = translated
			? translated.top + translated.height / 2 > over.rect.top + over.rect.height / 2
			: false;
		return getPracticeDropTarget(
			practices,
			activeData.practiceSlug,
			areaSlug,
			overData.practiceSlug,
			afterAnchor,
		);
	};

	const handleDragStart = ({ active }: DragStartEvent) => {
		const data = active.data.current as CatalogDndData | undefined;
		if (data?.type === "area") setActiveDrag({ type: "area", slug: data.areaSlug });
		if (data?.type === "practice") setActiveDrag({ type: "practice", slug: data.practiceSlug });
	};

	const handleDragOver = (event: DragOverEvent) => {
		const target = resolvePracticeDropTarget(event);
		const overData = event.over?.data.current as CatalogDndData | undefined;
		setDropTarget(target && overData ? { ...target, overType: overData.type } : null);
	};

	const resetDrag = () => {
		setActiveDrag(null);
		setDropTarget(null);
	};

	const handleDragCancel = (_event: DragCancelEvent) => resetDrag();

	const handleDragEnd = (event: DragEndEvent) => {
		const activeData = event.active.data.current as CatalogDndData | undefined;
		const overData = event.over?.data.current as CatalogDndData | undefined;
		const target = resolvePracticeDropTarget(event);
		resetDrag();
		if (!event.over || !activeData || !overData) return;
		if (activeData.type === "area" && overData.type === "area") {
			const ids = sortedAreas.map((area) => area.slug);
			const from = ids.indexOf(activeData.areaSlug);
			const to = ids.indexOf(overData.areaSlug);
			if (from !== to) onReorderAreas(arrayMove(ids, from, to));
			return;
		}
		if (activeData.type !== "practice" || !target) return;
		const currentAreaSlug = activeData.areaSlug;
		const currentPosition = practices
			.filter((practice) => (practice.areaSlug ?? null) === currentAreaSlug)
			.sort((a, b) => a.displayOrder - b.displayOrder || a.name.localeCompare(b.name))
			.findIndex((practice) => practice.slug === activeData.practiceSlug);
		if (currentAreaSlug === target.areaSlug && currentPosition === target.position) return;
		onPlacePractice(activeData.practiceSlug, target.areaSlug, target.position);
	};

	const announcements = {
		onDragStart: ({ active }: Pick<DragStartEvent, "active">) => {
			const data = active.data.current as CatalogDndData | undefined;
			return data ? `Picked up ${data.label}.` : undefined;
		},
		onDragOver: ({ over }: Pick<DragOverEvent, "active" | "over">) => {
			const data = over?.data.current as CatalogDndData | undefined;
			return data ? `Over ${data.label}.` : undefined;
		},
		onDragEnd: ({ active, over }: Pick<DragEndEvent, "active" | "over">) => {
			const activeData = active.data.current as CatalogDndData | undefined;
			const overData = over?.data.current as CatalogDndData | undefined;
			return activeData && overData
				? `Placed ${activeData.label} at ${overData.label}.`
				: "Move cancelled.";
		},
		onDragCancel: () => "Move cancelled.",
	};
	const draggedPractice =
		activeDrag?.type === "practice"
			? practices.find((practice) => practice.slug === activeDrag.slug)
			: undefined;

	return (
		<div className="space-y-4">
			<div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
				<Select
					items={FOCUS_FILTERS}
					value={focusFilter}
					onValueChange={(value) => value && onFocusFilterChange(value as FocusFilter)}
				>
					<SelectTrigger className="w-full sm:hidden" aria-label="Filter by work type">
						<SelectValue />
					</SelectTrigger>
					<SelectContent>
						{FOCUS_FILTERS.map((filter) => (
							<SelectItem key={filter.value} value={filter.value}>
								{filter.label}
							</SelectItem>
						))}
					</SelectContent>
				</Select>
				<ToggleGroup
					role="toolbar"
					value={[focusFilter]}
					onValueChange={(v) => v[0] && onFocusFilterChange(v[0] as FocusFilter)}
					variant="outline"
					size="sm"
					aria-label="Filter by work type"
					className="hidden sm:flex"
				>
					<ToggleGroupItem value="ALL" className="min-w-0">
						All
					</ToggleGroupItem>
					<ToggleGroupItem
						value="PULL_REQUEST"
						className="h-auto min-h-7 min-w-0 whitespace-normal py-1 sm:whitespace-nowrap"
					>
						Pull or merge requests
					</ToggleGroupItem>
					<ToggleGroupItem value="ISSUE" className="min-w-0">
						Issues
					</ToggleGroupItem>
					<ToggleGroupItem value="CONVERSATION_THREAD" className="min-w-0">
						Conversations
					</ToggleGroupItem>
				</ToggleGroup>
				<div className="grid grid-cols-2 gap-2 sm:flex">
					<AddAreaButton
						onCreate={onCreateArea}
						pending={pending.creatingArea}
						disabled={pending.areaStructure && !pending.creatingArea}
					/>
					<Link
						to="/w/$workspaceSlug/admin/practices/new"
						params={{ workspaceSlug }}
						className={cn(buttonVariants(), "w-full sm:w-auto")}
					>
						<Plus className="mr-1.5 size-4" />
						New practice
					</Link>
				</div>
			</div>
			{focusFilter !== "ALL" && (
				<p className="text-sm text-muted-foreground">Clear the filter to drag practices.</p>
			)}

			<DndContext
				sensors={sensors}
				collisionDetection={collisionDetection}
				modifiers={[restrictToVerticalAxis]}
				accessibility={{
					announcements,
					screenReaderInstructions: {
						draggable:
							"Press space to pick up an item. Use the arrow keys to move it, then press space to drop or escape to cancel.",
					},
				}}
				onDragStart={handleDragStart}
				onDragOver={handleDragOver}
				onDragEnd={handleDragEnd}
				onDragCancel={handleDragCancel}
			>
				<SortableContext
					items={sortedAreas.map((area) => areaDndId(area.slug))}
					strategy={verticalListSortingStrategy}
				>
					<Accordion
						className="space-y-2"
						multiple
						defaultValue={sortedAreas.map((area) => area.slug)}
					>
						{sortedAreas.map((area) => (
							<SortableArea
								key={area.slug}
								area={area}
								areas={sortedAreas}
								allPractices={practices}
								practices={byArea.get(area.slug) ?? []}
								totalPracticeCount={
									practices.filter((practice) => practice.areaSlug === area.slug).length
								}
								workspaceSlug={workspaceSlug}
								pendingAreaSlugs={pending.areaSlugs}
								pendingPracticeSlugs={pending.practiceSlugs}
								reorderDisabled={pending.areaStructure}
								blockedPracticeOrderBuckets={pending.blockedPracticeOrderBuckets}
								blockedMoveDestinationSlugs={pending.blockedMoveDestinationSlugs}
								dropTarget={dropTarget}
								onRequestRename={setRenamingArea}
								onToggleActive={onToggleAreaActive}
								onDelete={onDeleteArea}
								onSetVisual={onSetAreaVisual}
								onSetPracticeActive={onSetPracticeActive}
								onDeletePractice={onDeletePractice}
								onPlacePractice={onPlacePractice}
								showPracticeHandles={showPracticeHandles}
							/>
						))}
					</Accordion>
				</SortableContext>

				{(practices.length > 0 || sortedAreas.length > 0) && (
					<div className="rounded-lg border border-dashed">
						<div className="flex items-center gap-2 border-b border-dashed px-3 py-2">
							<span className="text-sm font-semibold text-muted-foreground">Unassigned</span>
							<Badge variant="secondary">{totalUnassignedCount}</Badge>
						</div>
						<div className="px-2 py-1">
							<PracticeBucket
								areaSlug={null}
								areaName="Unassigned"
								allPractices={practices}
								practices={unassigned}
								workspaceSlug={workspaceSlug}
								pendingPracticeSlugs={pending.practiceSlugs}
								reorderDisabled={pending.blockedPracticeOrderBuckets.has(UNASSIGNED)}
								blockedMoveDestinationSlugs={pending.blockedMoveDestinationSlugs}
								dropTarget={dropTarget}
								onSetPracticeActive={onSetPracticeActive}
								onDeletePractice={onDeletePractice}
								areas={sortedAreas}
								onPlacePractice={onPlacePractice}
								showReorderHandles={showPracticeHandles}
								emptyLabel={
									focusFilter === "ALL"
										? "No unassigned practices."
										: "No matching unassigned practices."
								}
							/>
						</div>
					</div>
				)}

				<DragOverlay adjustScale={false}>
					{draggedPractice ? <PracticeDragPreview practice={draggedPractice} /> : null}
				</DragOverlay>
			</DndContext>

			{areas.length === 0 && practices.length === 0 && (
				<Empty className="border">
					<EmptyHeader>
						<EmptyTitle>No practices yet</EmptyTitle>
						<EmptyDescription>
							Create a practice, then group related practices into areas.
						</EmptyDescription>
					</EmptyHeader>
				</Empty>
			)}

			<RenameAreaDialog
				area={renamingArea}
				onClose={() => setRenamingArea(null)}
				onRename={onRenameArea}
				pending={renamingArea ? pending.areaSlugs.has(renamingArea.slug) : false}
			/>
		</div>
	);
}

function RenameAreaDialog({
	area,
	onClose,
	onRename,
	pending,
}: {
	area: PracticeArea | null;
	onClose: () => void;
	onRename: (slug: string, name: string) => Promise<boolean>;
	pending: boolean;
}) {
	return (
		<Dialog open={area !== null} onOpenChange={(open) => !open && onClose()}>
			<DialogContent className="sm:max-w-sm">
				<DialogHeader>
					<DialogTitle>Rename area</DialogTitle>
				</DialogHeader>
				<form
					onSubmit={async (e) => {
						e.preventDefault();
						const input = e.currentTarget.elements.namedItem("areaName") as HTMLInputElement;
						const name = input.value.trim();
						if (!area || !name || name === area.name) {
							onClose();
							return;
						}
						if (await onRename(area.slug, name)) onClose();
					}}
					className="space-y-4"
				>
					<Input
						name="areaName"
						defaultValue={area?.name ?? ""}
						aria-label="Area name"
						autoComplete="off"
						disabled={pending}
					/>
					<DialogFooter>
						<Button type="button" variant="outline" onClick={onClose} disabled={pending}>
							Cancel
						</Button>
						<Button type="submit" className="min-w-20" disabled={pending}>
							{pending ? "Saving…" : "Save"}
						</Button>
					</DialogFooter>
				</form>
			</DialogContent>
		</Dialog>
	);
}

function SortableArea({
	area,
	areas,
	allPractices,
	practices,
	totalPracticeCount,
	workspaceSlug,
	pendingAreaSlugs,
	pendingPracticeSlugs,
	reorderDisabled,
	blockedPracticeOrderBuckets,
	blockedMoveDestinationSlugs,
	dropTarget,
	onRequestRename,
	onToggleActive,
	onDelete,
	onSetVisual,
	onSetPracticeActive,
	onDeletePractice,
	onPlacePractice,
	showPracticeHandles,
}: {
	area: PracticeArea;
	areas: PracticeArea[];
	allPractices: Practice[];
	practices: Practice[];
	totalPracticeCount: number;
	workspaceSlug: string;
	pendingAreaSlugs: ReadonlySet<string>;
	pendingPracticeSlugs: ReadonlySet<string>;
	reorderDisabled: boolean;
	blockedPracticeOrderBuckets: ReadonlySet<string>;
	blockedMoveDestinationSlugs: ReadonlySet<string>;
	dropTarget: ActivePracticeDrop | null;
	onRequestRename: (area: PracticeArea) => void;
	onToggleActive: (slug: string, active: boolean) => void;
	onDelete: (slug: string) => void;
	onSetVisual: (slug: string, patch: { icon?: string; color?: string }) => void;
	onSetPracticeActive: (slug: string, active: boolean) => void;
	onDeletePractice: (practice: Practice) => void;
	onPlacePractice: (practiceSlug: string, areaSlug: string | null, position: number) => void;
	showPracticeHandles: boolean;
}) {
	const pending = pendingAreaSlugs.has(area.slug);
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
	const style = { transform: CSS.Transform.toString(transform), transition };

	return (
		<AccordionItem
			ref={setDraggableNodeRef}
			value={area.slug}
			style={style}
			className={cn("rounded-lg border bg-card", isDragging && "z-10 opacity-40")}
		>
			<div
				ref={setDroppableNodeRef}
				className={cn(
					"flex items-center gap-2 rounded-lg px-2 transition-colors [&>h3]:min-w-0 [&>h3]:flex-1",
					isOver && active?.data.current?.type === "practice" && "bg-accent/70",
				)}
			>
				<Button
					ref={setActivatorNodeRef}
					type="button"
					variant="ghost"
					size="icon"
					className="touch-none shrink-0 cursor-grab text-muted-foreground active:cursor-grabbing disabled:cursor-default"
					aria-label={`Reorder area ${area.name}`}
					disabled={reorderDisabled}
					{...attributes}
					{...listeners}
				>
					<GripVertical className="size-4" />
				</Button>
				<AreaVisualPicker
					slug={area.slug}
					name={area.name}
					icon={area.icon}
					color={area.color}
					onChange={(patch) => onSetVisual(area.slug, patch)}
					disabled={pending}
				/>
				<AccordionTrigger className="w-full min-w-0 py-2.5 hover:no-underline">
					<span className="flex min-w-0 flex-wrap items-center gap-2">
						<span className="min-w-0 break-words font-medium">{area.name}</span>
						<Badge variant="secondary" className="shrink-0">
							{totalPracticeCount}
						</Badge>
						{!area.active && (
							<Badge variant="outline" className="shrink-0">
								Hidden from practice dashboards
							</Badge>
						)}
						<CatalogOriginBadge origin={area.catalogOrigin} kind="area" />
					</span>
				</AccordionTrigger>
				<div className="ml-auto flex items-center gap-2">
					<Switch
						className="hidden sm:inline-flex"
						checked={area.active}
						onCheckedChange={(c) => onToggleActive(area.slug, c)}
						disabled={pending}
						aria-label={`${area.name} area shown on practice dashboards`}
					/>
					<DropdownMenu>
						<DropdownMenuTrigger
							render={
								<Button
									variant="ghost"
									size="icon-sm"
									aria-label={`More actions for ${area.name}`}
									disabled={pending}
								>
									<MoreHorizontal className="size-4" />
								</Button>
							}
						/>
						<DropdownMenuContent align="end">
							<DropdownMenuItem disabled={pending} onClick={() => onRequestRename(area)}>
								Rename
							</DropdownMenuItem>
							<DropdownMenuItem
								disabled={pending}
								onClick={() => onToggleActive(area.slug, !area.active)}
							>
								{area.active ? "Hide from practice dashboards" : "Show on practice dashboards"}
							</DropdownMenuItem>
							<DropdownMenuSeparator />
							<DropdownMenuItem
								variant="destructive"
								disabled={pending || reorderDisabled}
								onClick={() => onDelete(area.slug)}
							>
								Delete area
							</DropdownMenuItem>
						</DropdownMenuContent>
					</DropdownMenu>
				</div>
			</div>
			<AccordionContent className="px-2 pb-2 sm:pl-9">
				<PracticeBucket
					areaSlug={area.slug}
					areaName={area.name}
					allPractices={allPractices}
					practices={practices}
					workspaceSlug={workspaceSlug}
					pendingPracticeSlugs={pendingPracticeSlugs}
					reorderDisabled={blockedPracticeOrderBuckets.has(area.slug)}
					blockedMoveDestinationSlugs={blockedMoveDestinationSlugs}
					dropTarget={dropTarget}
					onSetPracticeActive={onSetPracticeActive}
					onDeletePractice={onDeletePractice}
					areas={areas}
					onPlacePractice={onPlacePractice}
					showReorderHandles={showPracticeHandles}
					emptyLabel={
						totalPracticeCount > 0 ? "No matching practices." : "No practices in this area."
					}
				/>
			</AccordionContent>
		</AccordionItem>
	);
}

function PracticeBucket({
	areaSlug,
	areaName,
	allPractices,
	practices,
	workspaceSlug,
	pendingPracticeSlugs,
	reorderDisabled,
	blockedMoveDestinationSlugs,
	dropTarget,
	onSetPracticeActive,
	onDeletePractice,
	areas,
	onPlacePractice,
	showReorderHandles,
	emptyLabel,
}: {
	areaSlug: string | null;
	areaName: string;
	allPractices: Practice[];
	practices: Practice[];
	workspaceSlug: string;
	pendingPracticeSlugs: ReadonlySet<string>;
	reorderDisabled: boolean;
	blockedMoveDestinationSlugs: ReadonlySet<string>;
	dropTarget: ActivePracticeDrop | null;
	onSetPracticeActive: (slug: string, active: boolean) => void;
	onDeletePractice: (practice: Practice) => void;
	areas: PracticeArea[];
	onPlacePractice: (practiceSlug: string, areaSlug: string | null, position: number) => void;
	showReorderHandles: boolean;
	emptyLabel: string;
}) {
	const ordered = [...practices].sort(
		(a, b) => a.displayOrder - b.displayOrder || a.name.localeCompare(b.name),
	);
	const { active, isOver, setNodeRef } = useDroppable({
		id: bucketDndId(areaSlug),
		data: { type: "bucket", areaSlug, label: areaName } satisfies CatalogDndData,
		disabled: reorderDisabled || blockedMoveDestinationSlugs.has(areaSlug ?? UNASSIGNED),
	});
	const activeData = active?.data.current as CatalogDndData | undefined;
	const isPracticeDrag = activeData?.type === "practice";
	const activePracticeSlug = activeData?.type === "practice" ? activeData.practiceSlug : undefined;
	const destinationPractices = ordered.filter((practice) => practice.slug !== activePracticeSlug);
	const destinationPositionBySlug = new Map(
		destinationPractices.map((practice, position) => [practice.slug, position]),
	);
	const isTargeted = dropTarget?.areaSlug === areaSlug && dropTarget.overType !== "area";
	return (
		<div
			ref={setNodeRef}
			className={cn(
				"relative min-h-12 rounded-md border border-transparent transition-[background-color,border-color,box-shadow]",
				isPracticeDrag && "border-dashed border-border bg-muted/20",
				isTargeted && isPracticeDrag && "border-solid border-primary/60 bg-accent/50 shadow-sm",
			)}
		>
			<SortableContext
				items={ordered.map((practice) => practiceDndId(practice.slug))}
				strategy={verticalListSortingStrategy}
			>
				<ItemGroup className="gap-0.5">
					{ordered.map((practice) => (
						<div key={practice.slug} role="presentation" className="relative">
							<DropIndicator
								visible={
									isTargeted && dropTarget.position === destinationPositionBySlug.get(practice.slug)
								}
							/>
							<SortablePracticeRow
								practice={practice}
								workspaceSlug={workspaceSlug}
								pending={pendingPracticeSlugs.has(practice.slug)}
								onSetActive={onSetPracticeActive}
								onDelete={onDeletePractice}
								areas={areas}
								allPractices={allPractices}
								onPlace={onPlacePractice}
								showReorderHandle={showReorderHandles}
								reorderDisabled={reorderDisabled}
								blockedMoveDestinationSlugs={blockedMoveDestinationSlugs}
							/>
						</div>
					))}
				</ItemGroup>
				<DropIndicator
					visible={isTargeted && dropTarget.position === destinationPractices.length}
					atEnd
				/>
			</SortableContext>
			{ordered.length === 0 && (
				<p className="flex min-h-12 items-center px-2 py-3 text-sm text-muted-foreground">
					{isOver && isPracticeDrag ? "Release to move here." : emptyLabel}
				</p>
			)}
		</div>
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

function SortablePracticeRow({
	practice,
	workspaceSlug,
	pending,
	onSetActive,
	onDelete,
	areas,
	allPractices,
	onPlace,
	showReorderHandle,
	reorderDisabled,
	blockedMoveDestinationSlugs,
}: {
	practice: Practice;
	workspaceSlug: string;
	pending: boolean;
	onSetActive: (slug: string, active: boolean) => void;
	onDelete: (practice: Practice) => void;
	areas: PracticeArea[];
	allPractices: Practice[];
	onPlace: (practiceSlug: string, areaSlug: string | null, position: number) => void;
	showReorderHandle: boolean;
	reorderDisabled: boolean;
	blockedMoveDestinationSlugs: ReadonlySet<string>;
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
		id: practiceDndId(practice.slug),
		data: {
			type: "practice",
			areaSlug: practice.areaSlug ?? null,
			label: practice.name,
			practiceSlug: practice.slug,
		} satisfies CatalogDndData,
		disabled: !showReorderHandle || reorderDisabled || pending,
	});
	const style = { transform: CSS.Transform.toString(transform), transition };
	return (
		<Item
			ref={setNodeRef}
			role="listitem"
			style={style}
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
					aria-label={`Move practice ${practice.name}`}
					disabled={reorderDisabled || pending}
					{...attributes}
					{...listeners}
				>
					<GripVertical className="size-4" />
				</Button>
			)}
			<PracticeRowDetails
				practice={practice}
				title={
					<Link
						to="/w/$workspaceSlug/admin/practices/$practiceSlug"
						params={{ workspaceSlug, practiceSlug: practice.slug }}
						className="break-words rounded-sm hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
					>
						{practice.name}
					</Link>
				}
			/>
			<ItemActions className="ml-auto">
				<Switch
					className="hidden sm:inline-flex"
					checked={practice.active}
					onCheckedChange={(c) => onSetActive(practice.slug, c)}
					disabled={pending}
					aria-label={`${practice.name} included in new reviews`}
				/>
				<DropdownMenu>
					<DropdownMenuTrigger
						render={
							<Button
								variant="ghost"
								size="icon-sm"
								aria-label={`More actions for ${practice.name}`}
							>
								<MoreHorizontal className="size-4" />
							</Button>
						}
					/>
					<DropdownMenuContent align="end">
						<DropdownMenuItem
							render={
								<Link
									to="/w/$workspaceSlug/admin/practices/$practiceSlug"
									params={{ workspaceSlug, practiceSlug: practice.slug }}
								/>
							}
						>
							Edit practice
						</DropdownMenuItem>
						<DropdownMenuItem
							disabled={pending}
							onClick={() => onSetActive(practice.slug, !practice.active)}
						>
							{practice.active ? "Exclude from new reviews" : "Include in new reviews"}
						</DropdownMenuItem>
						<DropdownMenuSeparator />
						<DropdownMenuGroup>
							<DropdownMenuLabel>Move to</DropdownMenuLabel>
							<DropdownMenuRadioGroup
								value={practice.areaSlug ?? UNASSIGNED}
								onValueChange={(value) => {
									const areaSlug = value === UNASSIGNED ? null : value;
									if ((practice.areaSlug ?? null) === areaSlug) return;
									const position = getPracticeDropTarget(
										allPractices,
										practice.slug,
										areaSlug,
									)?.position;
									if (position !== undefined) onPlace(practice.slug, areaSlug, position);
								}}
							>
								<DropdownMenuRadioItem
									value={UNASSIGNED}
									disabled={blockedMoveDestinationSlugs.has(UNASSIGNED)}
									closeOnClick
								>
									Unassigned
								</DropdownMenuRadioItem>
								{areas
									.filter((area) => !blockedMoveDestinationSlugs.has(area.slug))
									.map((area) => (
										<DropdownMenuRadioItem key={area.slug} value={area.slug} closeOnClick>
											{area.name}
										</DropdownMenuRadioItem>
									))}
							</DropdownMenuRadioGroup>
						</DropdownMenuGroup>
						<DropdownMenuSeparator />
						<DropdownMenuItem
							variant="destructive"
							disabled={pending}
							onClick={() => onDelete(practice)}
						>
							Delete practice
						</DropdownMenuItem>
					</DropdownMenuContent>
				</DropdownMenu>
			</ItemActions>
		</Item>
	);
}

function PracticeRowDetails({ practice, title }: { practice: Practice; title: ReactNode }) {
	return (
		<ItemContent className="min-w-0">
			<ItemTitle className="w-full min-w-0 line-clamp-none">{title}</ItemTitle>
			<ItemDescription className="flex flex-wrap items-center gap-1.5">
				<span>{ARTIFACT_LABELS[practice.artifactType]}</span>
				{!practice.active && <Badge variant="outline">Excluded</Badge>}
				<CatalogOriginBadge origin={practice.catalogOrigin} kind="practice" />
				{practice.precomputeScript && <Badge variant="outline">Precompute</Badge>}
			</ItemDescription>
		</ItemContent>
	);
}

function PracticeDragPreview({ practice }: { practice: Practice }) {
	return (
		<Item
			aria-hidden="true"
			variant="outline"
			size="xs"
			className="flex-nowrap bg-popover text-popover-foreground shadow-lg ring-1 ring-foreground/10"
		>
			<div className="flex size-8 shrink-0 items-center justify-center text-muted-foreground">
				<GripVertical className="size-4" />
			</div>
			<PracticeRowDetails
				practice={practice}
				title={<span className="break-words">{practice.name}</span>}
			/>
		</Item>
	);
}

function AddAreaButton({
	onCreate,
	pending,
	disabled,
}: {
	onCreate: (name: string) => Promise<boolean>;
	pending: boolean;
	disabled: boolean;
}) {
	const [open, setOpen] = useState(false);
	return (
		<Popover open={open} onOpenChange={setOpen}>
			<PopoverTrigger
				render={
					<Button variant="outline" disabled={disabled}>
						<Plus className="mr-1.5 size-4" />
						Add area
					</Button>
				}
			/>
			<PopoverContent align="end" className="w-72">
				<form
					onSubmit={async (e) => {
						e.preventDefault();
						const input = e.currentTarget.elements.namedItem("areaName") as HTMLInputElement;
						const name = input.value.trim();
						if (name && (await onCreate(name))) setOpen(false);
					}}
					className="flex items-center gap-2"
				>
					<Input
						name="areaName"
						placeholder="New area name…"
						aria-label="New practice area name"
						autoComplete="off"
						disabled={pending}
					/>
					<Button type="submit" size="sm" className="min-w-16" disabled={pending}>
						{pending ? "Adding…" : "Add"}
					</Button>
				</form>
			</PopoverContent>
		</Popover>
	);
}

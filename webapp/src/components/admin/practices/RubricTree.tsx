import {
	closestCenter,
	DndContext,
	type DragEndEvent,
	KeyboardSensor,
	PointerSensor,
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
import { Code2, GripVertical, MoreHorizontal, Plus } from "lucide-react";
import { useState } from "react";
import type { Practice, PracticeArea } from "@/api/types.gen";
import {
	Accordion,
	AccordionContent,
	AccordionItem,
	AccordionTrigger,
} from "@/components/ui/accordion";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
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
	DropdownMenuItem,
	DropdownMenuSeparator,
	DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Input } from "@/components/ui/input";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Switch } from "@/components/ui/switch";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { cn } from "@/lib/utils";
import { AreaVisualPicker } from "./AreaVisualPicker";

export type FocusFilter = "ALL" | "PULL_REQUEST" | "ISSUE";

export interface RubricTreeProps {
	workspaceSlug: string;
	areas: PracticeArea[];
	practices: Practice[];
	togglingPractices: Set<string>;
	isMutating: boolean;
	focusFilter: FocusFilter;
	onFocusFilterChange: (f: FocusFilter) => void;
	// area actions
	onCreateArea: (name: string) => void;
	onRenameArea: (slug: string, name: string) => void;
	onToggleAreaActive: (slug: string, active: boolean) => void;
	onDeleteArea: (slug: string) => void;
	onReorderAreas: (orderedSlugs: string[]) => void;
	onSetAreaVisual: (slug: string, patch: { icon?: string; color?: string }) => void;
	// practice actions
	onSetPracticeActive: (slug: string, active: boolean) => void;
	onDeletePractice: (practice: Practice) => void;
	onReorderPractices: (areaSlug: string | null, orderedSlugs: string[]) => void;
}

const UNASSIGNED = "__unassigned__";

/**
 * The consolidated rubric: one accordion where each area section's header carries the area-level controls
 * and its content lists that area's practices. Both areas and within-area practices drag-reorder via
 * dnd-kit. Per the accordion contract, only the chevron+title is the trigger — the drag handle, picker,
 * switch and menu are siblings, never nested in the (button) trigger.
 */
export function RubricTree({
	workspaceSlug,
	areas,
	practices,
	togglingPractices,
	isMutating,
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
	onReorderPractices,
}: RubricTreeProps) {
	const [renamingArea, setRenamingArea] = useState<PracticeArea | null>(null);
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

	const sensors = useSensors(
		useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
		useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
	);

	const onAreaDragEnd = (e: DragEndEvent) => {
		const { active, over } = e;
		if (!over || active.id === over.id) return;
		const ids = sortedAreas.map((a) => a.slug);
		const next = arrayMove(ids, ids.indexOf(String(active.id)), ids.indexOf(String(over.id)));
		onReorderAreas(next);
	};

	return (
		<div className="space-y-4">
			<div className="flex flex-wrap items-center justify-between gap-3">
				<ToggleGroup
					value={[focusFilter]}
					onValueChange={(v) => v[0] && onFocusFilterChange(v[0] as FocusFilter)}
					variant="outline"
					size="sm"
					aria-label="Filter by artifact"
				>
					<ToggleGroupItem value="ALL">All</ToggleGroupItem>
					<ToggleGroupItem value="PULL_REQUEST">Pull requests</ToggleGroupItem>
					<ToggleGroupItem value="ISSUE">Issues</ToggleGroupItem>
				</ToggleGroup>
				<div className="flex items-center gap-2">
					<AddAreaButton onCreate={onCreateArea} disabled={isMutating} />
					<Button
						render={<Link to="/w/$workspaceSlug/admin/practices/new" params={{ workspaceSlug }} />}
					>
						<Plus className="mr-1.5 size-4" />
						New practice
					</Button>
				</div>
			</div>

			<DndContext
				sensors={sensors}
				collisionDetection={closestCenter}
				modifiers={[restrictToVerticalAxis]}
				onDragEnd={onAreaDragEnd}
			>
				<SortableContext
					items={sortedAreas.map((a) => a.slug)}
					strategy={verticalListSortingStrategy}
				>
					<Accordion className="space-y-2">
						{sortedAreas.map((area) => (
							<SortableArea
								key={area.slug}
								area={area}
								practices={byArea.get(area.slug) ?? []}
								workspaceSlug={workspaceSlug}
								togglingPractices={togglingPractices}
								isMutating={isMutating}
								sensors={sensors}
								onRequestRename={setRenamingArea}
								onToggleActive={onToggleAreaActive}
								onDelete={onDeleteArea}
								onSetVisual={onSetAreaVisual}
								onSetPracticeActive={onSetPracticeActive}
								onDeletePractice={onDeletePractice}
								onReorderPractices={onReorderPractices}
							/>
						))}
					</Accordion>
				</SortableContext>
			</DndContext>

			{unassigned.length > 0 && (
				<div className="rounded-lg border border-dashed">
					<div className="flex items-center gap-2 border-b border-dashed px-3 py-2">
						<span className="text-sm font-semibold text-muted-foreground">Unassigned</span>
						<Badge variant="secondary">{unassigned.length}</Badge>
					</div>
					<div className="py-1 pl-3 pr-2">
						<PracticeRows
							areaSlug={null}
							practices={unassigned}
							workspaceSlug={workspaceSlug}
							togglingPractices={togglingPractices}
							sensors={sensors}
							onSetPracticeActive={onSetPracticeActive}
							onDeletePractice={onDeletePractice}
							onReorderPractices={onReorderPractices}
						/>
					</div>
				</div>
			)}

			{sortedAreas.length === 0 && unassigned.length === 0 && (
				<p className="py-12 text-center text-sm text-muted-foreground">
					No practice areas yet. Add one to start grouping practices.
				</p>
			)}

			<RenameAreaDialog
				area={renamingArea}
				onClose={() => setRenamingArea(null)}
				onRename={onRenameArea}
			/>
		</div>
	);
}

function RenameAreaDialog({
	area,
	onClose,
	onRename,
}: {
	area: PracticeArea | null;
	onClose: () => void;
	onRename: (slug: string, name: string) => void;
}) {
	return (
		<Dialog open={area !== null} onOpenChange={(open) => !open && onClose()}>
			<DialogContent className="sm:max-w-sm">
				<DialogHeader>
					<DialogTitle>Rename area</DialogTitle>
				</DialogHeader>
				<form
					onSubmit={(e) => {
						e.preventDefault();
						const input = e.currentTarget.elements.namedItem("areaName") as HTMLInputElement;
						const name = input.value.trim();
						if (area && name && name !== area.name) onRename(area.slug, name);
						onClose();
					}}
					className="space-y-4"
				>
					<Input
						name="areaName"
						defaultValue={area?.name ?? ""}
						aria-label="Area name"
						autoComplete="off"
					/>
					<DialogFooter>
						<Button type="button" variant="outline" onClick={onClose}>
							Cancel
						</Button>
						<Button type="submit">Save</Button>
					</DialogFooter>
				</form>
			</DialogContent>
		</Dialog>
	);
}

function SortableArea({
	area,
	practices,
	workspaceSlug,
	togglingPractices,
	isMutating,
	sensors,
	onRequestRename,
	onToggleActive,
	onDelete,
	onSetVisual,
	onSetPracticeActive,
	onDeletePractice,
	onReorderPractices,
}: {
	area: PracticeArea;
	practices: Practice[];
	workspaceSlug: string;
	togglingPractices: Set<string>;
	isMutating: boolean;
	sensors: ReturnType<typeof useSensors>;
	onRequestRename: (area: PracticeArea) => void;
	onToggleActive: (slug: string, active: boolean) => void;
	onDelete: (slug: string) => void;
	onSetVisual: (slug: string, patch: { icon?: string; color?: string }) => void;
	onSetPracticeActive: (slug: string, active: boolean) => void;
	onDeletePractice: (practice: Practice) => void;
	onReorderPractices: (areaSlug: string | null, orderedSlugs: string[]) => void;
}) {
	const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
		id: area.slug,
	});
	const style = { transform: CSS.Transform.toString(transform), transition };

	return (
		<AccordionItem
			ref={setNodeRef}
			value={area.slug}
			style={style}
			className={cn(
				"rounded-lg border bg-card",
				isDragging && "z-10 shadow-lg",
				!area.active && "opacity-60",
			)}
		>
			<div className="flex items-center gap-2 px-2">
				<button
					type="button"
					className="flex size-8 shrink-0 cursor-grab items-center justify-center text-muted-foreground hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring active:cursor-grabbing"
					aria-label={`Drag ${area.name}`}
					{...attributes}
					{...listeners}
				>
					<GripVertical className="size-4" />
				</button>
				<AreaVisualPicker
					slug={area.slug}
					name={area.name}
					icon={area.icon}
					color={area.color}
					onChange={(patch) => onSetVisual(area.slug, patch)}
					disabled={isMutating}
				/>
				<AccordionTrigger className="min-w-0 shrink py-2.5 hover:no-underline">
					<span className="flex min-w-0 items-center gap-2">
						<span className="truncate font-medium">{area.name}</span>
						<Badge variant="secondary" className="shrink-0">
							{practices.length}
						</Badge>
					</span>
				</AccordionTrigger>
				<Switch
					checked={area.active}
					onCheckedChange={(c) => onToggleActive(area.slug, c)}
					disabled={isMutating}
					aria-label={`Toggle ${area.name} active`}
				/>
				<DropdownMenu>
					<DropdownMenuTrigger
						render={
							<Button variant="ghost" size="icon-sm" aria-label={`More actions for ${area.name}`}>
								<MoreHorizontal className="size-4" />
							</Button>
						}
					/>
					<DropdownMenuContent align="end">
						<DropdownMenuItem onClick={() => onRequestRename(area)}>Rename</DropdownMenuItem>
						<DropdownMenuSeparator />
						<DropdownMenuItem variant="destructive" onClick={() => onDelete(area.slug)}>
							Delete area
						</DropdownMenuItem>
					</DropdownMenuContent>
				</DropdownMenu>
				<div className="flex-1" />
			</div>
			<AccordionContent className="pb-2 pl-9 pr-2">
				{practices.length === 0 ? (
					<p className="px-2 py-3 text-sm text-muted-foreground">No practices in this area yet.</p>
				) : (
					<PracticeRows
						areaSlug={area.slug}
						practices={practices}
						workspaceSlug={workspaceSlug}
						togglingPractices={togglingPractices}
						sensors={sensors}
						onSetPracticeActive={onSetPracticeActive}
						onDeletePractice={onDeletePractice}
						onReorderPractices={onReorderPractices}
					/>
				)}
			</AccordionContent>
		</AccordionItem>
	);
}

function PracticeRows({
	areaSlug,
	practices,
	workspaceSlug,
	togglingPractices,
	sensors,
	onSetPracticeActive,
	onDeletePractice,
	onReorderPractices,
}: {
	areaSlug: string | null;
	practices: Practice[];
	workspaceSlug: string;
	togglingPractices: Set<string>;
	sensors: ReturnType<typeof useSensors>;
	onSetPracticeActive: (slug: string, active: boolean) => void;
	onDeletePractice: (practice: Practice) => void;
	onReorderPractices: (areaSlug: string | null, orderedSlugs: string[]) => void;
}) {
	const ordered = [...practices].sort(
		(a, b) => a.displayOrder - b.displayOrder || a.name.localeCompare(b.name),
	);
	const onDragEnd = (e: DragEndEvent) => {
		const { active, over } = e;
		if (!over || active.id === over.id) return;
		const ids = ordered.map((p) => p.slug);
		onReorderPractices(
			areaSlug,
			arrayMove(ids, ids.indexOf(String(active.id)), ids.indexOf(String(over.id))),
		);
	};
	return (
		<DndContext
			sensors={sensors}
			collisionDetection={closestCenter}
			modifiers={[restrictToVerticalAxis]}
			onDragEnd={onDragEnd}
		>
			<SortableContext items={ordered.map((p) => p.slug)} strategy={verticalListSortingStrategy}>
				<div className="space-y-0.5">
					{ordered.map((p) => (
						<SortablePracticeRow
							key={p.slug}
							practice={p}
							workspaceSlug={workspaceSlug}
							isToggling={togglingPractices.has(p.slug)}
							onSetActive={onSetPracticeActive}
							onDelete={onDeletePractice}
						/>
					))}
				</div>
			</SortableContext>
		</DndContext>
	);
}

function SortablePracticeRow({
	practice,
	workspaceSlug,
	isToggling,
	onSetActive,
	onDelete,
}: {
	practice: Practice;
	workspaceSlug: string;
	isToggling: boolean;
	onSetActive: (slug: string, active: boolean) => void;
	onDelete: (practice: Practice) => void;
}) {
	const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
		id: practice.slug,
	});
	const style = { transform: CSS.Transform.toString(transform), transition };
	return (
		<div
			ref={setNodeRef}
			style={style}
			className={cn(
				"flex items-center gap-2 rounded-md py-1 transition-colors hover:bg-muted/60",
				isDragging && "z-10 bg-background shadow-md",
				!practice.active && "opacity-60",
			)}
		>
			<button
				type="button"
				className="flex size-7 shrink-0 cursor-grab items-center justify-center text-muted-foreground hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring active:cursor-grabbing"
				aria-label={`Drag ${practice.name}`}
				{...attributes}
				{...listeners}
			>
				<GripVertical className="size-4" />
			</button>
			<Badge variant="outline" className="w-12 shrink-0 justify-center">
				{practice.artifactType === "ISSUE" ? "Issue" : "PR"}
			</Badge>
			<Link
				to="/w/$workspaceSlug/admin/practices/$practiceSlug"
				params={{ workspaceSlug, practiceSlug: practice.slug }}
				className="min-w-0 shrink rounded-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
			>
				<span className="flex items-center gap-1.5">
					<span className="truncate text-sm font-medium hover:underline">{practice.name}</span>
					{practice.precomputeScript && (
						<Tooltip>
							<TooltipTrigger
								render={<Code2 className="size-3.5 shrink-0 text-muted-foreground" />}
							/>
							<TooltipContent>Has precompute script</TooltipContent>
						</Tooltip>
					)}
				</span>
				<span className="block truncate text-xs text-muted-foreground">{practice.slug}</span>
			</Link>
			<Switch
				checked={practice.active}
				onCheckedChange={(c) => onSetActive(practice.slug, c)}
				disabled={isToggling}
				aria-label={`Toggle ${practice.name} active`}
			/>
			<DropdownMenu>
				<DropdownMenuTrigger
					render={
						<Button variant="ghost" size="icon-sm" aria-label={`More actions for ${practice.name}`}>
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
						Edit standard
					</DropdownMenuItem>
					<DropdownMenuSeparator />
					<DropdownMenuItem variant="destructive" onClick={() => onDelete(practice)}>
						Delete practice
					</DropdownMenuItem>
				</DropdownMenuContent>
			</DropdownMenu>
			<div className="flex-1" />
		</div>
	);
}

function AddAreaButton({
	onCreate,
	disabled,
}: {
	onCreate: (name: string) => void;
	disabled?: boolean;
}) {
	return (
		<Popover>
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
					onSubmit={(e) => {
						e.preventDefault();
						const input = e.currentTarget.elements.namedItem("areaName") as HTMLInputElement;
						const name = input.value.trim();
						if (name) {
							onCreate(name);
							input.value = "";
						}
					}}
					className="flex items-center gap-2"
				>
					<Input
						name="areaName"
						placeholder="New area name…"
						aria-label="New practice area name"
						autoComplete="off"
					/>
					<Button type="submit" size="sm">
						Add
					</Button>
				</form>
			</PopoverContent>
		</Popover>
	);
}

import { Link } from "@tanstack/react-router";
import { GripVertical, MoreHorizontal, Plus } from "lucide-react";
import { type ReactNode, useState } from "react";
import type { Practice, PracticeArea, PracticeDefinitionOptions } from "@/api/types.gen";
import { AreaVisualPicker } from "@/components/admin/practice-catalog/AreaVisualPicker";
import { WORK_ARTIFACT_FILTER_OPTIONS } from "@/components/admin/practice-catalog/constants";
import {
	automatedReviewUnavailableLabel,
	canAttemptAutomatedReview,
} from "@/components/admin/practice-catalog/evidence-presentation";
import {
	type CatalogEntryMoveActions,
	type CatalogMoveActions,
	SortableCatalogTree,
	UNASSIGNED_CATALOG_BUCKET,
} from "@/components/admin/practice-catalog/SortableCatalogTree";
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
import { Item, ItemContent, ItemDescription, ItemTitle } from "@/components/ui/item";
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
import { ARTIFACT_KIND, artifactKindLabel, type KnownArtifactKind } from "@/lib/artifact-kinds";
import { cn } from "@/lib/utils";
import { CatalogOriginBadge } from "./CatalogOriginBadge";

export type FocusFilter = "ALL" | KnownArtifactKind;

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
	definitionOptions: PracticeDefinitionOptions;
	pending: PracticeCatalogPendingState;
	focusFilter: FocusFilter;
	onFocusFilterChange: (f: FocusFilter) => void;
	onCreateArea: (name: string) => Promise<boolean>;
	onRenameArea: (slug: string, name: string) => Promise<boolean>;
	onSetAreaDashboardVisibility: (slug: string, visibleInPracticeDashboards: boolean) => void;
	onDeleteArea: (slug: string) => void;
	onReorderAreas: (orderedSlugs: string[]) => void;
	onSetAreaVisual: (slug: string, patch: { icon?: string; color?: string }) => void;
	onSetPracticeUsedInNewReviews: (slug: string, usedInNewReviews: boolean) => void;
	onDeletePractice: (practice: Practice) => void;
	onPlacePractice: (practiceSlug: string, areaSlug: string | null, position: number) => void;
}

const FOCUS_FILTERS = [
	{ value: "ALL", label: "All work types" },
	...WORK_ARTIFACT_FILTER_OPTIONS,
] satisfies Array<{ value: FocusFilter; label: string }>;

export function PracticeCatalog({
	workspaceSlug,
	areas,
	practices,
	definitionOptions,
	pending,
	focusFilter,
	onFocusFilterChange,
	onCreateArea,
	onRenameArea,
	onSetAreaDashboardVisibility,
	onDeleteArea,
	onReorderAreas,
	onSetAreaVisual,
	onSetPracticeUsedInNewReviews,
	onDeletePractice,
	onPlacePractice,
}: PracticeCatalogProps) {
	const [renamingArea, setRenamingArea] = useState<PracticeArea | null>(null);
	const visiblePracticeSlugs = new Set(
		practices
			.filter((practice) => focusFilter === "ALL" || practice.artifactKind === focusFilter)
			.map((practice) => practice.slug),
	);
	const forceOpenAreaSlugs =
		focusFilter === "ALL"
			? undefined
			: new Set(
					practices
						.filter((practice) => visiblePracticeSlugs.has(practice.slug))
						.map((practice) => practice.areaSlug)
						.filter((slug): slug is string => Boolean(slug)),
				);
	const supportedModesFor = (practice: Practice) =>
		definitionOptions.workTypes.find((option) => option.artifactKind === practice.artifactKind)
			?.supportedAutomatedReviewModes ?? [];

	return (
		<div className="space-y-4">
			<CatalogToolbar
				workspaceSlug={workspaceSlug}
				focusFilter={focusFilter}
				onFocusFilterChange={onFocusFilterChange}
				onCreateArea={onCreateArea}
				creatingArea={pending.creatingArea}
				areaStructurePending={pending.areaStructure}
			/>
			{focusFilter !== "ALL" && (
				<p className="text-muted-foreground text-sm">Clear the filter to reorder practices.</p>
			)}

			<SortableCatalogTree
				areas={areas}
				entries={practices}
				visibleEntrySlugs={visiblePracticeSlugs}
				forceOpenAreaSlugs={forceOpenAreaSlugs}
				areaReorderDisabled={pending.areaStructure}
				disabledAreaSlugs={pending.areaSlugs}
				disabledEntrySlugs={pending.practiceSlugs}
				blockedEntryOrderBuckets={pending.blockedPracticeOrderBuckets}
				blockedMoveDestinationSlugs={pending.blockedMoveDestinationSlugs}
				showEntryReorderHandles={focusFilter === "ALL"}
				onReorderAreas={onReorderAreas}
				onPlaceEntry={onPlacePractice}
				renderAreaLeading={(area) => (
					<AreaVisualPicker
						slug={area.slug}
						name={area.name}
						icon={area.icon}
						color={area.color}
						onChange={(patch) => onSetAreaVisual(area.slug, patch)}
						disabled={pending.areaSlugs.has(area.slug)}
					/>
				)}
				renderAreaMeta={(area) => (
					<>
						{!area.visibleInPracticeDashboards && (
							<Badge variant="outline">Hidden from practice dashboards</Badge>
						)}
						<CatalogOriginBadge origin={area.catalogOrigin} kind="area" />
					</>
				)}
				renderAreaActions={(area, move) => (
					<AreaActions
						area={area}
						move={move}
						pending={pending.areaSlugs.has(area.slug)}
						structurePending={pending.areaStructure}
						onRename={() => setRenamingArea(area)}
						onSetDashboardVisibility={onSetAreaDashboardVisibility}
						onDelete={onDeleteArea}
					/>
				)}
				renderEntryContent={(practice) => (
					<PracticeRowDetails
						practice={practice}
						supportedModes={supportedModesFor(practice)}
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
				)}
				renderEntryActions={(practice, move) => (
					<PracticeActions
						practice={practice}
						supportedModes={supportedModesFor(practice)}
						workspaceSlug={workspaceSlug}
						areas={areas}
						move={move}
						pending={pending.practiceSlugs.has(practice.slug)}
						onSetUsedInNewReviews={onSetPracticeUsedInNewReviews}
						onDelete={onDeletePractice}
					/>
				)}
				renderEntryPreview={(practice) => (
					<PracticeDragPreview practice={practice} supportedModes={supportedModesFor(practice)} />
				)}
				getEmptyLabel={(areaSlug, total) => {
					if (total > 0) return "No matching practices.";
					return areaSlug === null ? "No unassigned practices." : "No practices in this area.";
				}}
			/>

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

function CatalogToolbar({
	workspaceSlug,
	focusFilter,
	onFocusFilterChange,
	onCreateArea,
	creatingArea,
	areaStructurePending,
}: {
	workspaceSlug: string;
	focusFilter: FocusFilter;
	onFocusFilterChange: (filter: FocusFilter) => void;
	onCreateArea: (name: string) => Promise<boolean>;
	creatingArea: boolean;
	areaStructurePending: boolean;
}) {
	return (
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
				onValueChange={(value) => value[0] && onFocusFilterChange(value[0] as FocusFilter)}
				variant="outline"
				size="sm"
				aria-label="Filter by work type"
				className="hidden sm:flex"
			>
				{FOCUS_FILTERS.map((filter) => (
					<ToggleGroupItem
						key={filter.value}
						value={filter.value}
						className={cn(
							"min-w-0",
							filter.value === ARTIFACT_KIND.pullRequest &&
								"h-auto min-h-7 whitespace-normal py-1 sm:whitespace-nowrap",
						)}
					>
						{filter.value === "ALL" ? "All" : filter.label}
					</ToggleGroupItem>
				))}
			</ToggleGroup>
			<div className="grid grid-cols-2 gap-2 sm:flex">
				<CreateAreaButton
					onCreate={onCreateArea}
					pending={creatingArea}
					disabled={areaStructurePending && !creatingArea}
				/>
				<Link
					to="/w/$workspaceSlug/admin/practices/new"
					params={{ workspaceSlug }}
					className={cn(buttonVariants(), "w-full sm:w-auto")}
				>
					<Plus className="mr-1.5 size-4" />
					Create practice
				</Link>
			</div>
		</div>
	);
}

function AreaActions({
	area,
	move,
	pending,
	structurePending,
	onRename,
	onSetDashboardVisibility,
	onDelete,
}: {
	area: PracticeArea;
	move: CatalogMoveActions;
	pending: boolean;
	structurePending: boolean;
	onRename: () => void;
	onSetDashboardVisibility: (slug: string, visibleInPracticeDashboards: boolean) => void;
	onDelete: (slug: string) => void;
}) {
	return (
		<>
			<Switch
				className="hidden sm:inline-flex"
				checked={area.visibleInPracticeDashboards}
				onCheckedChange={(visibleInPracticeDashboards) =>
					onSetDashboardVisibility(area.slug, visibleInPracticeDashboards)
				}
				disabled={pending}
				aria-label={`Show ${area.name} on practice dashboards`}
			/>
			<DropdownMenu>
				<DropdownMenuTrigger
					render={
						<Button
							ref={move.actionTriggerRef}
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
					<DropdownMenuItem disabled={pending} onClick={onRename}>
						Rename
					</DropdownMenuItem>
					<DropdownMenuItem
						disabled={pending}
						onClick={() => onSetDashboardVisibility(area.slug, !area.visibleInPracticeDashboards)}
					>
						{area.visibleInPracticeDashboards
							? "Hide from practice dashboards"
							: "Show on practice dashboards"}
					</DropdownMenuItem>
					<DropdownMenuSeparator />
					<DropdownMenuGroup>
						<DropdownMenuLabel>Order</DropdownMenuLabel>
						<DropdownMenuItem disabled={!move.canMoveUp} onClick={move.moveUp}>
							Move up
						</DropdownMenuItem>
						<DropdownMenuItem disabled={!move.canMoveDown} onClick={move.moveDown}>
							Move down
						</DropdownMenuItem>
					</DropdownMenuGroup>
					<DropdownMenuSeparator />
					<DropdownMenuItem
						variant="destructive"
						disabled={pending || structurePending}
						onClick={() => onDelete(area.slug)}
					>
						Delete area
					</DropdownMenuItem>
				</DropdownMenuContent>
			</DropdownMenu>
		</>
	);
}

function PracticeActions({
	practice,
	supportedModes,
	workspaceSlug,
	areas,
	move,
	pending,
	onSetUsedInNewReviews,
	onDelete,
}: {
	practice: Practice;
	supportedModes: readonly Practice["automatedReviewPolicy"]["automatedReview"]["mode"][];
	workspaceSlug: string;
	areas: PracticeArea[];
	move: CatalogEntryMoveActions;
	pending: boolean;
	onSetUsedInNewReviews: (slug: string, usedInNewReviews: boolean) => void;
	onDelete: (practice: Practice) => void;
}) {
	const canReview = canAttemptAutomatedReview(practice.automatedReviewPolicy, supportedModes);
	const usageChangeDisabled = pending || (!practice.usedInNewReviews && !canReview);
	return (
		<>
			<Switch
				className="hidden sm:inline-flex"
				checked={practice.usedInNewReviews}
				onCheckedChange={(usedInNewReviews) =>
					onSetUsedInNewReviews(practice.slug, usedInNewReviews)
				}
				disabled={usageChangeDisabled}
				aria-label={`Use ${practice.name} in new reviews`}
			/>
			<DropdownMenu>
				<DropdownMenuTrigger
					render={
						<Button
							ref={move.actionTriggerRef}
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
						disabled={usageChangeDisabled}
						onClick={() => onSetUsedInNewReviews(practice.slug, !practice.usedInNewReviews)}
					>
						{practice.usedInNewReviews ? "Stop using in new reviews" : "Use in new reviews"}
					</DropdownMenuItem>
					<DropdownMenuSeparator />
					<DropdownMenuGroup>
						<DropdownMenuLabel>Order</DropdownMenuLabel>
						<DropdownMenuItem disabled={!move.canMoveUp} onClick={move.moveUp}>
							Move up
						</DropdownMenuItem>
						<DropdownMenuItem disabled={!move.canMoveDown} onClick={move.moveDown}>
							Move down
						</DropdownMenuItem>
					</DropdownMenuGroup>
					<DropdownMenuSeparator />
					<DropdownMenuGroup>
						<DropdownMenuLabel>Move to</DropdownMenuLabel>
						<DropdownMenuRadioGroup
							value={practice.areaSlug ?? UNASSIGNED_CATALOG_BUCKET}
							onValueChange={(value) =>
								move.moveTo(value === UNASSIGNED_CATALOG_BUCKET ? null : value)
							}
						>
							<DropdownMenuRadioItem
								value={UNASSIGNED_CATALOG_BUCKET}
								disabled={move.currentAreaSlug !== null && !move.canMoveTo(null)}
								closeOnClick
							>
								Unassigned
							</DropdownMenuRadioItem>
							{areas.map((area) => (
								<DropdownMenuRadioItem
									key={area.slug}
									value={area.slug}
									disabled={move.currentAreaSlug !== area.slug && !move.canMoveTo(area.slug)}
									closeOnClick
								>
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
		</>
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
					onSubmit={async (event) => {
						event.preventDefault();
						const input = event.currentTarget.elements.namedItem("areaName") as HTMLInputElement;
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

function PracticeRowDetails({
	practice,
	title,
	supportedModes,
}: {
	practice: Practice;
	title: ReactNode;
	supportedModes: readonly Practice["automatedReviewPolicy"]["automatedReview"]["mode"][];
}) {
	const unavailableLabel = automatedReviewUnavailableLabel(
		practice.automatedReviewPolicy,
		supportedModes,
	);
	return (
		<ItemContent className="min-w-0">
			<ItemTitle className="w-full min-w-0 line-clamp-none">{title}</ItemTitle>
			<ItemDescription className="flex flex-wrap items-center gap-1.5">
				<span>{artifactKindLabel(practice.artifactKind)}</span>
				{!practice.usedInNewReviews && <Badge variant="outline">Not used in new reviews</Badge>}
				{unavailableLabel && <Badge variant="warning">{unavailableLabel}</Badge>}
				<CatalogOriginBadge origin={practice.catalogOrigin} kind="practice" />
			</ItemDescription>
		</ItemContent>
	);
}

function PracticeDragPreview({
	practice,
	supportedModes,
}: {
	practice: Practice;
	supportedModes: readonly Practice["automatedReviewPolicy"]["automatedReview"]["mode"][];
}) {
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
				supportedModes={supportedModes}
				title={<span className="break-words">{practice.name}</span>}
			/>
		</Item>
	);
}

function CreateAreaButton({
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
						Create area
					</Button>
				}
			/>
			<PopoverContent align="end" className="w-72">
				<form
					onSubmit={async (event) => {
						event.preventDefault();
						const input = event.currentTarget.elements.namedItem("areaName") as HTMLInputElement;
						const name = input.value.trim();
						if (name && (await onCreate(name))) setOpen(false);
					}}
					className="flex items-center gap-2"
				>
					<Input
						name="areaName"
						placeholder="New area name…"
						aria-label="New area name"
						autoComplete="off"
						disabled={pending}
					/>
					<Button type="submit" size="sm" className="min-w-16" disabled={pending}>
						{pending ? "Creating…" : "Create"}
					</Button>
				</form>
			</PopoverContent>
		</Popover>
	);
}

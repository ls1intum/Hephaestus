import { Link } from "@tanstack/react-router";
import { GripVertical, MoreHorizontal } from "lucide-react";
import type { CuratedArea, CuratedPracticeSummary } from "@/api/types.gen";
import { getAreaVisual } from "@/components/admin/practices/area-visuals";
import type { WorkArtifact } from "@/components/admin/practices/constants";
import {
	type CatalogEntryMoveActions,
	type CatalogMoveActions,
	SortableCatalogTree,
	UNASSIGNED_CATALOG_BUCKET,
} from "@/components/admin/practices/SortableCatalogTree";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
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
import { Item, ItemContent, ItemDescription, ItemTitle } from "@/components/ui/item";
import { Spinner } from "@/components/ui/spinner";
import { Switch } from "@/components/ui/switch";
import { cn } from "@/lib/utils";
import { CuratedEntryBadges } from "./CuratedEntryBadges";

type TreeArea = CuratedArea & { displayOrder: number; name: string };
type TreePractice = CuratedPracticeSummary & {
	displayOrder: number;
	missingAreaSlug?: string;
	moveSourceAreaSlug?: string;
};

const ARTIFACT_LABELS: Record<WorkArtifact, string> = {
	PULL_REQUEST: "Pull or merge request",
	ISSUE: "Issue",
	CONVERSATION_THREAD: "Conversation",
};

export interface CuratedCatalogTreeProps {
	areas: readonly CuratedArea[];
	practices: readonly CuratedPracticeSummary[];
	visibleAreaSlugs: ReadonlySet<string>;
	visiblePracticeSlugs: ReadonlySet<string>;
	forceOpenAreaSlugs?: ReadonlySet<string>;
	canReorder: boolean;
	structurePending: boolean;
	pendingPracticeSlugs: ReadonlySet<string>;
	pendingAreaSlugs: ReadonlySet<string>;
	onPracticeStatusChange: (practice: CuratedPracticeSummary, offered: boolean) => void;
	onAreaStatusChange: (area: CuratedArea, offered: boolean) => void;
	onExcludePractice: (practice: CuratedPracticeSummary) => void;
	onExcludeArea: (area: CuratedArea) => void;
	onReorderAreas: (orderedSlugs: string[]) => void;
	onPlacePractice: (practiceSlug: string, areaSlug: string | null, position: number) => void;
}

export function CuratedCatalogTree({
	areas,
	practices,
	visibleAreaSlugs,
	visiblePracticeSlugs,
	forceOpenAreaSlugs,
	canReorder,
	structurePending,
	pendingPracticeSlugs,
	pendingAreaSlugs,
	onPracticeStatusChange,
	onAreaStatusChange,
	onExcludePractice,
	onExcludeArea,
	onReorderAreas,
	onPlacePractice,
}: CuratedCatalogTreeProps) {
	const knownAreas = new Set(areas.map((area) => area.slug));
	const treeAreas: TreeArea[] = areas.map((area) => ({
		...area,
		name: area.definition.name,
		displayOrder: area.position,
	}));
	const treePractices: TreePractice[] = practices.map((practice) => ({
		...practice,
		areaSlug:
			practice.areaSlug && knownAreas.has(practice.areaSlug) ? practice.areaSlug : undefined,
		displayOrder: practice.position,
		missingAreaSlug:
			practice.areaSlug && !knownAreas.has(practice.areaSlug) ? practice.areaSlug : undefined,
		moveSourceAreaSlug:
			practice.areaSlug && !knownAreas.has(practice.areaSlug) ? practice.areaSlug : undefined,
	}));
	const anyWritePending =
		structurePending || pendingAreaSlugs.size > 0 || pendingPracticeSlugs.size > 0;
	const blockedBuckets = canReorder
		? new Set<string>()
		: new Set([...areas.map((area) => area.slug), UNASSIGNED_CATALOG_BUCKET]);

	return (
		<SortableCatalogTree
			areas={treeAreas.filter((area) => visibleAreaSlugs.has(area.slug))}
			entries={treePractices}
			visibleEntrySlugs={visiblePracticeSlugs}
			forceOpenAreaSlugs={forceOpenAreaSlugs}
			areaReorderDisabled={!canReorder || anyWritePending}
			disabledAreaSlugs={pendingAreaSlugs}
			disabledEntrySlugs={pendingPracticeSlugs}
			blockedEntryOrderBuckets={blockedBuckets}
			blockedMoveDestinationSlugs={blockedBuckets}
			showEntryReorderHandles={canReorder && !anyWritePending}
			onReorderAreas={onReorderAreas}
			onPlaceEntry={onPlacePractice}
			renderAreaLeading={(area) => <AreaIcon area={area} />}
			renderAreaMeta={(area) => <CuratedEntryBadges status={area.status} kind="area" />}
			renderAreaActions={(area, move) => (
				<AreaActions
					area={area}
					move={move}
					pending={structurePending || pendingAreaSlugs.has(area.slug)}
					onStatusChange={onAreaStatusChange}
					onExclude={onExcludeArea}
				/>
			)}
			renderEntryContent={(practice) => <PracticeDetails practice={practice} />}
			renderEntryActions={(practice, move) => (
				<PracticeActions
					practice={practice}
					areas={treeAreas}
					move={move}
					pending={structurePending || pendingPracticeSlugs.has(practice.slug)}
					onStatusChange={onPracticeStatusChange}
					onExclude={onExcludePractice}
				/>
			)}
			renderEntryPreview={(practice) => <PracticeDragPreview practice={practice} />}
			getEmptyLabel={(areaSlug, total) => {
				if (total > 0) return "No matching practices.";
				return areaSlug === null ? "No unassigned practices." : "No practices in this area.";
			}}
		/>
	);
}

function AreaIcon({ area }: { area: TreeArea }) {
	const { Icon, pill } = getAreaVisual(
		area.slug,
		area.definition.name,
		area.definition.icon,
		area.definition.color,
	);
	return (
		<span
			className={cn("flex size-8 shrink-0 items-center justify-center rounded-md", pill)}
			aria-hidden
		>
			<Icon className="size-4" />
		</span>
	);
}

function AreaActions({
	area,
	move,
	pending,
	onStatusChange,
	onExclude,
}: {
	area: TreeArea;
	move: CatalogMoveActions;
	pending: boolean;
	onStatusChange: (area: CuratedArea, offered: boolean) => void;
	onExclude: (area: CuratedArea) => void;
}) {
	return (
		<>
			{pending && <Spinner className="size-4 text-muted-foreground" />}
			<Switch
				className="hidden sm:inline-flex"
				checked={area.status.offered}
				onCheckedChange={(offered) => (offered ? onStatusChange(area, true) : onExclude(area))}
				disabled={pending}
				aria-busy={pending}
				aria-label={`Include ${area.definition.name} in new workspaces`}
			/>
			<DropdownMenu>
				<DropdownMenuTrigger
					render={
						<Button
							ref={move.actionTriggerRef}
							variant="ghost"
							size="icon-sm"
							disabled={pending}
							aria-label={`More actions for ${area.definition.name}`}
						>
							<MoreHorizontal className="size-4" />
						</Button>
					}
				/>
				<DropdownMenuContent align="end">
					<DropdownMenuItem
						render={
							<Link
								from="/admin/catalog"
								to="/admin/catalog/areas/$areaSlug"
								params={{ areaSlug: area.slug }}
								search={(previous) => previous}
							/>
						}
					>
						Edit area
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
					{area.status.offered ? (
						<DropdownMenuItem variant="destructive" onClick={() => onExclude(area)}>
							Exclude from new workspaces
						</DropdownMenuItem>
					) : (
						<DropdownMenuItem onClick={() => onStatusChange(area, true)}>
							Include in new workspaces
						</DropdownMenuItem>
					)}
				</DropdownMenuContent>
			</DropdownMenu>
		</>
	);
}

function PracticeDetails({ practice }: { practice: TreePractice }) {
	const parentUnavailable =
		Boolean(practice.missingAreaSlug) || (practice.status.offered && !practice.effectivelyOffered);
	return (
		<ItemContent className="min-w-0">
			<ItemTitle className="w-full min-w-0 line-clamp-none">
				<Link
					from="/admin/catalog"
					to="/admin/catalog/practices/$practiceSlug"
					params={{ practiceSlug: practice.slug }}
					search={(previous) => previous}
					className="break-words rounded-sm hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
				>
					{practice.name}
				</Link>
			</ItemTitle>
			<ItemDescription className="flex flex-wrap items-center gap-1.5">
				<span>{ARTIFACT_LABELS[practice.artifactType]}</span>
				{parentUnavailable && (
					<Badge variant="outline">
						{practice.missingAreaSlug
							? "Area no longer exists"
							: "Excluded because its area is excluded"}
					</Badge>
				)}
				<CuratedEntryBadges status={practice.status} kind="practice" />
			</ItemDescription>
		</ItemContent>
	);
}

function PracticeActions({
	practice,
	areas,
	move,
	pending,
	onStatusChange,
	onExclude,
}: {
	practice: TreePractice;
	areas: readonly TreeArea[];
	move: CatalogEntryMoveActions;
	pending: boolean;
	onStatusChange: (practice: CuratedPracticeSummary, offered: boolean) => void;
	onExclude: (practice: CuratedPracticeSummary) => void;
}) {
	const area = practice.areaSlug
		? areas.find((candidate) => candidate.slug === practice.areaSlug)
		: undefined;
	const parentUnavailable = Boolean(practice.missingAreaSlug) || area?.status.offered === false;
	const includeLabel = practice.missingAreaSlug
		? "Move to Unassigned or an included area first"
		: parentUnavailable
			? "Include when its area is included"
			: "Include in new workspaces";
	const switchLabel = practice.missingAreaSlug
		? `${practice.name} cannot be included until it is moved out of the missing area`
		: parentUnavailable
			? practice.status.offered
				? `${practice.name} is excluded because its area is excluded`
				: `${practice.name} is excluded from new workspaces`
			: `Include ${practice.name} in new workspaces`;
	const persistedPractice = practice.missingAreaSlug
		? { ...practice, areaSlug: practice.missingAreaSlug }
		: practice;
	return (
		<>
			{pending && <Spinner className="size-4 text-muted-foreground" />}
			<Switch
				className="hidden sm:inline-flex"
				checked={practice.effectivelyOffered}
				onCheckedChange={(offered) =>
					offered ? onStatusChange(persistedPractice, true) : onExclude(persistedPractice)
				}
				disabled={pending || parentUnavailable}
				aria-busy={pending}
				aria-label={switchLabel}
			/>
			<DropdownMenu>
				<DropdownMenuTrigger
					render={
						<Button
							ref={move.actionTriggerRef}
							variant="ghost"
							size="icon-sm"
							disabled={pending}
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
								from="/admin/catalog"
								to="/admin/catalog/practices/$practiceSlug"
								params={{ practiceSlug: practice.slug }}
								search={(previous) => previous}
							/>
						}
					>
						Edit practice
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
							value={practice.missingAreaSlug ?? practice.areaSlug ?? UNASSIGNED_CATALOG_BUCKET}
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
							{areas.map((destination) => (
								<DropdownMenuRadioItem
									key={destination.slug}
									value={destination.slug}
									disabled={
										move.currentAreaSlug !== destination.slug && !move.canMoveTo(destination.slug)
									}
									closeOnClick
								>
									{destination.definition.name}
									{!destination.status.offered && " (excluded)"}
								</DropdownMenuRadioItem>
							))}
						</DropdownMenuRadioGroup>
					</DropdownMenuGroup>
					<DropdownMenuSeparator />
					{practice.status.offered ? (
						<DropdownMenuItem variant="destructive" onClick={() => onExclude(persistedPractice)}>
							Exclude from new workspaces
						</DropdownMenuItem>
					) : (
						<DropdownMenuItem
							disabled={Boolean(practice.missingAreaSlug)}
							onClick={() => onStatusChange(persistedPractice, true)}
						>
							{includeLabel}
						</DropdownMenuItem>
					)}
				</DropdownMenuContent>
			</DropdownMenu>
		</>
	);
}

function PracticeDragPreview({ practice }: { practice: TreePractice }) {
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
			<ItemContent className="min-w-0">
				<ItemTitle className="break-words line-clamp-none">{practice.name}</ItemTitle>
				<ItemDescription>{ARTIFACT_LABELS[practice.artifactType]}</ItemDescription>
			</ItemContent>
		</Item>
	);
}

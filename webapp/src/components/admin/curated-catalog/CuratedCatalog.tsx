import { Link } from "@tanstack/react-router";
import { Plus, RotateCcw, Search, Shapes } from "lucide-react";
import { useState } from "react";
import type {
	CuratedArea,
	CuratedPracticeSummary,
	CuratedCatalogSummary as Summary,
} from "@/api/types.gen";
import {
	WORK_ARTIFACT_FILTER_OPTIONS,
	type WorkArtifact,
} from "@/components/admin/practice-catalog/constants";
import {
	AlertDialog,
	AlertDialogAction,
	AlertDialogCancel,
	AlertDialogContent,
	AlertDialogDescription,
	AlertDialogFooter,
	AlertDialogHeader,
	AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Button, buttonVariants } from "@/components/ui/button";
import {
	Empty,
	EmptyContent,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
import { Input } from "@/components/ui/input";
import {
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from "@/components/ui/select";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { artifactKindLabel, isKnownArtifactKind } from "@/lib/artifact-kinds";
import { cn } from "@/lib/utils";
import { CuratedCatalogSummary } from "./CuratedCatalogSummary";
import { CuratedCatalogTree } from "./CuratedCatalogTree";
import type { CuratedCatalogSearch } from "./curated-catalog-search";

export interface CuratedCatalogProps {
	areas: readonly CuratedArea[];
	practices: readonly CuratedPracticeSummary[];
	summary: Summary;
	search: CuratedCatalogSearch;
	customOrder: boolean;
	writePending?: boolean;
	pendingPracticeSlugs?: ReadonlySet<string>;
	pendingAreaSlugs?: ReadonlySet<string>;
	onSearchChange: (search: CuratedCatalogSearch) => void;
	onPracticeStatusChange: (practice: CuratedPracticeSummary, offered: boolean) => void;
	onAreaStatusChange: (area: CuratedArea, offered: boolean) => void;
	onReorderAreas: (orderedSlugs: string[]) => void;
	onPlacePractice: (practiceSlug: string, areaSlug: string | null, position: number) => void;
	onResetOrder: () => void;
}

type StatusFilter = "OFFERED" | "NOT_OFFERED" | "ALL";
type ArtifactFilter = WorkArtifact | "ALL";

const STATUS_FILTERS = [
	{ value: "ALL", label: "All" },
	{ value: "OFFERED", label: "Included" },
	{ value: "NOT_OFFERED", label: "Excluded" },
] satisfies Array<{ value: StatusFilter; label: string }>;

function matches(haystack: (string | undefined)[], needle: string): boolean {
	return !needle || haystack.some((value) => value?.toLowerCase().includes(needle));
}

export function CuratedCatalog({
	areas,
	practices,
	summary,
	search,
	customOrder,
	writePending = false,
	pendingPracticeSlugs = new Set(),
	pendingAreaSlugs = new Set(),
	onSearchChange,
	onPracticeStatusChange,
	onAreaStatusChange,
	onReorderAreas,
	onPlacePractice,
	onResetOrder,
}: CuratedCatalogProps) {
	const [excludingPractice, setExcludingPractice] = useState<CuratedPracticeSummary | null>(null);
	const [excludingArea, setExcludingArea] = useState<CuratedArea | null>(null);
	const [resettingOrder, setResettingOrder] = useState(false);
	const query = search.q ?? "";
	const status: StatusFilter = search.status ?? "ALL";
	const artifact: ArtifactFilter = search.artifact ?? "ALL";
	const reviewOnly = search.review === true;
	const removedDefaultsToReview =
		areas.filter((area) => area.status.state === "NO_LONGER_SHIPPED" && area.status.offered)
			.length +
		practices.filter(
			(practice) => practice.status.state === "NO_LONGER_SHIPPED" && practice.effectivelyOffered,
		).length;
	const needle = query.trim().toLowerCase();
	const areaBySlug = new Map(areas.map((area) => [area.slug, area]));
	const matchesStatus = (offered: boolean) =>
		status === "ALL" || (status === "OFFERED") === offered;

	const visiblePractices = practices.filter(
		(practice) =>
			(!reviewOnly ||
				practice.status.state === "UPDATE_WAITING" ||
				(practice.status.state === "NO_LONGER_SHIPPED" && practice.effectivelyOffered)) &&
			matchesStatus(practice.effectivelyOffered) &&
			(artifact === "ALL" || practice.artifactKind === artifact) &&
			matches(
				[
					practice.name,
					practice.slug,
					practice.areaSlug ?? undefined,
					practice.areaSlug ? areaBySlug.get(practice.areaSlug)?.definition.name : undefined,
				],
				needle,
			),
	);
	const visiblePracticeSlugs = new Set(visiblePractices.map((practice) => practice.slug));
	const areasHoldingMatches = new Set(
		visiblePractices.map((practice) => practice.areaSlug).filter((slug): slug is string => !!slug),
	);
	const visibleAreas = areas.filter(
		(area) =>
			areasHoldingMatches.has(area.slug) ||
			(artifact === "ALL" &&
				(!reviewOnly ||
					area.status.state === "UPDATE_WAITING" ||
					(area.status.state === "NO_LONGER_SHIPPED" && area.status.offered)) &&
				matchesStatus(area.status.offered) &&
				matches(
					[area.definition.name, area.slug, area.definition.description ?? undefined],
					needle,
				)),
	);

	const visibleAreaSlugs = new Set(visibleAreas.map((area) => area.slug));
	const canReorder = !needle && status === "ALL" && artifact === "ALL" && !reviewOnly;
	const forcedOpenAreas = canReorder ? undefined : visibleAreaSlugs;
	const catalogIsEmpty = areas.length === 0 && practices.length === 0;
	const nothingMatches = visibleAreas.length === 0 && visiblePractices.length === 0;
	const practicesExcludedWithArea = excludingArea
		? practices.filter(
				(practice) => practice.areaSlug === excludingArea.slug && practice.status.offered,
			)
		: [];

	return (
		<>
			<div className="space-y-4">
				<CuratedCatalogSummary
					summary={summary}
					removedDefaultsToReview={removedDefaultsToReview}
					reviewing={reviewOnly}
					onReviewChanges={() => onSearchChange({ review: true })}
				/>
				<CatalogFilters search={search} onSearchChange={onSearchChange} />
				{customOrder && (
					<div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border p-3 text-sm">
						<p className="text-muted-foreground">
							This catalog keeps your custom order when Hephaestus changes.
						</p>
						<Button
							variant="outline"
							size="sm"
							disabled={writePending}
							onClick={() => setResettingOrder(true)}
						>
							<RotateCcw className="mr-1.5 size-4" aria-hidden />
							Use Hephaestus order
						</Button>
					</div>
				)}
				{!canReorder && !catalogIsEmpty && (
					<p className="text-muted-foreground text-sm">
						Clear the search and filters to reorder the catalog.
					</p>
				)}

				{catalogIsEmpty ? (
					<Empty className="min-h-56 border">
						<EmptyHeader>
							<EmptyMedia variant="icon">
								<Shapes aria-hidden />
							</EmptyMedia>
							<EmptyTitle>The catalog is empty</EmptyTitle>
							<EmptyDescription>
								Create a practice or area to build the starting configuration for new workspaces.
							</EmptyDescription>
						</EmptyHeader>
						<EmptyContent>
							<Link
								from="/admin/catalog"
								to="/admin/catalog/practices/new"
								search={(previous) => previous}
								className={cn(buttonVariants())}
							>
								<Plus className="mr-1.5 size-4" aria-hidden />
								Create practice
							</Link>
						</EmptyContent>
					</Empty>
				) : nothingMatches ? (
					<Empty className="min-h-56 border">
						<EmptyHeader>
							<EmptyMedia variant="icon">
								<Shapes aria-hidden />
							</EmptyMedia>
							<EmptyTitle>Nothing matches</EmptyTitle>
							<EmptyDescription>
								Adjust the search or filters to see more of the catalog.
							</EmptyDescription>
						</EmptyHeader>
						<EmptyContent>
							<Button variant="outline" onClick={() => onSearchChange({})}>
								Clear search and filters
							</Button>
						</EmptyContent>
					</Empty>
				) : (
					<CuratedCatalogTree
						areas={areas}
						practices={practices}
						visibleAreaSlugs={visibleAreaSlugs}
						visiblePracticeSlugs={visiblePracticeSlugs}
						forceOpenAreaSlugs={forcedOpenAreas}
						canReorder={canReorder}
						writePending={writePending}
						pendingAreaSlugs={pendingAreaSlugs}
						pendingPracticeSlugs={pendingPracticeSlugs}
						onAreaStatusChange={onAreaStatusChange}
						onPracticeStatusChange={onPracticeStatusChange}
						onExcludeArea={setExcludingArea}
						onExcludePractice={setExcludingPractice}
						onReorderAreas={onReorderAreas}
						onPlacePractice={onPlacePractice}
					/>
				)}
			</div>

			<AlertDialog open={resettingOrder} onOpenChange={setResettingOrder}>
				<AlertDialogContent>
					<AlertDialogHeader>
						<AlertDialogTitle>Use the Hephaestus order?</AlertDialogTitle>
						<AlertDialogDescription>
							Areas and practices will return to the order included with this Hephaestus version.
							Definitions and inclusion will not change.
						</AlertDialogDescription>
					</AlertDialogHeader>
					<AlertDialogFooter>
						<AlertDialogCancel>Cancel</AlertDialogCancel>
						<AlertDialogAction
							onClick={() => {
								onResetOrder();
								setResettingOrder(false);
							}}
						>
							Use Hephaestus order
						</AlertDialogAction>
					</AlertDialogFooter>
				</AlertDialogContent>
			</AlertDialog>

			<AlertDialog
				open={excludingPractice !== null}
				onOpenChange={(open) => {
					if (!open) setExcludingPractice(null);
				}}
			>
				<AlertDialogContent>
					<AlertDialogHeader>
						<AlertDialogTitle>
							Exclude “{excludingPractice?.name}” from new workspaces?
						</AlertDialogTitle>
						<AlertDialogDescription>
							{excludingPractice?.effectivelyOffered === false
								? "Its area is already excluded. This keeps the practice excluded if the area is included again. Existing workspaces will not change."
								: "New workspaces will not receive this practice. Existing workspaces will not change. You can include it again later."}
						</AlertDialogDescription>
					</AlertDialogHeader>
					<AlertDialogFooter>
						<AlertDialogCancel>Cancel</AlertDialogCancel>
						<AlertDialogAction
							onClick={() => {
								if (excludingPractice) onPracticeStatusChange(excludingPractice, false);
								setExcludingPractice(null);
							}}
						>
							Exclude practice
						</AlertDialogAction>
					</AlertDialogFooter>
				</AlertDialogContent>
			</AlertDialog>

			<AlertDialog
				open={excludingArea !== null}
				onOpenChange={(open) => {
					if (!open) setExcludingArea(null);
				}}
			>
				<AlertDialogContent>
					<AlertDialogHeader>
						<AlertDialogTitle>
							Exclude “{excludingArea?.definition.name}” from new workspaces?
						</AlertDialogTitle>
						<AlertDialogDescription>
							{practicesExcludedWithArea.length === 0
								? "New workspaces will not receive this area. No additional practices will be excluded. Existing workspaces will not change."
								: `New workspaces will not receive this area. This also excludes ${practicesExcludedWithArea.length} currently included ${
										practicesExcludedWithArea.length === 1 ? "practice" : "practices"
									}. Existing workspaces will not change.`}
						</AlertDialogDescription>
					</AlertDialogHeader>
					{practicesExcludedWithArea.length > 0 && (
						<ul
							aria-label="Practices this also excludes"
							className="max-h-40 list-disc overflow-y-auto pl-5 text-muted-foreground text-sm"
						>
							{practicesExcludedWithArea.map((practice) => (
								<li key={practice.slug}>{practice.name}</li>
							))}
						</ul>
					)}
					<AlertDialogFooter>
						<AlertDialogCancel>Cancel</AlertDialogCancel>
						<AlertDialogAction
							onClick={() => {
								if (excludingArea) onAreaStatusChange(excludingArea, false);
								setExcludingArea(null);
							}}
						>
							Exclude area
						</AlertDialogAction>
					</AlertDialogFooter>
				</AlertDialogContent>
			</AlertDialog>
		</>
	);
}

function CatalogFilters({
	search,
	onSearchChange,
}: {
	search: CuratedCatalogSearch;
	onSearchChange: (search: CuratedCatalogSearch) => void;
}) {
	const status: StatusFilter = search.status ?? "ALL";
	const artifact: ArtifactFilter = search.artifact ?? "ALL";
	return (
		<div className="grid gap-2 sm:grid-cols-2 lg:flex lg:items-center">
			{search.review && (
				<Button
					variant="secondary"
					size="sm"
					onClick={() => onSearchChange({ ...search, review: undefined })}
				>
					Show all entries
				</Button>
			)}
			<div className="relative sm:col-span-2 lg:w-64">
				<Search
					className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground"
					aria-hidden
				/>
				<Input
					type="search"
					value={search.q ?? ""}
					onChange={(event) => onSearchChange({ ...search, q: event.target.value || undefined })}
					placeholder="Search the catalog"
					aria-label="Search the catalog"
					className="pl-9"
				/>
			</div>
			<Select
				items={STATUS_FILTERS}
				value={status}
				onValueChange={(value) =>
					value &&
					onSearchChange({
						...search,
						status: value === "ALL" ? undefined : (value as "OFFERED" | "NOT_OFFERED"),
					})
				}
			>
				<SelectTrigger
					className="w-full lg:hidden"
					aria-label="Filter by inclusion in new workspaces"
				>
					<SelectValue />
				</SelectTrigger>
				<SelectContent>
					{STATUS_FILTERS.map((filter) => (
						<SelectItem key={filter.value} value={filter.value}>
							{filter.label}
						</SelectItem>
					))}
				</SelectContent>
			</Select>
			{/*
			 * toolbar, not the default group: ToggleGroup emits aria-orientation, which ARIA allows on
			 * toolbar but not on group, and these buttons already have the roving arrow-key focus a
			 * toolbar implies. radiogroup would be worse — the items are aria-pressed, not radios.
			 */}
			<ToggleGroup
				role="toolbar"
				value={[status]}
				onValueChange={(value) =>
					value[0] &&
					onSearchChange({
						...search,
						status: value[0] === "ALL" ? undefined : (value[0] as "OFFERED" | "NOT_OFFERED"),
					})
				}
				variant="outline"
				size="sm"
				aria-label="Filter by inclusion in new workspaces"
				className="hidden lg:flex"
			>
				{STATUS_FILTERS.map((filter) => (
					<ToggleGroupItem key={filter.value} value={filter.value} className="min-w-0">
						{filter.label}
					</ToggleGroupItem>
				))}
			</ToggleGroup>
			<Select
				value={artifact}
				onValueChange={(value) =>
					onSearchChange({
						...search,
						artifact: value === "ALL" || !isKnownArtifactKind(value) ? undefined : value,
					})
				}
			>
				<SelectTrigger className="w-full lg:w-52" aria-label="Filter by work type">
					<SelectValue>
						{artifact === "ALL" ? "All work types" : artifactKindLabel(artifact)}
					</SelectValue>
				</SelectTrigger>
				<SelectContent>
					<SelectItem value="ALL">All work types</SelectItem>
					{WORK_ARTIFACT_FILTER_OPTIONS.map(({ value, label }) => (
						<SelectItem key={value} value={value}>
							{label}
						</SelectItem>
					))}
				</SelectContent>
			</Select>
		</div>
	);
}

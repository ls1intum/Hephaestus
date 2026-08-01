import { Link } from "@tanstack/react-router";
import { Plus, Search, Shapes } from "lucide-react";
import { useState } from "react";
import type {
	CuratedArea,
	CuratedPracticeSummary,
	CuratedCatalogSummary as Summary,
} from "@/api/types.gen";
import type { WorkArtifact } from "@/components/admin/practices/constants";
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
import { cn } from "@/lib/utils";
import { CuratedCatalogSummary } from "./CuratedCatalogSummary";
import { CuratedCatalogTree } from "./CuratedCatalogTree";
import type { CuratedCatalogSearch } from "./curated-catalog-search";

export interface CuratedCatalogProps {
	areas: readonly CuratedArea[];
	practices: readonly CuratedPracticeSummary[];
	summary: Summary;
	search: CuratedCatalogSearch;
	structurePending?: boolean;
	pendingPracticeSlugs?: ReadonlySet<string>;
	pendingAreaSlugs?: ReadonlySet<string>;
	onSearchChange: (search: CuratedCatalogSearch) => void;
	onPracticeStatusChange: (practice: CuratedPracticeSummary, offered: boolean) => void;
	onAreaStatusChange: (area: CuratedArea, offered: boolean) => void;
	onReorderAreas: (orderedSlugs: string[]) => void;
	onPlacePractice: (practiceSlug: string, areaSlug: string | null, position: number) => void;
}

type StatusFilter = "OFFERED" | "NOT_OFFERED" | "ALL";
type ArtifactFilter = WorkArtifact | "ALL";

const ARTIFACT_LABELS: Record<WorkArtifact, string> = {
	PULL_REQUEST: "Pull or merge request",
	ISSUE: "Issue",
	CONVERSATION_THREAD: "Conversation",
};

const STATUS_FILTERS = [
	{ value: "ALL", label: "All" },
	{ value: "OFFERED", label: "Offered" },
	{ value: "NOT_OFFERED", label: "Not offered" },
] satisfies Array<{ value: StatusFilter; label: string }>;

function matches(haystack: (string | undefined)[], needle: string): boolean {
	return !needle || haystack.some((value) => value?.toLowerCase().includes(needle));
}

export function CuratedCatalog({
	areas,
	practices,
	summary,
	search,
	structurePending = false,
	pendingPracticeSlugs = new Set(),
	pendingAreaSlugs = new Set(),
	onSearchChange,
	onPracticeStatusChange,
	onAreaStatusChange,
	onReorderAreas,
	onPlacePractice,
}: CuratedCatalogProps) {
	const [retiringPractice, setRetiringPractice] = useState<CuratedPracticeSummary | null>(null);
	const [retiringArea, setRetiringArea] = useState<CuratedArea | null>(null);
	const query = search.q ?? "";
	const status: StatusFilter = search.status ?? "ALL";
	const artifact: ArtifactFilter = search.artifact ?? "ALL";
	const needle = query.trim().toLowerCase();
	const areaBySlug = new Map(areas.map((area) => [area.slug, area]));
	const matchesStatus = (offered: boolean) =>
		status === "ALL" || (status === "OFFERED") === offered;

	const visiblePractices = practices.filter(
		(practice) =>
			matchesStatus(practice.effectivelyOffered) &&
			(artifact === "ALL" || practice.artifactType === artifact) &&
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
				matchesStatus(area.status.offered) &&
				matches(
					[area.definition.name, area.slug, area.definition.description ?? undefined],
					needle,
				)),
	);

	const visibleAreaSlugs = new Set(visibleAreas.map((area) => area.slug));
	const canReorder = !needle && status === "ALL" && artifact === "ALL";
	const forcedOpenAreas = canReorder ? undefined : visibleAreaSlugs;
	const catalogIsEmpty = areas.length === 0 && practices.length === 0;
	const nothingMatches = visibleAreas.length === 0 && visiblePractices.length === 0;
	const areaBeingRetiredHolds = retiringArea
		? practices.filter(
				(practice) => practice.areaSlug === retiringArea.slug && practice.status.offered,
			)
		: [];

	return (
		<>
			<div className="space-y-4">
				<CuratedCatalogSummary summary={summary} />
				<CatalogFilters search={search} onSearchChange={onSearchChange} />
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
							<EmptyTitle>No practices in the catalog</EmptyTitle>
							<EmptyDescription>
								Add a practice. Every workspace created from then on receives it.
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
								Add practice
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
						structurePending={structurePending}
						pendingAreaSlugs={pendingAreaSlugs}
						pendingPracticeSlugs={pendingPracticeSlugs}
						onAreaStatusChange={onAreaStatusChange}
						onPracticeStatusChange={onPracticeStatusChange}
						onRetireArea={setRetiringArea}
						onRetirePractice={setRetiringPractice}
						onReorderAreas={onReorderAreas}
						onPlacePractice={onPlacePractice}
					/>
				)}
			</div>

			<AlertDialog
				open={retiringPractice !== null}
				onOpenChange={(open) => {
					if (!open) setRetiringPractice(null);
				}}
			>
				<AlertDialogContent>
					<AlertDialogHeader>
						<AlertDialogTitle>Retire “{retiringPractice?.name}”?</AlertDialogTitle>
						<AlertDialogDescription>
							{retiringPractice?.effectivelyOffered === false
								? "It is already unavailable because its area is not offered. Retiring it keeps it unavailable if the area becomes available again. Workspaces that already have it keep it unchanged."
								: "New workspaces will not receive it. Workspaces that already have it keep it unchanged. You can offer it again later."}
						</AlertDialogDescription>
					</AlertDialogHeader>
					<AlertDialogFooter>
						<AlertDialogCancel>
							{retiringPractice?.effectivelyOffered === false ? "Leave as is" : "Keep offering it"}
						</AlertDialogCancel>
						<AlertDialogAction
							onClick={() => {
								if (retiringPractice) onPracticeStatusChange(retiringPractice, false);
								setRetiringPractice(null);
							}}
						>
							Retire practice
						</AlertDialogAction>
					</AlertDialogFooter>
				</AlertDialogContent>
			</AlertDialog>

			<AlertDialog
				open={retiringArea !== null}
				onOpenChange={(open) => {
					if (!open) setRetiringArea(null);
				}}
			>
				<AlertDialogContent>
					<AlertDialogHeader>
						<AlertDialogTitle>Retire “{retiringArea?.definition.name}”?</AlertDialogTitle>
						<AlertDialogDescription>
							{areaBeingRetiredHolds.length === 0
								? "New workspaces will not receive this area. No practice is filed under it, so nothing else changes. Workspaces that already have it keep it, unchanged."
								: `New workspaces will receive neither this area nor the ${areaBeingRetiredHolds.length} ${
										areaBeingRetiredHolds.length === 1 ? "practice" : "practices"
									} filed under it. Workspaces that already have them keep them, unchanged.`}
						</AlertDialogDescription>
					</AlertDialogHeader>
					{areaBeingRetiredHolds.length > 0 && (
						<ul
							aria-label="Practices filed under this area"
							className="max-h-40 list-disc overflow-y-auto pl-5 text-muted-foreground text-sm"
						>
							{areaBeingRetiredHolds.map((practice) => (
								<li key={practice.slug}>{practice.name}</li>
							))}
						</ul>
					)}
					<AlertDialogFooter>
						<AlertDialogCancel>Keep offering it</AlertDialogCancel>
						<AlertDialogAction
							onClick={() => {
								if (retiringArea) onAreaStatusChange(retiringArea, false);
								setRetiringArea(null);
							}}
						>
							Retire area
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
				<SelectTrigger className="w-full lg:hidden" aria-label="Filter by availability">
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
				aria-label="Filter by availability"
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
						artifact: value === "ALL" ? undefined : (value as WorkArtifact),
					})
				}
			>
				<SelectTrigger className="w-full lg:w-52" aria-label="Filter by work type">
					<SelectValue>
						{artifact === "ALL" ? "All work types" : ARTIFACT_LABELS[artifact]}
					</SelectValue>
				</SelectTrigger>
				<SelectContent>
					<SelectItem value="ALL">All work types</SelectItem>
					{Object.entries(ARTIFACT_LABELS).map(([value, label]) => (
						<SelectItem key={value} value={value}>
							{label}
						</SelectItem>
					))}
				</SelectContent>
			</Select>
		</div>
	);
}

import { Link } from "@tanstack/react-router";
import { Archive, Pencil, RotateCcw, Search, Shapes } from "lucide-react";
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
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
	Empty,
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
import { CuratedCatalogSummary } from "./CuratedCatalogSummary";
import { CuratedEntryBadges } from "./CuratedEntryBadges";
import type { CuratedCatalogSearch } from "./curated-catalog-search";

export interface CuratedCatalogProps {
	areas: readonly CuratedArea[];
	practices: readonly CuratedPracticeSummary[];
	summary: Summary;
	search: CuratedCatalogSearch;
	pendingSlugs?: ReadonlySet<string>;
	/** Practices an area still holds, so retiring it can say what it withholds. */
	practicesInArea: (areaSlug: string) => readonly string[];
	onSearchChange: (search: CuratedCatalogSearch) => void;
	onPracticeStatusChange: (practice: CuratedPracticeSummary, offered: boolean) => void;
	onAreaStatusChange: (area: CuratedArea, offered: boolean) => void;
}

type StatusFilter = "OFFERED" | "NOT_OFFERED" | "ALL";
type ArtifactFilter = WorkArtifact | "ALL";

const ARTIFACT_LABELS: Record<WorkArtifact, string> = {
	PULL_REQUEST: "Pull or merge request",
	ISSUE: "Issue",
	CONVERSATION_THREAD: "Conversation",
};

const UNFILED = "__unfiled__";

function matches(haystack: (string | undefined)[], needle: string): boolean {
	return !needle || haystack.some((value) => value?.toLowerCase().includes(needle));
}

export function CuratedCatalog({
	areas,
	practices,
	summary,
	search,
	pendingSlugs,
	practicesInArea,
	onSearchChange,
	onPracticeStatusChange,
	onAreaStatusChange,
}: CuratedCatalogProps) {
	const [retiringPractice, setRetiringPractice] = useState<CuratedPracticeSummary | null>(null);
	const [retiringArea, setRetiringArea] = useState<CuratedArea | null>(null);
	const query = search.q ?? "";
	const status: StatusFilter = search.status ?? "OFFERED";
	const artifact: ArtifactFilter = search.artifact ?? "ALL";
	const needle = query.trim().toLowerCase();

	const visibleAreas = areas.filter(
		(area) =>
			(status === "ALL" || (status === "OFFERED") === area.status.offered) &&
			matches([area.definition.name, area.slug, area.definition.description ?? undefined], needle),
	);
	const visiblePractices = practices.filter(
		(practice) =>
			(status === "ALL" || (status === "OFFERED") === practice.status.offered) &&
			(artifact === "ALL" || practice.artifactType === artifact) &&
			matches(
				[
					practice.name,
					practice.slug,
					practice.areaSlug ?? undefined,
					areas.find((area) => area.slug === practice.areaSlug)?.definition.name,
				],
				needle,
			),
	);

	const grouped = visiblePractices.reduce((result, practice) => {
		const key = practice.areaSlug ?? UNFILED;
		result.set(key, [...(result.get(key) ?? []), practice]);
		return result;
	}, new Map<string, CuratedPracticeSummary[]>());
	const orderedGroups = [...grouped.entries()].sort(([left], [right]) => {
		if (left === UNFILED) return 1;
		if (right === UNFILED) return -1;
		const leftArea = areas.find((area) => area.slug === left);
		const rightArea = areas.find((area) => area.slug === right);
		return (
			(leftArea?.definition.displayOrder ?? Number.MAX_SAFE_INTEGER) -
				(rightArea?.definition.displayOrder ?? Number.MAX_SAFE_INTEGER) ||
			(leftArea?.definition.name ?? left).localeCompare(rightArea?.definition.name ?? right)
		);
	});

	const areaBeingRetiredHolds = retiringArea ? practicesInArea(retiringArea.slug) : [];

	return (
		<>
			<div className="space-y-8">
				<CuratedCatalogSummary summary={summary} />

				<div className="grid gap-3 rounded-lg border bg-card p-4 md:grid-cols-[minmax(16rem,1fr)_13rem_13rem]">
					<div className="relative">
						<Search
							className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground"
							aria-hidden
						/>
						<Input
							type="search"
							value={query}
							onChange={(event) =>
								onSearchChange({ ...search, q: event.target.value || undefined })
							}
							placeholder="Search the catalog"
							aria-label="Search the catalog"
							className="pl-9"
						/>
					</div>
					<Select
						value={status}
						onValueChange={(value) =>
							onSearchChange({
								...search,
								status: value === "OFFERED" ? undefined : (value as "NOT_OFFERED" | "ALL"),
							})
						}
					>
						<SelectTrigger aria-label="Filter by status">
							<SelectValue>
								{status === "ALL"
									? "All statuses"
									: status === "OFFERED"
										? "Offered"
										: "Not offered"}
							</SelectValue>
						</SelectTrigger>
						<SelectContent>
							<SelectItem value="OFFERED">Offered</SelectItem>
							<SelectItem value="NOT_OFFERED">Not offered</SelectItem>
							<SelectItem value="ALL">All statuses</SelectItem>
						</SelectContent>
					</Select>
					<Select
						value={artifact}
						onValueChange={(value) =>
							onSearchChange({
								...search,
								artifact: value === "ALL" ? undefined : (value as WorkArtifact),
							})
						}
					>
						<SelectTrigger aria-label="Filter by work type">
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

				<section className="space-y-3" aria-labelledby="curated-areas">
					<div className="flex items-center gap-2">
						<h2 id="curated-areas" className="font-semibold">
							Areas
						</h2>
						<Badge variant="secondary">{visibleAreas.length}</Badge>
					</div>
					{visibleAreas.length === 0 ? (
						<p className="text-muted-foreground text-sm">No areas match the search.</p>
					) : (
						<div className="divide-y rounded-lg border bg-card">
							{visibleAreas.map((area) => (
								<article
									key={area.slug}
									className="flex flex-col gap-4 p-4 sm:flex-row sm:items-center sm:justify-between"
								>
									<div className="min-w-0 space-y-2">
										<div className="flex flex-wrap items-center gap-2">
											<h3 className="font-medium">{area.definition.name}</h3>
											<CuratedEntryBadges status={area.status} kind="area" />
										</div>
										<p className="break-all text-muted-foreground text-xs">{area.slug}</p>
										<div className="flex flex-wrap gap-x-3 gap-y-1 text-muted-foreground text-xs">
											<span>
												{practicesInArea(area.slug).length}{" "}
												{practicesInArea(area.slug).length === 1 ? "practice" : "practices"}
											</span>
										</div>
									</div>
									<div className="flex shrink-0 flex-wrap gap-2">
										<Button
											variant="outline"
											nativeButton={false}
											disabled={pendingSlugs?.has(area.slug) ?? false}
											render={
												<Link
													from="/admin/catalog"
													to="/admin/catalog/areas/$areaSlug"
													params={{ areaSlug: area.slug }}
													search={(previous) => previous}
												/>
											}
											aria-label={`Edit ${area.definition.name}`}
										>
											<Pencil className="size-4" aria-hidden />
											Edit
										</Button>
										{area.status.offered ? (
											<Button
												type="button"
												variant="outline"
												disabled={pendingSlugs?.has(area.slug) ?? false}
												onClick={() => setRetiringArea(area)}
												aria-label={`Retire ${area.definition.name}`}
											>
												<Archive className="size-4" aria-hidden />
												Retire
											</Button>
										) : (
											<Button
												type="button"
												variant="outline"
												disabled={pendingSlugs?.has(area.slug) ?? false}
												onClick={() => onAreaStatusChange(area, true)}
												aria-label={`Offer ${area.definition.name} again`}
											>
												<RotateCcw className="size-4" aria-hidden />
												Offer again
											</Button>
										)}
									</div>
								</article>
							))}
						</div>
					)}
				</section>

				{orderedGroups.length === 0 ? (
					<Empty className="min-h-56 border">
						<EmptyHeader>
							<EmptyMedia variant="icon">
								<Shapes aria-hidden />
							</EmptyMedia>
							<EmptyTitle>
								{practices.length === 0 ? "No practices in the catalog" : "No matching practices"}
							</EmptyTitle>
							<EmptyDescription>
								{practices.length === 0
									? "Add a practice to offer it to every workspace created from now on."
									: "Adjust the search or filters to see more of the catalog."}
							</EmptyDescription>
						</EmptyHeader>
					</Empty>
				) : (
					orderedGroups.map(([areaSlug, areaPractices]) => (
						<section key={areaSlug} className="space-y-3" aria-labelledby={`area-${areaSlug}`}>
							<div className="flex items-center gap-2">
								<h2 id={`area-${areaSlug}`} className="font-semibold">
									{areaSlug === UNFILED
										? "Unfiled"
										: (areas.find((area) => area.slug === areaSlug)?.definition.name ?? areaSlug)}
								</h2>
								<Badge variant="secondary">{areaPractices.length}</Badge>
							</div>
							<div className="divide-y rounded-lg border bg-card">
								{areaPractices.map((practice) => (
									<article
										key={practice.slug}
										className="flex flex-col gap-4 p-4 sm:flex-row sm:items-center sm:justify-between"
									>
										<div className="min-w-0 space-y-2">
											<div className="flex flex-wrap items-center gap-2">
												<h3 className="font-medium">{practice.name}</h3>
												<CuratedEntryBadges status={practice.status} kind="practice" />
											</div>
											<p className="break-all text-muted-foreground text-xs">{practice.slug}</p>
											<div className="flex flex-wrap gap-x-3 gap-y-1 text-muted-foreground text-xs">
												<span>{ARTIFACT_LABELS[practice.artifactType]}</span>
											</div>
										</div>
										<div className="flex shrink-0 flex-wrap gap-2">
											<Button
												variant="outline"
												nativeButton={false}
												disabled={pendingSlugs?.has(practice.slug) ?? false}
												render={
													<Link
														from="/admin/catalog"
														to="/admin/catalog/practices/$practiceSlug"
														params={{ practiceSlug: practice.slug }}
														search={(previous) => previous}
													/>
												}
												aria-label={`Edit ${practice.name}`}
											>
												<Pencil className="size-4" aria-hidden />
												Edit
											</Button>
											{practice.status.offered ? (
												<Button
													type="button"
													variant="outline"
													disabled={pendingSlugs?.has(practice.slug) ?? false}
													onClick={() => setRetiringPractice(practice)}
													aria-label={`Retire ${practice.name}`}
												>
													<Archive className="size-4" aria-hidden />
													Retire
												</Button>
											) : (
												<Button
													type="button"
													variant="outline"
													disabled={pendingSlugs?.has(practice.slug) ?? false}
													onClick={() => onPracticeStatusChange(practice, true)}
													aria-label={`Offer ${practice.name} again`}
												>
													<RotateCcw className="size-4" aria-hidden />
													Offer again
												</Button>
											)}
										</div>
									</article>
								))}
							</div>
						</section>
					))
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
						<AlertDialogTitle>Stop offering “{retiringPractice?.name}”?</AlertDialogTitle>
						<AlertDialogDescription>
							New workspaces will not receive it. Workspaces that already have it keep it,
							unchanged.
						</AlertDialogDescription>
					</AlertDialogHeader>
					<AlertDialogFooter>
						<AlertDialogCancel>Keep offering it</AlertDialogCancel>
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
						<AlertDialogTitle>Stop offering “{retiringArea?.definition.name}”?</AlertDialogTitle>
						<AlertDialogDescription>
							{areaBeingRetiredHolds.length === 0
								? "No practices are filed under this area. Workspaces that already have it keep it, unchanged."
								: `New workspaces will receive neither this area nor the ${areaBeingRetiredHolds.length} ${
										areaBeingRetiredHolds.length === 1 ? "practice" : "practices"
									} filed under it. Workspaces that already have them keep them, unchanged.`}
						</AlertDialogDescription>
					</AlertDialogHeader>
					{areaBeingRetiredHolds.length > 0 && (
						<ul className="max-h-40 overflow-y-auto text-muted-foreground text-sm">
							{areaBeingRetiredHolds.map((slug) => (
								<li key={slug}>{slug}</li>
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

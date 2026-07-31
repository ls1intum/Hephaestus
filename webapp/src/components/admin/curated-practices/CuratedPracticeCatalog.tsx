import { Link } from "@tanstack/react-router";
import { Archive, Pencil, RotateCcw, Search, Shapes } from "lucide-react";
import { useState } from "react";
import type { CuratedPracticeSummary } from "@/api/types.gen";
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
import type { CuratedCatalogSearch } from "./curated-catalog-search";

export type CuratedPracticeStatus = CuratedPracticeSummary["status"];
export type CuratedPracticeSourceKind = CuratedPracticeSummary["sourceKind"];
export type CuratedPracticeSyncStatus = CuratedPracticeSummary["syncStatus"];

export interface CuratedPracticeCatalogItem {
	slug: string;
	name: string;
	artifactType: WorkArtifact;
	areaSlug?: string;
	revisionNumber: number;
	revisionCreatedAt: string | Date;
	version: number;
	status: CuratedPracticeStatus;
	sourceKind: CuratedPracticeSourceKind;
	syncStatus: CuratedPracticeSyncStatus;
	latestBundledCatalogRevision?: number | null;
}

export interface CuratedPracticeCatalogProps {
	areas: readonly { slug: string; name: string; displayOrder: number }[];
	practices: readonly CuratedPracticeCatalogItem[];
	search: CuratedCatalogSearch;
	pendingSlugs?: ReadonlySet<string>;
	onSearchChange: (search: CuratedCatalogSearch) => void;
	onStatusChange: (practice: CuratedPracticeCatalogItem, status: CuratedPracticeStatus) => void;
}

type StatusFilter = CuratedPracticeStatus | "ALL";
type ArtifactFilter = WorkArtifact | "ALL";

const ARTIFACT_LABELS: Record<WorkArtifact, string> = {
	PULL_REQUEST: "Pull or merge request",
	ISSUE: "Issue",
	CONVERSATION_THREAD: "Conversation",
};

function SourceBadges({ practice }: { practice: CuratedPracticeCatalogItem }) {
	if (practice.syncStatus === "INSTANCE") {
		return <Badge variant="outline">Instance-created</Badge>;
	}
	if (practice.syncStatus === "OVERRIDDEN") {
		return <Badge variant="secondary">Instance override</Badge>;
	}
	if (practice.syncStatus === "UPDATE_AVAILABLE") {
		return (
			<>
				<Badge variant="secondary">Instance override</Badge>
				<Badge variant="warning">Hephaestus update available</Badge>
			</>
		);
	}
	if (practice.syncStatus === "SOURCE_REMOVED") {
		return <Badge variant="warning">No longer shipped by Hephaestus</Badge>;
	}
	return <Badge variant="outline">Hephaestus managed</Badge>;
}

function areaLabel(slug: string): string {
	return slug
		.split("-")
		.map((part) => part.charAt(0).toUpperCase() + part.slice(1))
		.join(" ");
}

function formatRevisionDate(value: string | Date): string {
	return new Intl.DateTimeFormat(undefined, {
		year: "numeric",
		month: "short",
		day: "numeric",
	}).format(new Date(value));
}

export function CuratedPracticeCatalog({
	areas,
	practices,
	search,
	pendingSlugs,
	onSearchChange,
	onStatusChange,
}: CuratedPracticeCatalogProps) {
	const [retiring, setRetiring] = useState<CuratedPracticeCatalogItem | null>(null);
	const query = search.q ?? "";
	const status: StatusFilter = search.status ?? "AVAILABLE";
	const artifact: ArtifactFilter = search.artifact ?? "ALL";

	const normalizedQuery = query.trim().toLowerCase();
	const filtered = practices.filter(
		(practice) =>
			(status === "ALL" || practice.status === status) &&
			(artifact === "ALL" || practice.artifactType === artifact) &&
			(!normalizedQuery ||
				practice.name.toLowerCase().includes(normalizedQuery) ||
				practice.slug.toLowerCase().includes(normalizedQuery) ||
				practice.areaSlug?.toLowerCase().includes(normalizedQuery) ||
				areas
					.find((area) => area.slug === practice.areaSlug)
					?.name.toLowerCase()
					.includes(normalizedQuery)),
	);

	const groups = filtered.reduce((result, practice) => {
		const key = practice.areaSlug ?? "__unassigned__";
		const group = result.get(key) ?? [];
		group.push(practice);
		result.set(key, group);
		return result;
	}, new Map<string, CuratedPracticeCatalogItem[]>());
	const orderedGroups = [...groups.entries()].sort(([left], [right]) => {
		if (left === "__unassigned__") return 1;
		if (right === "__unassigned__") return -1;
		const leftArea = areas.find((area) => area.slug === left);
		const rightArea = areas.find((area) => area.slug === right);
		return (
			(leftArea?.displayOrder ?? Number.MAX_SAFE_INTEGER) -
				(rightArea?.displayOrder ?? Number.MAX_SAFE_INTEGER) ||
			(leftArea?.name ?? left).localeCompare(rightArea?.name ?? right)
		);
	});

	return (
		<>
			<div className="space-y-6">
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
							placeholder="Search practices"
							aria-label="Search practices"
							className="pl-9"
						/>
					</div>
					<Select
						value={status}
						onValueChange={(value) =>
							onSearchChange({
								...search,
								status: value === "AVAILABLE" ? undefined : (value as "RETIRED" | "ALL"),
							})
						}
					>
						<SelectTrigger aria-label="Filter by status">
							<SelectValue>
								{status === "ALL"
									? "All statuses"
									: status === "AVAILABLE"
										? "Available"
										: "Retired"}
							</SelectValue>
						</SelectTrigger>
						<SelectContent>
							<SelectItem value="AVAILABLE">Available</SelectItem>
							<SelectItem value="RETIRED">Retired</SelectItem>
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

				{orderedGroups.length === 0 ? (
					<Empty className="min-h-56 border">
						<EmptyHeader>
							<EmptyMedia variant="icon">
								<Shapes aria-hidden />
							</EmptyMedia>
							<EmptyTitle>
								{practices.length === 0 ? "No curated practices yet" : "No matching practices"}
							</EmptyTitle>
							<EmptyDescription>
								{practices.length === 0
									? "Create a practice to make it available to workspace administrators."
									: "Adjust the search or filters to see more of the catalog."}
							</EmptyDescription>
						</EmptyHeader>
					</Empty>
				) : (
					orderedGroups.map(([areaSlug, areaPractices]) => (
						<section key={areaSlug} className="space-y-3" aria-labelledby={`area-${areaSlug}`}>
							<div className="flex items-center gap-2">
								<h2 id={`area-${areaSlug}`} className="font-semibold">
									{areaSlug === "__unassigned__"
										? "Unassigned"
										: (areas.find((area) => area.slug === areaSlug)?.name ?? areaLabel(areaSlug))}
								</h2>
								<Badge variant="secondary">{areaPractices.length}</Badge>
							</div>
							<div className="divide-y rounded-lg border bg-card">
								{areaPractices.map((practice) => {
									const isPending = pendingSlugs?.has(practice.slug) ?? false;
									return (
										<article
											key={practice.slug}
											className="flex flex-col gap-4 p-4 sm:flex-row sm:items-center sm:justify-between"
										>
											<div className="min-w-0 space-y-2">
												<div className="flex flex-wrap items-center gap-2">
													<h3 className="font-medium">{practice.name}</h3>
													<Badge
														variant={practice.status === "AVAILABLE" ? "success" : "secondary"}
													>
														{practice.status === "AVAILABLE" ? "Available" : "Retired"}
													</Badge>
													<SourceBadges practice={practice} />
												</div>
												<p className="break-all text-xs text-muted-foreground">{practice.slug}</p>
												<div className="flex flex-wrap gap-x-3 gap-y-1 text-xs text-muted-foreground">
													<span>{ARTIFACT_LABELS[practice.artifactType]}</span>
													<span>Revision {practice.revisionNumber}</span>
													<time dateTime={new Date(practice.revisionCreatedAt).toISOString()}>
														{formatRevisionDate(practice.revisionCreatedAt)}
													</time>
												</div>
											</div>
											<div className="flex shrink-0 flex-wrap gap-2">
												<Button
													variant="outline"
													nativeButton={false}
													disabled={isPending}
													render={
														<Link
															from="/admin/catalog"
															to="/admin/catalog/$practiceSlug"
															params={{ practiceSlug: practice.slug }}
															search={(previous) => previous}
														/>
													}
													aria-label={`Edit ${practice.name}`}
												>
													<Pencil className="size-4" aria-hidden />
													Edit
												</Button>
												{practice.status === "AVAILABLE" ? (
													<Button
														type="button"
														variant="outline"
														disabled={isPending}
														onClick={() => setRetiring(practice)}
														aria-label={`Retire ${practice.name}`}
													>
														<Archive className="size-4" aria-hidden />
														Retire
													</Button>
												) : (
													<Button
														type="button"
														variant="outline"
														disabled={isPending}
														onClick={() => onStatusChange(practice, "AVAILABLE")}
														aria-label={`Restore ${practice.name}`}
													>
														<RotateCcw className="size-4" aria-hidden />
														Restore
													</Button>
												)}
											</div>
										</article>
									);
								})}
							</div>
						</section>
					))
				)}
			</div>

			<AlertDialog
				open={retiring !== null}
				onOpenChange={(open) => {
					if (!open) setRetiring(null);
				}}
			>
				<AlertDialogContent>
					<AlertDialogHeader>
						<AlertDialogTitle>Retire “{retiring?.name}”?</AlertDialogTitle>
						<AlertDialogDescription>
							The practice will no longer be offered for adoption. Existing workspace copies are
							unaffected.
						</AlertDialogDescription>
					</AlertDialogHeader>
					<AlertDialogFooter>
						<AlertDialogCancel>Keep available</AlertDialogCancel>
						<AlertDialogAction
							disabled={retiring ? pendingSlugs?.has(retiring.slug) : false}
							onClick={() => {
								if (!retiring) return;
								onStatusChange(retiring, "RETIRED");
								setRetiring(null);
							}}
						>
							Retire practice
						</AlertDialogAction>
					</AlertDialogFooter>
				</AlertDialogContent>
			</AlertDialog>
		</>
	);
}

import { Link } from "@tanstack/react-router";
import { MoreHorizontal, Plus, Search, Shapes } from "lucide-react";
import { useState } from "react";
import type {
	CuratedArea,
	CuratedPracticeSummary,
	CuratedCatalogSummary as Summary,
} from "@/api/types.gen";
import { getAreaVisual } from "@/components/admin/practices/area-visuals";
import type { WorkArtifact } from "@/components/admin/practices/constants";
import {
	Accordion,
	AccordionContent,
	AccordionItem,
	AccordionTrigger,
} from "@/components/ui/accordion";
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
import { Button, buttonVariants } from "@/components/ui/button";
import {
	DropdownMenu,
	DropdownMenuContent,
	DropdownMenuItem,
	DropdownMenuSeparator,
	DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
	Empty,
	EmptyContent,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
import { Input } from "@/components/ui/input";
import { ItemGroup } from "@/components/ui/item";
import {
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from "@/components/ui/select";
import { Spinner } from "@/components/ui/spinner";
import { Switch } from "@/components/ui/switch";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { cn } from "@/lib/utils";
import { CuratedCatalogSummary } from "./CuratedCatalogSummary";
import { CuratedEntryBadges } from "./CuratedEntryBadges";
import { CuratedEntryRow } from "./CuratedEntryRow";
import type { CuratedCatalogSearch } from "./curated-catalog-search";

export interface CuratedCatalogProps {
	areas: readonly CuratedArea[];
	practices: readonly CuratedPracticeSummary[];
	summary: Summary;
	search: CuratedCatalogSearch;
	pendingPracticeSlugs?: ReadonlySet<string>;
	pendingAreaSlugs?: ReadonlySet<string>;
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

const STATUS_FILTERS = [
	{ value: "OFFERED", label: "Offered" },
	{ value: "NOT_OFFERED", label: "Not offered" },
	{ value: "ALL", label: "All" },
] satisfies Array<{ value: StatusFilter; label: string }>;

const UNASSIGNED = "__unassigned__";

function matches(haystack: (string | undefined)[], needle: string): boolean {
	return !needle || haystack.some((value) => value?.toLowerCase().includes(needle));
}

export function CuratedCatalog({
	areas,
	practices,
	summary,
	search,
	pendingPracticeSlugs,
	pendingAreaSlugs,
	onSearchChange,
	onPracticeStatusChange,
	onAreaStatusChange,
}: CuratedCatalogProps) {
	const [retiringPractice, setRetiringPractice] = useState<CuratedPracticeSummary | null>(null);
	const [retiringArea, setRetiringArea] = useState<CuratedArea | null>(null);
	// Areas the administrator has collapsed. Everything else is open, so an area a filter or a
	// search has just revealed shows its practices rather than hiding them behind a closed panel.
	const [collapsedAreas, setCollapsedAreas] = useState<readonly string[]>([]);
	const query = search.q ?? "";
	const status: StatusFilter = search.status ?? "OFFERED";
	const artifact: ArtifactFilter = search.artifact ?? "ALL";
	const needle = query.trim().toLowerCase();
	const isPracticePending = (slug: string) => pendingPracticeSlugs?.has(slug) ?? false;
	const isAreaPending = (slug: string) => pendingAreaSlugs?.has(slug) ?? false;

	const areaBySlug = new Map(areas.map((area) => [area.slug, area]));
	const matchesStatus = (offered: boolean) =>
		status === "ALL" || (status === "OFFERED") === offered;

	const visiblePractices = practices.filter(
		(practice) =>
			matchesStatus(practice.status.offered) &&
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
	const byArea = new Map<string, CuratedPracticeSummary[]>();
	for (const practice of visiblePractices) {
		const key = practice.areaSlug ?? UNASSIGNED;
		const list = byArea.get(key);
		if (list) list.push(practice);
		else byArea.set(key, [practice]);
	}
	for (const list of byArea.values()) {
		list.sort((a, b) => a.name.localeCompare(b.name));
	}

	// An area is shown when it matches, or when it still holds a practice that does — hiding a
	// heading whose contents matched would make the search look broken.
	const visibleAreas = [...areas]
		.filter(
			(area) =>
				(matchesStatus(area.status.offered) &&
					matches(
						[area.definition.name, area.slug, area.definition.description ?? undefined],
						needle,
					)) ||
				(byArea.get(area.slug)?.length ?? 0) > 0,
		)
		.sort(
			(a, b) =>
				a.definition.displayOrder - b.definition.displayOrder ||
				a.definition.name.localeCompare(b.definition.name),
		);

	const knownAreas = new Set(areas.map((area) => area.slug));
	// A practice can outlive its area — the admin edited the practice, a newer build dropped both,
	// so the practice survives as an override and its area does not. Never let it fall off the page.
	const orphaned = [...byArea.entries()]
		.filter(([key]) => key !== UNASSIGNED && !knownAreas.has(key))
		.flatMap(([, held]) => held)
		.sort((a, b) => a.name.localeCompare(b.name));
	const unassigned = [...(byArea.get(UNASSIGNED) ?? []), ...orphaned].sort((a, b) =>
		a.name.localeCompare(b.name),
	);
	const unassignedTotal = practices.filter(
		(practice) => practice.areaSlug == null || !knownAreas.has(practice.areaSlug),
	).length;
	const nothingMatches = visibleAreas.length === 0 && unassigned.length === 0;
	const catalogIsEmpty = areas.length === 0 && practices.length === 0;
	const areaBeingRetiredHolds = retiringArea
		? practices.filter(
				(practice) => practice.areaSlug === retiringArea.slug && practice.status.offered,
			)
		: [];

	const practiceRow = (practice: CuratedPracticeSummary) => (
		<CuratedEntryRow
			key={practice.slug}
			name={practice.name}
			kind="practice"
			status={practice.status}
			pending={isPracticePending(practice.slug)}
			onOfferedChange={(offered) =>
				offered ? onPracticeStatusChange(practice, true) : setRetiringPractice(practice)
			}
			title={
				<Link
					from="/admin/catalog"
					to="/admin/catalog/practices/$practiceSlug"
					params={{ practiceSlug: practice.slug }}
					search={(previous) => previous}
					className="break-words rounded-sm hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
				>
					{practice.name}
				</Link>
			}
			meta={<span>{ARTIFACT_LABELS[practice.artifactType]}</span>}
			actions={
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
						{practice.status.offered ? (
							<DropdownMenuItem
								variant="destructive"
								disabled={isPracticePending(practice.slug)}
								onClick={() => setRetiringPractice(practice)}
							>
								Retire practice
							</DropdownMenuItem>
						) : (
							<DropdownMenuItem
								disabled={isPracticePending(practice.slug)}
								onClick={() => onPracticeStatusChange(practice, true)}
							>
								Offer again
							</DropdownMenuItem>
						)}
					</DropdownMenuContent>
				</DropdownMenu>
			}
		/>
	);

	return (
		<>
			<div className="space-y-4">
				<CuratedCatalogSummary summary={summary} />

				<div className="flex flex-col gap-2 sm:flex-row sm:items-center">
					<div className="flex flex-col gap-2 sm:flex-row sm:items-center">
						<div className="relative sm:w-64">
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
							items={STATUS_FILTERS}
							value={status}
							onValueChange={(value) =>
								value &&
								onSearchChange({
									...search,
									status: value === "OFFERED" ? undefined : (value as "NOT_OFFERED" | "ALL"),
								})
							}
						>
							<SelectTrigger
								className="w-full sm:hidden"
								aria-label="Filter by whether it is offered"
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
						<ToggleGroup
							role="toolbar"
							value={[status]}
							onValueChange={(value) =>
								value[0] &&
								onSearchChange({
									...search,
									status: value[0] === "OFFERED" ? undefined : (value[0] as "NOT_OFFERED" | "ALL"),
								})
							}
							variant="outline"
							size="sm"
							aria-label="Filter by whether it is offered"
							className="hidden sm:flex"
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
							<SelectTrigger className="w-full sm:w-52" aria-label="Filter by work type">
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
				</div>

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
					</Empty>
				) : (
					<>
						<Accordion
							className="space-y-2"
							multiple
							value={visibleAreas
								.map((area) => area.slug)
								.filter((slug) => !collapsedAreas.includes(slug))}
							onValueChange={(open) =>
								setCollapsedAreas(
									visibleAreas.map((area) => area.slug).filter((slug) => !open.includes(slug)),
								)
							}
						>
							{visibleAreas.map((area) => {
								const held = byArea.get(area.slug) ?? [];
								const total = practices.filter(
									(practice) => practice.areaSlug === area.slug,
								).length;
								const { Icon, pill } = getAreaVisual(
									area.slug,
									area.definition.name,
									area.definition.icon,
									area.definition.color,
								);
								const pending = isAreaPending(area.slug);
								return (
									<AccordionItem
										key={area.slug}
										value={area.slug}
										className="rounded-lg border bg-card"
									>
										<div className="flex items-center gap-2 rounded-lg px-2 [&>h3]:min-w-0 [&>h3]:flex-1">
											<span
												className={cn(
													"flex size-8 shrink-0 items-center justify-center rounded-md",
													pill,
												)}
												aria-hidden
											>
												<Icon className="size-4" />
											</span>
											<AccordionTrigger className="w-full min-w-0 py-2.5 hover:no-underline">
												<span className="flex min-w-0 flex-wrap items-center gap-2">
													<span className="min-w-0 break-words font-medium">
														{area.definition.name}
													</span>
													<Badge variant="secondary" className="shrink-0">
														{total}
													</Badge>
													<CuratedEntryBadges status={area.status} kind="area" />
												</span>
											</AccordionTrigger>
											<div className="ml-auto flex items-center gap-2">
												{pending && <Spinner className="size-4 text-muted-foreground" />}
												<Switch
													className="hidden sm:inline-flex"
													checked={area.status.offered}
													onCheckedChange={(offered) =>
														offered ? onAreaStatusChange(area, true) : setRetiringArea(area)
													}
													disabled={pending}
													aria-busy={pending}
													aria-label={`Offer ${area.definition.name} to new workspaces`}
												/>
												<DropdownMenu>
													<DropdownMenuTrigger
														render={
															<Button
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
														{area.status.offered ? (
															<DropdownMenuItem
																variant="destructive"
																disabled={pending}
																onClick={() => setRetiringArea(area)}
															>
																Retire area
															</DropdownMenuItem>
														) : (
															<DropdownMenuItem
																disabled={pending}
																onClick={() => onAreaStatusChange(area, true)}
															>
																Offer again
															</DropdownMenuItem>
														)}
													</DropdownMenuContent>
												</DropdownMenu>
											</div>
										</div>
										<AccordionContent className="px-2 pb-2 sm:pl-9">
											{held.length === 0 ? (
												<p className="flex min-h-12 items-center px-2 py-3 text-muted-foreground text-sm">
													{total > 0 ? "No matching practices." : "No practices in this area."}
												</p>
											) : (
												<ItemGroup className="gap-0.5">{held.map(practiceRow)}</ItemGroup>
											)}
										</AccordionContent>
									</AccordionItem>
								);
							})}
						</Accordion>

						<div className="rounded-lg border border-dashed">
							<div className="flex items-center gap-2 border-b border-dashed px-3 py-2">
								<span className="font-semibold text-muted-foreground text-sm">Unassigned</span>
								<Badge variant="secondary">
									{practices.filter((practice) => practice.areaSlug == null).length}
								</Badge>
							</div>
							<div className="px-2 py-1">
								{unassigned.length === 0 ? (
									<p className="flex min-h-12 items-center px-2 py-3 text-muted-foreground text-sm">
										{unassignedTotal > 0
											? "No matching practices outside an area."
											: "No practices sit outside an area."}
									</p>
								) : (
									<ItemGroup className="gap-0.5">{unassigned.map(practiceRow)}</ItemGroup>
								)}
							</div>
						</div>
					</>
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
							New workspaces will not receive it. Workspaces that already have it keep it,
							unchanged. You can offer it again later.
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

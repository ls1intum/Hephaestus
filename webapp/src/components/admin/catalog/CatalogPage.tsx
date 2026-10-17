import { Plus, RotateCcw, Search, Shapes } from "lucide-react";
import { type ReactNode, useState } from "react";

import { cn } from "cn";
import type { CatalogGroup, CatalogPractice } from "@/api/types.gen";
import { DetailStackLink } from "@/components/layout/detail-drawer/DetailStackLink";
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
import { InputGroup, InputGroupAddon, InputGroupInput } from "@/components/ui/input-group";
import {
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from "@/components/ui/select";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import {
	isKnownItemKind,
	ITEM_KIND_VALUES,
	itemKindLabel,
	type KnownItemKind,
} from "@/lib/item-kinds";
import { hasText } from "@/lib/text";

import { catalogChangeCounts, needsReview } from "./catalog-entry-state";
import { type CatalogSearch, catalogPracticeLevel } from "./catalog-search";
import { CatalogSummary } from "./CatalogSummary";
import { CatalogTree } from "./CatalogTree";

export interface CatalogPageProps {
	groups: readonly CatalogGroup[];
	practices: readonly CatalogPractice[];
	search: CatalogSearch;
	writePending?: boolean;
	pendingPracticeSlugs?: ReadonlySet<string>;
	pendingGroupSlugs?: ReadonlySet<string>;
	onSearchChange: (search: CatalogSearch) => void;
	onPracticeStatusChange: (practice: CatalogPractice, offered: boolean) => void;
	onGroupStatusChange: (group: CatalogGroup, offered: boolean) => void;
	onReorderGroups: (orderedSlugs: string[]) => void;
	onPlacePractice: (practiceSlug: string, groupSlug: string | null, position: number) => void;
	onResetOrder: () => void;
}

type StatusFilter = "OFFERED" | "NOT_OFFERED" | "ALL";
type KindFilter = KnownItemKind | "ALL";

function kindFilters(
	practices: readonly CatalogPractice[],
	selected: KindFilter,
): { value: KindFilter; label: string }[] {
	const kinds = ITEM_KIND_VALUES.filter(
		(kind) => kind === selected || practices.some((practice) => practice.kind === kind),
	);
	return [
		{ value: "ALL", label: "All work" },
		...kinds.map((kind) => ({ value: kind, label: itemKindLabel(kind, 2) })),
	];
}

const STATUS_FILTERS = [
	{ value: "ALL", label: "All" },
	{ value: "OFFERED", label: "Included" },
	{ value: "NOT_OFFERED", label: "Excluded" },
] satisfies { value: StatusFilter; label: string }[];

function matches(haystack: (string | null | undefined)[], needle: string): boolean {
	return !needle || haystack.some((value) => value?.toLowerCase().includes(needle) === true);
}

const NO_SLUGS: ReadonlySet<string> = new Set();

export function CatalogPage({
	groups,
	practices,
	search,
	writePending = false,
	pendingPracticeSlugs = NO_SLUGS,
	pendingGroupSlugs = NO_SLUGS,
	onSearchChange,
	onPracticeStatusChange,
	onGroupStatusChange,
	onReorderGroups,
	onPlacePractice,
	onResetOrder,
}: CatalogPageProps) {
	const [excludingPractice, setExcludingPractice] = useState<CatalogPractice | null>(null);
	const [excludingGroup, setExcludingGroup] = useState<CatalogGroup | null>(null);
	const [resettingOrder, setResettingOrder] = useState(false);
	const query = search.q ?? "";
	const status: StatusFilter = search.status ?? "ALL";
	const kind: KindFilter = search.kind ?? "ALL";
	const reviewOnly = search.review === true;
	const needle = query.trim().toLowerCase();
	const groupBySlug = new Map(groups.map((group) => [group.slug, group]));
	const matchesStatus = (offered: boolean) =>
		status === "ALL" || (status === "OFFERED") === offered;

	const visiblePractices = practices.filter(
		(practice) =>
			(!reviewOnly || needsReview(practice)) &&
			matchesStatus(practice.offered) &&
			(kind === "ALL" || practice.kind === kind) &&
			matches(
				[
					practice.definition.name,
					practice.slug,
					practice.groupSlug,
					hasText(practice.groupSlug) ? groupBySlug.get(practice.groupSlug)?.name : undefined,
				],
				needle,
			),
	);
	const visiblePracticeSlugs = new Set(visiblePractices.map((practice) => practice.slug));
	const groupsHoldingMatches = new Set(
		visiblePractices
			.map((practice) => practice.groupSlug)
			.filter((slug): slug is string => hasText(slug)),
	);
	const visibleGroups = groups.filter(
		(group) =>
			groupsHoldingMatches.has(group.slug) ||
			(kind === "ALL" &&
				(!reviewOnly || needsReview(group)) &&
				matchesStatus(group.offered) &&
				matches([group.name, group.slug, group.description], needle)),
	);

	const visibleGroupSlugs = new Set(visibleGroups.map((group) => group.slug));
	const canReorder = !needle && status === "ALL" && kind === "ALL" && !reviewOnly;
	const forcedOpenGroups = canReorder ? undefined : visibleGroupSlugs;
	const catalogIsEmpty = groups.length === 0 && practices.length === 0;
	const nothingMatches = visibleGroups.length === 0 && visiblePractices.length === 0;
	const practicesExcludedWithGroup = excludingGroup
		? practices.filter((practice) => practice.groupSlug === excludingGroup.slug && practice.offered)
		: [];

	let catalog: ReactNode;
	if (catalogIsEmpty) {
		catalog = (
			<Empty variant="outlined" className="min-h-56">
				<EmptyHeader>
					<EmptyMedia variant="icon">
						<Shapes aria-hidden />
					</EmptyMedia>
					<EmptyTitle>The catalog is empty</EmptyTitle>
					<EmptyDescription>
						Create a group or practice, then include it for workspace admins.
					</EmptyDescription>
				</EmptyHeader>
				<EmptyContent>
					<DetailStackLink entry={catalogPracticeLevel()} className={cn(buttonVariants())}>
						<Plus className="mr-1.5 size-4" aria-hidden />
						Create practice
					</DetailStackLink>
				</EmptyContent>
			</Empty>
		);
	} else if (nothingMatches) {
		catalog = (
			<Empty variant="outlined" className="min-h-56">
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
		);
	} else {
		catalog = (
			<CatalogTree
				groups={groups}
				practices={practices}
				visibleGroupSlugs={visibleGroupSlugs}
				visiblePracticeSlugs={visiblePracticeSlugs}
				forceOpenGroupSlugs={forcedOpenGroups}
				canReorder={canReorder}
				writePending={writePending}
				pendingGroupSlugs={pendingGroupSlugs}
				pendingPracticeSlugs={pendingPracticeSlugs}
				onGroupStatusChange={onGroupStatusChange}
				onPracticeStatusChange={onPracticeStatusChange}
				onExcludeGroup={setExcludingGroup}
				onExcludePractice={setExcludingPractice}
				onReorderGroups={onReorderGroups}
				onPlacePractice={onPlacePractice}
			/>
		);
	}

	return (
		<>
			<div className="space-y-4">
				<CatalogSummary
					counts={catalogChangeCounts({ groups, practices })}
					reviewing={reviewOnly}
					onReviewChanges={() => onSearchChange({ review: true })}
				/>
				<CatalogFilters
					search={search}
					kinds={kindFilters(practices, kind)}
					onSearchChange={onSearchChange}
				/>
				{!catalogIsEmpty && (
					<div className="flex flex-wrap items-center justify-between gap-3 text-sm">
						<p className="text-muted-foreground">
							{canReorder
								? "Drag groups and practices to change the order new workspaces see."
								: "Clear the search and filters to reorder the catalog."}
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

				{catalog}
			</div>

			<AlertDialog open={resettingOrder} onOpenChange={setResettingOrder}>
				<AlertDialogContent>
					<AlertDialogHeader>
						<AlertDialogTitle>Use the Hephaestus order?</AlertDialogTitle>
						<AlertDialogDescription>
							Groups and practices will return to the order included with this Hephaestus version.
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
					if (!open) {
						setExcludingPractice(null);
					}
				}}
			>
				<AlertDialogContent>
					<AlertDialogHeader>
						<AlertDialogTitle>
							Exclude practice “{excludingPractice?.definition.name}”?
						</AlertDialogTitle>
						<AlertDialogDescription>
							{excludingPractice?.offered === false
								? "Its group is already excluded. This keeps the practice excluded if the group is included again. Existing workspace copies will not change."
								: "Workspace admins will no longer be able to add this practice. Existing workspace copies will not change. You can include it again later."}
						</AlertDialogDescription>
					</AlertDialogHeader>
					<AlertDialogFooter>
						<AlertDialogCancel>Cancel</AlertDialogCancel>
						<AlertDialogAction
							onClick={() => {
								if (excludingPractice) {
									onPracticeStatusChange(excludingPractice, false);
								}
								setExcludingPractice(null);
							}}
						>
							Exclude practice
						</AlertDialogAction>
					</AlertDialogFooter>
				</AlertDialogContent>
			</AlertDialog>

			<AlertDialog
				open={excludingGroup !== null}
				onOpenChange={(open) => {
					if (!open) {
						setExcludingGroup(null);
					}
				}}
			>
				<AlertDialogContent>
					<AlertDialogHeader>
						<AlertDialogTitle>Exclude group “{excludingGroup?.name}”?</AlertDialogTitle>
						<AlertDialogDescription>
							{practicesExcludedWithGroup.length === 0
								? "Workspace admins will no longer be able to add this group. No additional practices are affected. Existing workspace copies will not change."
								: `Workspace admins will no longer be able to add this group. This also excludes ${practicesExcludedWithGroup.length} included ${
										practicesExcludedWithGroup.length === 1 ? "practice" : "practices"
									}. Existing workspace copies will not change.`}
						</AlertDialogDescription>
					</AlertDialogHeader>
					{practicesExcludedWithGroup.length > 0 && (
						<ul
							aria-label="Practices this also excludes"
							className="max-h-40 list-disc overflow-y-auto pl-5 text-sm text-muted-foreground"
						>
							{practicesExcludedWithGroup.map((practice) => (
								<li key={practice.slug}>{practice.definition.name}</li>
							))}
						</ul>
					)}
					<AlertDialogFooter>
						<AlertDialogCancel>Cancel</AlertDialogCancel>
						<AlertDialogAction
							onClick={() => {
								if (excludingGroup) {
									onGroupStatusChange(excludingGroup, false);
								}
								setExcludingGroup(null);
							}}
						>
							Exclude group
						</AlertDialogAction>
					</AlertDialogFooter>
				</AlertDialogContent>
			</AlertDialog>
		</>
	);
}

function CatalogFilters({
	search,
	kinds,
	onSearchChange,
}: {
	search: CatalogSearch;
	kinds: { value: KindFilter; label: string }[];
	onSearchChange: (search: CatalogSearch) => void;
}) {
	const status: StatusFilter = search.status ?? "ALL";
	const kind: KindFilter = search.kind ?? "ALL";
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
			<InputGroup className="sm:col-span-2 lg:w-64">
				<InputGroupAddon>
					<Search aria-hidden />
				</InputGroupAddon>
				<InputGroupInput
					type="search"
					value={search.q ?? ""}
					onChange={(event) => onSearchChange({ ...search, q: event.target.value || undefined })}
					placeholder="Search the catalog"
					aria-label="Search the catalog"
				/>
			</InputGroup>
			<Select
				items={STATUS_FILTERS}
				value={status}
				onValueChange={(value) => {
					if (value) {
						onSearchChange({ ...search, status: value === "ALL" ? undefined : value });
					}
				}}
			>
				<SelectTrigger className="w-full lg:hidden" aria-label="Filter by inclusion in workspaces">
					<SelectValue />
				</SelectTrigger>
				<SelectContent aria-label="Filter by inclusion in workspaces">
					{STATUS_FILTERS.map((filter) => (
						<SelectItem key={filter.value} value={filter.value}>
							{filter.label}
						</SelectItem>
					))}
				</SelectContent>
			</Select>
			{/* `toolbar`, not the default `group`: Base UI gives the group a roving tabindex, and
			    `toolbar` is the role that contract belongs to. `radiogroup` would be worse — the items
			    are `aria-pressed`, not radios. */}
			<ToggleGroup
				role="toolbar"
				value={[status]}
				onValueChange={(value) =>
					value[0] &&
					onSearchChange({
						...search,
						status: value[0] === "ALL" ? undefined : value[0],
					})
				}
				variant="outline"
				size="sm"
				aria-label="Filter by inclusion in workspaces"
				className="hidden lg:flex"
			>
				{STATUS_FILTERS.map((filter) => (
					<ToggleGroupItem key={filter.value} value={filter.value} className="min-w-0">
						{filter.label}
					</ToggleGroupItem>
				))}
			</ToggleGroup>
			<Select
				items={kinds}
				value={kind}
				onValueChange={(value) =>
					onSearchChange({
						...search,
						kind: value !== null && isKnownItemKind(value) ? value : undefined,
					})
				}
			>
				<SelectTrigger className="w-full lg:w-52" aria-label="Filter by kind of work">
					<SelectValue />
				</SelectTrigger>
				<SelectContent aria-label="Filter by kind of work">
					{kinds.map(({ value, label }) => (
						<SelectItem key={value} value={value}>
							{label}
						</SelectItem>
					))}
				</SelectContent>
			</Select>
		</div>
	);
}

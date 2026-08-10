import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { ChevronRightIcon, RadarIcon } from "lucide-react";
import { listTracedArtifactsOptions } from "@/api/@tanstack/react-query.gen";
import { FilterToolbar } from "@/components/common/FilterToolbar";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { RelativeTime } from "@/components/common/RelativeTime";
import { ResultCount } from "@/components/common/ResultCount";
import { TablePagination } from "@/components/common/TablePagination";
import { PageHeader } from "@/components/core/PageHeader";
import {
	Empty,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
import {
	Item,
	ItemActions,
	ItemContent,
	ItemDescription,
	ItemGroup,
	ItemTitle,
} from "@/components/ui/item";
import { Label } from "@/components/ui/label";
import {
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import {
	ARTIFACT_KIND_VALUES,
	artifactKindIcon,
	artifactKindLabel,
	artifactKindPluralLabel,
} from "@/lib/artifact-kinds";
import { signalCountsLabel } from "./trace-format";
import type { TraceSearch } from "./trace-search";

const PAGE_SIZE = 20;
/** Base UI treats "" as "no selection", so the "everything" choice needs a value of its own. */
const ALL_KINDS = "__all";

export interface TraceListPageProps {
	workspaceSlug: string;
	search: TraceSearch;
	onSearchChange: (patch: Partial<TraceSearch>) => void;
}

/**
 * Every piece of work this workspace has recorded anything about — including work no practice had a
 * word to say about, which is exactly what "why did nobody say anything?" needs to show.
 */
export function TraceListPage({ workspaceSlug, search, onSearchChange }: TraceListPageProps) {
	const page = search.page ?? 0;
	const query = useQuery({
		...listTracedArtifactsOptions({
			path: { workspaceSlug },
			query: { page, size: PAGE_SIZE, artifactKind: search.kind },
		}),
	});
	const artifacts = query.data?.content ?? [];
	// No endpoint enumerates the kinds, so the choices are the ones this build knows plus any the
	// page shows — and always the active filter, so a filter arriving by link can be seen and cleared.
	const kinds = [
		...new Set([
			...ARTIFACT_KIND_VALUES,
			...artifacts.map((artifact) => artifact.artifactKind),
			...(search.kind ? [search.kind] : []),
		]),
	];
	// Without `items`, Base UI has nothing to resolve the selected value against and the closed
	// trigger prints the value itself — "scm.issue", not "Issues".
	const kindItems = [
		{ value: ALL_KINDS, label: "All work" },
		...kinds.map((kind) => ({ value: kind, label: artifactKindPluralLabel(kind) })),
	];
	const hasFilter = Boolean(search.kind);

	return (
		<div className="min-w-0 space-y-6">
			<PageHeader
				icon={<RadarIcon />}
				title="Review activity"
				description="Everything Hephaestus noticed about this workspace's work, and — for each piece of work — what every practice made of it. Including the practices that stayed quiet, and why."
			/>
			<section aria-label="Recorded work" className="space-y-4">
				<FilterToolbar
					hasFilter={hasFilter}
					onReset={() => onSearchChange({ kind: undefined, page: undefined })}
					actions={
						<ResultCount
							total={query.data?.page?.totalElements}
							noun={["piece of work", "pieces of work"]}
							hasFilter={hasFilter}
						/>
					}
				>
					<div className="flex min-w-0 items-center gap-2">
						<Label
							id="trace-artifact-kind-label"
							htmlFor="trace-artifact-kind"
							className="shrink-0 text-muted-foreground"
						>
							Show
						</Label>
						<Select
							items={kindItems}
							value={search.kind ?? ALL_KINDS}
							onValueChange={(value) =>
								onSearchChange({
									kind: value === ALL_KINDS ? undefined : String(value),
									page: undefined,
								})
							}
						>
							<SelectTrigger id="trace-artifact-kind" className="w-56 max-w-full">
								<SelectValue placeholder="All work" />
							</SelectTrigger>
							<SelectContent aria-labelledby="trace-artifact-kind-label">
								{kindItems.map((kind) => (
									<SelectItem key={kind.value} value={kind.value}>
										{kind.label}
									</SelectItem>
								))}
							</SelectContent>
						</Select>
					</div>
				</FilterToolbar>

				{query.isError ? (
					<QueryErrorAlert
						error={query.error}
						title="Couldn't load review activity"
						onRetry={() => void query.refetch()}
					/>
				) : query.isLoading ? (
					<TraceListSkeleton />
				) : artifacts.length === 0 ? (
					<Empty className="border">
						<EmptyHeader>
							<EmptyMedia variant="icon">
								<RadarIcon />
							</EmptyMedia>
							<EmptyTitle>
								{hasFilter
									? `No ${artifactKindPluralLabel(search.kind).toLowerCase()} recorded yet`
									: "Nothing has been recorded here yet"}
							</EmptyTitle>
							<EmptyDescription>
								{hasFilter
									? "Switch back to all work to see everything this workspace has recorded."
									: "As soon as this workspace is connected to a repository or a chat, the pull requests, issues and threads it sees will appear here — including the ones no practice had anything to say about."}
							</EmptyDescription>
						</EmptyHeader>
					</Empty>
				) : (
					<ItemGroup>
						{artifacts.map((artifact) => {
							const KindIcon = artifactKindIcon(artifact.artifactKind);
							return (
								<div key={`${artifact.artifactKind}:${artifact.artifactId}`} role="listitem">
									<Item
										variant="outline"
										className="items-start"
										render={
											<Link
												to="/w/$workspaceSlug/reviews/$artifactKind/$artifactId"
												params={{
													workspaceSlug,
													artifactKind: artifact.artifactKind,
													artifactId: String(artifact.artifactId),
												}}
											/>
										}
									>
										<span className="mt-0.5 shrink-0 text-muted-foreground [&>svg]:size-4">
											<KindIcon aria-hidden />
											<span className="sr-only">{artifactKindLabel(artifact.artifactKind)}</span>
										</span>
										<ItemContent className="min-w-0">
											<ItemTitle className="w-full min-w-0 line-clamp-none break-words">
												{artifact.title}
												{artifact.number != null && (
													<span className="ml-1 font-normal text-muted-foreground tabular-nums">
														#{artifact.number}
													</span>
												)}
											</ItemTitle>
											{artifact.container && (
												<ItemDescription className="break-words">
													{artifact.container}
												</ItemDescription>
											)}
											<div className="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-muted-foreground">
												<RelativeTime value={artifact.lastSignalAt} />
												<span aria-hidden>·</span>
												<span>
													{signalCountsLabel(artifact.signalCount, artifact.reviewedSignalCount)}
												</span>
											</div>
										</ItemContent>
										<ItemActions>
											<ChevronRightIcon className="size-4 text-muted-foreground" aria-hidden />
										</ItemActions>
									</Item>
								</div>
							);
						})}
					</ItemGroup>
				)}

				<TablePagination
					page={query.data?.page?.number ?? page}
					totalPages={query.data?.page?.totalPages ?? 0}
					renderPageLink={(target, props) => (
						<Link
							{...props}
							to="/w/$workspaceSlug/reviews"
							params={{ workspaceSlug }}
							search={(previous) => ({ ...previous, page: target === 0 ? undefined : target })}
						/>
					)}
				/>
			</section>
		</div>
	);
}

function TraceListSkeleton() {
	return (
		<div role="status" className="space-y-4">
			<span className="sr-only">Loading review activity</span>
			{Array.from({ length: 4 }, (_, index) => (
				<div key={index} className="space-y-2 rounded-lg border p-3">
					<Skeleton className="h-5 w-3/4 max-w-md" />
					<Skeleton className="h-4 w-1/2 max-w-xs" />
					<Skeleton className="h-4 w-40" />
				</div>
			))}
		</div>
	);
}

import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import {
	listAreasOptions,
	listPracticeReviewObservationsOptions,
	listPracticesOptions,
} from "@/api/@tanstack/react-query.gen";
import { DateRangeFacet } from "@/components/common/DateRangeFacet";
import { FacetMultiSelect, type FacetOption } from "@/components/common/FacetMultiSelect";
import { FilterToolbar } from "@/components/common/FilterToolbar";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { ReferenceFilterPill } from "@/components/common/ReferenceFilterPill";
import { ResultCount } from "@/components/common/ResultCount";
import { TablePagination } from "@/components/common/TablePagination";
import { ASSESSMENT_DEFS } from "@/components/practice-vocabulary/assessment-defs";
import { PRESENCE_DEFS } from "@/components/practice-vocabulary/presence-defs";
import { SEVERITY_DEFS } from "@/components/practice-vocabulary/severity-defs";
import { statusFacetOptions } from "@/components/practice-vocabulary/status-def";
import { useClampedPage } from "@/hooks/use-clamped-page";
import { fromDateRange, toDateRange } from "@/lib/date-range-search";
import { nonEmpty } from "@/lib/search-params";
import { AppliedFacetPills, facetPills } from "./AppliedFacetPills";
import { ObservationResults } from "./ObservationResults";
import { ObservationSortSelect } from "./ObservationSortSelect";
import { reviewArtifactScopeLabel } from "./ReviewArtifact";
import { ReviewPersonFacet } from "./ReviewPersonFacet";
import { type ObservationsSearch, observationsQuery, REVIEW_PAGE_SIZE } from "./review-search";

/** Every option wears the badge its rows wear; see the note on `FeedbackListPage`'s facets. */
const ASSESSMENT_OPTIONS = statusFacetOptions(ASSESSMENT_DEFS);
const PRESENCE_OPTIONS = statusFacetOptions(PRESENCE_DEFS);
const SEVERITY_OPTIONS = statusFacetOptions(SEVERITY_DEFS);

export interface ObservationsListPageProps {
	workspaceSlug: string;
	search: ObservationsSearch;
	onSearchChange: (patch: Partial<ObservationsSearch>) => void;
}

export function ObservationsListPage({
	workspaceSlug,
	search,
	onSearchChange,
}: ObservationsListPageProps) {
	const observationsQueryResult = useQuery({
		...listPracticeReviewObservationsOptions({
			path: { workspaceSlug },
			query: observationsQuery(search, REVIEW_PAGE_SIZE),
		}),
	});
	const areasQuery = useQuery({ ...listAreasOptions({ path: { workspaceSlug } }) });
	const practicesQuery = useQuery({ ...listPracticesOptions({ path: { workspaceSlug } }) });
	const areaOptions: FacetOption[] = (areasQuery.data ?? []).map((area) => ({
		value: area.slug,
		label: area.name,
	}));
	const practiceOptions: FacetOption[] = (practicesQuery.data ?? []).map((practice) => ({
		value: practice.slug,
		label: practice.name,
		description: (areasQuery.data ?? []).find((area) => area.slug === practice.areaSlug)?.name,
	}));
	const observations = observationsQueryResult.data?.content ?? [];
	const totalPages = observationsQueryResult.data?.page?.totalPages;
	// Guarded on the filter being set, because that is the only condition under which the first row
	// names the filtered person — unfiltered, row zero is whoever happens to sort first, and the facet
	// would put a stranger's name on somebody else's id. `ReviewPersonFacet` reads `fallbackName` only
	// inside `selected != null`; deriving it under the same condition is what keeps the two together.
	const filteredSubject = search.subjectUserId != null ? observations[0]?.subject : undefined;
	const hasFilter = Boolean(
		search.areaSlug?.length ||
			search.practiceSlug?.length ||
			search.presence?.length ||
			search.assessment?.length ||
			search.severity?.length ||
			search.agentJobId ||
			search.artifactKind ||
			search.subjectUserId ||
			search.from ||
			search.to,
	);
	const reset = () =>
		onSearchChange({
			page: 0,
			areaSlug: undefined,
			practiceSlug: undefined,
			presence: undefined,
			assessment: undefined,
			severity: undefined,
			agentJobId: undefined,
			artifactKind: undefined,
			artifactId: undefined,
			subjectUserId: undefined,
			from: undefined,
			to: undefined,
		});
	const patchFilter = (patch: Partial<ObservationsSearch>) => onSearchChange({ ...patch, page: 0 });

	useClampedPage(search.page, totalPages, (page) => onSearchChange({ page }));

	return (
		<section aria-label="Practice review observations" className="space-y-4">
			<FilterToolbar
				hasFilter={hasFilter}
				onReset={reset}
				actions={
					<>
						{/* Sort sits with the count rather than among the facets: it does not narrow the set,
						    and `Reset` deliberately leaves it alone. */}
						<ObservationSortSelect
							value={search.order}
							onChange={(order) => onSearchChange({ order, page: 0 })}
						/>
						<ResultCount
							total={observationsQueryResult.data?.page?.totalElements}
							noun={["observation", "observations"]}
							hasFilter={hasFilter}
						/>
					</>
				}
			>
				<div className="flex flex-wrap gap-2">
					<FacetMultiSelect
						title="Area"
						options={areaOptions}
						selected={search.areaSlug ?? []}
						onChange={(values) => patchFilter({ areaSlug: nonEmpty(values) })}
						disabled={areasQuery.isLoading}
						emptyLabel={areasQuery.isError ? "Could not load areas" : "No areas available"}
					/>
					<FacetMultiSelect
						title="Practice"
						options={practiceOptions}
						selected={search.practiceSlug ?? []}
						onChange={(values) => patchFilter({ practiceSlug: nonEmpty(values) })}
						disabled={practicesQuery.isLoading}
						emptyLabel={
							practicesQuery.isError ? "Could not load practices" : "No practices available"
						}
					/>
					<FacetMultiSelect
						title="Result"
						options={ASSESSMENT_OPTIONS}
						selected={search.assessment ?? []}
						onChange={(values) => patchFilter({ assessment: nonEmpty(values) })}
					/>
					<FacetMultiSelect
						title="Severity"
						options={SEVERITY_OPTIONS}
						selected={search.severity ?? []}
						onChange={(values) => patchFilter({ severity: nonEmpty(values) })}
					/>
					<FacetMultiSelect
						title="Practice status"
						options={PRESENCE_OPTIONS}
						selected={search.presence ?? []}
						onChange={(values) => patchFilter({ presence: nonEmpty(values) })}
					/>
					<ReviewPersonFacet
						workspaceSlug={workspaceSlug}
						title="Developer"
						selected={search.subjectUserId}
						onChange={(subjectUserId) => patchFilter({ subjectUserId })}
						fallbackName={filteredSubject?.name ?? filteredSubject?.login}
					/>
					{/* "Observed" rather than "Date": this range filters `observedAt`, and the same control
					    on Delivery filters when the feedback was composed. A filter label has to be
					    "concrete and predictable" (NN/g, "Filter Categories and Values"). */}
					<DateRangeFacet
						title="Observed"
						value={toDateRange(search)}
						onChange={(range) => patchFilter(fromDateRange(range))}
					/>
				</div>
				<AppliedFacetPills
					pills={[
						...facetPills("Area", areaOptions, search.areaSlug, (values) =>
							patchFilter({ areaSlug: nonEmpty(values) }),
						),
						...facetPills("Practice", practiceOptions, search.practiceSlug, (values) =>
							patchFilter({ practiceSlug: nonEmpty(values) }),
						),
						...facetPills("Result", ASSESSMENT_OPTIONS, search.assessment, (values) =>
							patchFilter({ assessment: nonEmpty(values) }),
						),
						...facetPills("Severity", SEVERITY_OPTIONS, search.severity, (values) =>
							patchFilter({ severity: nonEmpty(values) }),
						),
						...facetPills("Practice status", PRESENCE_OPTIONS, search.presence, (values) =>
							patchFilter({ presence: nonEmpty(values) }),
						),
					]}
				/>
				{search.agentJobId && (
					<ReferenceFilterPill
						label="Review"
						value={search.agentJobId}
						onClear={() => patchFilter({ agentJobId: undefined })}
					/>
				)}
				{search.artifactKind && (
					<ReferenceFilterPill
						label="Reviewed work"
						value={reviewArtifactScopeLabel(
							search.artifactKind,
							search.artifactId,
							observations[0]?.artifact,
						)}
						onClear={() => patchFilter({ artifactKind: undefined, artifactId: undefined })}
					/>
				)}
			</FilterToolbar>
			{observationsQueryResult.isError ? (
				<QueryErrorAlert
					error={observationsQueryResult.error}
					title="Couldn't load observations"
					onRetry={() => observationsQueryResult.refetch()}
				/>
			) : (
				<ObservationResults
					workspaceSlug={workspaceSlug}
					state={
						observationsQueryResult.isLoading
							? { status: "loading" }
							: observations.length === 0
								? hasFilter
									? { status: "empty", filtered: true, onClearFilters: reset }
									: { status: "empty", filtered: false }
								: { status: "ready", observations }
					}
				/>
			)}
			<TablePagination
				page={observationsQueryResult.data?.page?.number ?? search.page ?? 0}
				totalPages={observationsQueryResult.data?.page?.totalPages ?? 0}
				renderPageLink={(page, props) => (
					<Link
						{...props}
						to="/w/$workspaceSlug/admin/practices/reviews/observations"
						params={{ workspaceSlug }}
						search={(previous) => ({ ...previous, page: page === 0 ? undefined : page })}
					/>
				)}
			/>
		</section>
	);
}

import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { useEffect } from "react";
import {
	listAreasOptions,
	listPracticeReviewFindingsOptions,
	listPracticesOptions,
} from "@/api/@tanstack/react-query.gen";
import { DateRangeFacet } from "@/components/common/DateRangeFacet";
import {
	FacetMultiSelect,
	type FacetOption,
	toFacetOptions,
} from "@/components/common/FacetMultiSelect";
import { FilterToolbar } from "@/components/common/FilterToolbar";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { ReferenceFilterPill } from "@/components/common/ReferenceFilterPill";
import { ResultCount } from "@/components/common/ResultCount";
import { TablePagination } from "@/components/common/TablePagination";
import { fromDateRange, toDateRange } from "@/lib/date-range-search";
import { nonEmpty } from "@/lib/search-params";
import { FindingResults } from "./FindingResults";
import { reviewArtifactScopeLabel } from "./ReviewArtifact";
import { ReviewMoreFilters } from "./ReviewMoreFilters";
import {
	ASSESSMENT_LABELS,
	type Assessment,
	PRESENCE_LABELS,
	type Presence,
	SEVERITY_LABELS,
	type Severity,
} from "./review-format";
import { type FindingsSearch, findingsQuery } from "./review-search";

const PAGE_SIZE = 25;
const ASSESSMENT_OPTIONS: FacetOption<Assessment>[] = toFacetOptions(ASSESSMENT_LABELS);
const PRESENCE_OPTIONS: FacetOption<Presence>[] = toFacetOptions(PRESENCE_LABELS);
const SEVERITY_OPTIONS: FacetOption<Severity>[] = toFacetOptions(SEVERITY_LABELS);

export interface FindingsListPageProps {
	workspaceSlug: string;
	search: FindingsSearch;
	onSearchChange: (patch: Partial<FindingsSearch>) => void;
}

export function FindingsListPage({ workspaceSlug, search, onSearchChange }: FindingsListPageProps) {
	const findingsQueryResult = useQuery({
		...listPracticeReviewFindingsOptions({
			path: { workspaceSlug },
			query: findingsQuery(search, PAGE_SIZE),
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
	const findings = findingsQueryResult.data?.content ?? [];
	const totalPages = findingsQueryResult.data?.page?.totalPages;
	const subject = findings[0]?.subject;
	const hasFilter = Boolean(
		search.areaSlug?.length ||
			search.practiceSlug?.length ||
			search.presence?.length ||
			search.assessment?.length ||
			search.severity?.length ||
			search.agentJobId ||
			search.artifactType ||
			search.subjectUserId ||
			search.from ||
			search.to,
	);
	const advancedCount = (search.presence?.length ?? 0) + (search.severity?.length ?? 0);
	const reset = () =>
		onSearchChange({
			page: 0,
			areaSlug: undefined,
			practiceSlug: undefined,
			presence: undefined,
			assessment: undefined,
			severity: undefined,
			agentJobId: undefined,
			artifactType: undefined,
			artifactId: undefined,
			subjectUserId: undefined,
			from: undefined,
			to: undefined,
		});
	const patchFilter = (patch: Partial<FindingsSearch>) => onSearchChange({ ...patch, page: 0 });

	useEffect(() => {
		if (totalPages !== undefined && search.page && search.page >= totalPages) {
			onSearchChange({ page: Math.max(0, totalPages - 1) });
		}
	}, [onSearchChange, search.page, totalPages]);

	return (
		<section aria-label="Practice review findings" className="space-y-4">
			<FilterToolbar
				hasFilter={hasFilter}
				onReset={reset}
				actions={
					<ResultCount
						total={findingsQueryResult.data?.page?.totalElements}
						noun={["finding", "findings"]}
						hasFilter={hasFilter}
					/>
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
						title="Assessment"
						options={ASSESSMENT_OPTIONS}
						selected={search.assessment ?? []}
						onChange={(values) => patchFilter({ assessment: nonEmpty(values) })}
					/>
					<DateRangeFacet
						value={toDateRange(search)}
						onChange={(range) => patchFilter(fromDateRange(range))}
					/>
					<ReviewMoreFilters activeCount={advancedCount}>
						<FacetMultiSelect
							title="Severity"
							variant="field"
							options={SEVERITY_OPTIONS}
							selected={search.severity ?? []}
							onChange={(values) => patchFilter({ severity: nonEmpty(values) })}
						/>
						<FacetMultiSelect
							title="Practice status"
							variant="field"
							options={PRESENCE_OPTIONS}
							selected={search.presence ?? []}
							onChange={(values) => patchFilter({ presence: nonEmpty(values) })}
						/>
					</ReviewMoreFilters>
				</div>
				{search.agentJobId && (
					<ReferenceFilterPill
						label="Review"
						value={search.agentJobId}
						onClear={() => patchFilter({ agentJobId: undefined })}
					/>
				)}
				{search.subjectUserId && (
					<ReferenceFilterPill
						label="Developer"
						id={search.subjectUserId}
						name={subject?.name ?? subject?.login}
						onClear={() => patchFilter({ subjectUserId: undefined })}
					/>
				)}
				{search.artifactType && (
					<ReferenceFilterPill
						label="Reviewed work"
						value={reviewArtifactScopeLabel(
							search.artifactType,
							search.artifactId,
							findings[0]?.artifact,
						)}
						onClear={() => patchFilter({ artifactType: undefined, artifactId: undefined })}
					/>
				)}
			</FilterToolbar>
			{findingsQueryResult.isError ? (
				<QueryErrorAlert
					error={findingsQueryResult.error}
					title="Couldn't load findings"
					onRetry={() => findingsQueryResult.refetch()}
				/>
			) : (
				<FindingResults
					workspaceSlug={workspaceSlug}
					state={
						findingsQueryResult.isLoading
							? { status: "loading" }
							: findings.length === 0
								? { status: "empty", filtered: hasFilter }
								: { status: "ready", findings }
					}
				/>
			)}
			<TablePagination
				page={findingsQueryResult.data?.page?.number ?? search.page ?? 0}
				totalPages={findingsQueryResult.data?.page?.totalPages ?? 0}
				renderPageLink={(page, props) => (
					<Link
						{...props}
						to="/w/$workspaceSlug/admin/practices/reviews/findings"
						params={{ workspaceSlug }}
						search={(previous) => ({ ...previous, page: page === 0 ? undefined : page })}
					/>
				)}
			/>
		</section>
	);
}

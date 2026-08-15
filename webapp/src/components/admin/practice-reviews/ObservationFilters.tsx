import type { ReviewArtifact } from "@/api/types.gen";
import { DateRangeFacet } from "@/components/common/DateRangeFacet";
import { FacetMultiSelect, type FacetOption } from "@/components/common/FacetMultiSelect";
import { FilterToolbar } from "@/components/common/FilterToolbar";
import { ReferenceFilterPill } from "@/components/common/ReferenceFilterPill";
import { ResultCount } from "@/components/common/ResultCount";
import { ASSESSMENT_DEFS } from "@/components/practice-vocabulary/assessment-defs";
import { PRESENCE_DEFS } from "@/components/practice-vocabulary/presence-defs";
import { SEVERITY_DEFS } from "@/components/practice-vocabulary/severity-defs";
import { statusFacetOptions } from "@/components/practice-vocabulary/status-def";
import { fromDateRange, toDateRange } from "@/lib/date-range-search";
import { nonEmpty } from "@/lib/search-params";
import { AppliedFacetPills, facetPills } from "./AppliedFacetPills";
import { ObservationSortSelect } from "./ObservationSortSelect";
import { reviewArtifactScopeLabel } from "./ReviewArtifact";
import { type ReviewPeople, ReviewPersonFacet } from "./ReviewPersonFacet";
import type { ObservationsSearch } from "./review-search";

/** Every option wears the badge its rows wear; see the note on `FeedbackFilters`' facets. */
const ASSESSMENT_OPTIONS = statusFacetOptions(ASSESSMENT_DEFS);
const PRESENCE_OPTIONS = statusFacetOptions(PRESENCE_DEFS);
const SEVERITY_OPTIONS = statusFacetOptions(SEVERITY_DEFS);

/**
 * Every field this toolbar can set, cleared — `order` deliberately excluded, because sorting does not
 * narrow anything and Reset leaves it alone. Exported so the list's empty state clears exactly what
 * Reset clears.
 */
export function clearedObservationFilters(): Partial<ObservationsSearch> {
	return {
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
	};
}

/** Whether anything above is set — read by the count's wording and by the empty state's copy. */
export function hasObservationFilter(search: ObservationsSearch): boolean {
	return Boolean(
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
}

/**
 * A facet whose options are the workspace's own catalogue rather than a fixed registry, so it can be
 * empty for two different reasons and has to say which. Same shape as {@link ReviewPeople}: whoever
 * fetches owes the options and the two flags, and the control never learns where they came from.
 */
export interface FacetSource {
	options: FacetOption[];
	isLoading?: boolean;
	isError?: boolean;
}

/**
 * Typed by what the derivation reads rather than by the whole record: an area's tier and dashboard
 * visibility are nothing to a facet, and asking for them would make a caller hand over fields it has
 * no reason to hold.
 */
type NamedArea = { slug: string; name: string };

export function areaFacetOptions(areas: readonly NamedArea[] | undefined): FacetOption[] {
	return (areas ?? []).map((area) => ({ value: area.slug, label: area.name }));
}

/** The area name rides along as each practice's description, which is how the facet groups them. */
export function practiceFacetOptions(
	practices: readonly { slug: string; name: string; areaSlug?: string }[] | undefined,
	areas: readonly NamedArea[] | undefined,
): FacetOption[] {
	return (practices ?? []).map((practice) => ({
		value: practice.slug,
		label: practice.name,
		description: (areas ?? []).find((area) => area.slug === practice.areaSlug)?.name,
	}));
}

export interface ObservationFiltersProps {
	search: ObservationsSearch;
	/** Reports one changed facet. The caller sends the reader back to page one. */
	onPatch: (patch: Partial<ObservationsSearch>) => void;
	onReset: () => void;
	areas: FacetSource;
	practices: FacetSource;
	people: ReviewPeople;
	total: number | undefined;
	/**
	 * The work `artifactKind`/`artifactId` points at, so the pill can name it rather than print an
	 * id. Absent until a row carrying that artifact has arrived.
	 */
	scopedArtifact?: ReviewArtifact;
	/**
	 * The name of the person `subjectUserId` identifies. It must name *that* person: reading it off
	 * the first row is right only while the filter is on.
	 */
	subjectName?: string;
}

export function ObservationFilters({
	search,
	onPatch,
	onReset,
	areas,
	practices,
	people,
	total,
	scopedArtifact,
	subjectName,
}: ObservationFiltersProps) {
	const hasFilter = hasObservationFilter(search);

	return (
		<FilterToolbar
			hasFilter={hasFilter}
			onReset={onReset}
			actions={
				<>
					{/* Sort sits with the count rather than among the facets: it does not narrow the set,
					    and `Reset` deliberately leaves it alone. */}
					<ObservationSortSelect value={search.order} onChange={(order) => onPatch({ order })} />
					<ResultCount total={total} noun={["observation", "observations"]} hasFilter={hasFilter} />
				</>
			}
		>
			<div className="flex flex-wrap gap-2">
				<FacetMultiSelect
					title="Area"
					options={areas.options}
					selected={search.areaSlug ?? []}
					onChange={(values) => onPatch({ areaSlug: nonEmpty(values) })}
					disabled={areas.isLoading}
					emptyLabel={areas.isError ? "Could not load areas" : "No areas available"}
				/>
				<FacetMultiSelect
					title="Practice"
					options={practices.options}
					selected={search.practiceSlug ?? []}
					onChange={(values) => onPatch({ practiceSlug: nonEmpty(values) })}
					disabled={practices.isLoading}
					emptyLabel={practices.isError ? "Could not load practices" : "No practices available"}
				/>
				<FacetMultiSelect
					title="Result"
					options={ASSESSMENT_OPTIONS}
					selected={search.assessment ?? []}
					onChange={(values) => onPatch({ assessment: nonEmpty(values) })}
				/>
				<FacetMultiSelect
					title="Severity"
					options={SEVERITY_OPTIONS}
					selected={search.severity ?? []}
					onChange={(values) => onPatch({ severity: nonEmpty(values) })}
				/>
				<FacetMultiSelect
					title="Practice status"
					options={PRESENCE_OPTIONS}
					selected={search.presence ?? []}
					onChange={(values) => onPatch({ presence: nonEmpty(values) })}
				/>
				<ReviewPersonFacet
					title="Developer"
					people={people}
					selected={search.subjectUserId}
					onChange={(subjectUserId) => onPatch({ subjectUserId })}
					fallbackName={subjectName}
				/>
				{/* "Observed", not "Date": this range filters `observedAt`, while the same control on
				    Delivery filters when the feedback was composed. */}
				<DateRangeFacet
					title="Observed"
					value={toDateRange(search)}
					onChange={(range) => onPatch(fromDateRange(range))}
				/>
			</div>
			<AppliedFacetPills
				pills={[
					...facetPills("Area", areas.options, search.areaSlug, (values) =>
						onPatch({ areaSlug: nonEmpty(values) }),
					),
					...facetPills("Practice", practices.options, search.practiceSlug, (values) =>
						onPatch({ practiceSlug: nonEmpty(values) }),
					),
					...facetPills("Result", ASSESSMENT_OPTIONS, search.assessment, (values) =>
						onPatch({ assessment: nonEmpty(values) }),
					),
					...facetPills("Severity", SEVERITY_OPTIONS, search.severity, (values) =>
						onPatch({ severity: nonEmpty(values) }),
					),
					...facetPills("Practice status", PRESENCE_OPTIONS, search.presence, (values) =>
						onPatch({ presence: nonEmpty(values) }),
					),
				]}
			/>
			{search.agentJobId && (
				<ReferenceFilterPill
					label="Review"
					value={search.agentJobId}
					onClear={() => onPatch({ agentJobId: undefined })}
				/>
			)}
			{search.artifactKind && (
				<ReferenceFilterPill
					label="Reviewed work"
					value={reviewArtifactScopeLabel(search.artifactKind, search.artifactId, scopedArtifact)}
					onClear={() => onPatch({ artifactKind: undefined, artifactId: undefined })}
				/>
			)}
		</FilterToolbar>
	);
}

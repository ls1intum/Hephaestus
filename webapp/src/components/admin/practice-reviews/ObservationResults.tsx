import { Link } from "@tanstack/react-router";
import { ScanSearchIcon } from "lucide-react";
import type { Practice, ReviewObservation } from "@/api/types.gen";
import { RelativeTime } from "@/components/common/RelativeTime";
import { observationResult } from "@/components/practice-vocabulary/observation-result";
import { Button } from "@/components/ui/button";
import {
	Empty,
	EmptyContent,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
import { ReviewArtifactLabel } from "./ReviewArtifact";
import {
	ClaimCurrentnessBadge,
	FeedbackCountsSummary,
	ObservationOriginBadge,
	ObservationResultBadge,
} from "./ReviewBadges";
import { ReviewPerson } from "./ReviewPerson";
import { ReviewPracticeLink } from "./ReviewPracticeLink";
import { ReviewResultsSkeleton } from "./ReviewResultsSkeleton";
import { ReviewRow, ReviewRowList, ReviewRowMeta } from "./ReviewRow";
import { REVIEW_PAGE_SIZE, type ReviewScopeSearch } from "./review-search";

/**
 * `onClearFilters` is part of the filtered-empty variant rather than an optional prop, so a filtered
 * empty state cannot be rendered without the control that gets the reader out of it.
 */
export type ObservationResultsState =
	| { status: "loading" }
	| { status: "empty"; filtered: false }
	| { status: "empty"; filtered: true; onClearFilters: () => void }
	| { status: "ready"; observations: ReviewObservation[] };

export interface ObservationResultsProps {
	workspaceSlug: string;
	state: ObservationResultsState;
	/**
	 * The workspace's practices, which the rows' practice links show as a hover card. A row carries a
	 * practice's slug, name and area but not its prose, so the list is the join the card needs; the
	 * screen fetches it once and every row reads the record it names out of it. Optional because
	 * nothing the card shows is load-bearing — a caller without the list still gets working links.
	 */
	practices?: Practice[];
}

export function ObservationResults({ workspaceSlug, state, practices }: ObservationResultsProps) {
	if (state.status === "loading")
		return <ReviewResultsSkeleton label="Loading observations" rows={REVIEW_PAGE_SIZE} />;
	if (state.status === "empty") {
		return (
			<Empty className="border">
				<EmptyHeader>
					<EmptyMedia variant="icon">
						<ScanSearchIcon />
					</EmptyMedia>
					<EmptyTitle>
						{state.filtered ? "No observations match these filters" : "No observations yet"}
					</EmptyTitle>
					<EmptyDescription>
						{state.filtered
							? "Every filter still applies. Clear them to see the whole list, or narrow one at a time."
							: "Observations appear after a practice review completes."}
					</EmptyDescription>
				</EmptyHeader>
				{state.filtered && (
					<EmptyContent>
						<Button variant="outline" size="sm" onClick={state.onClearFilters}>
							Clear all filters
						</Button>
					</EmptyContent>
				)}
			</Empty>
		);
	}

	// Named "Observations" and not "Observations, newest first": this is the one list whose order the
	// reader chooses, and a label naming an ordering the toolbar can change is wrong half the time.
	return (
		<ReviewRowList label="Observations">
			{state.observations.map((observation) => (
				<ObservationRow
					key={observation.id}
					workspaceSlug={workspaceSlug}
					observation={observation}
					practice={practices?.find((practice) => practice.slug === observation.practiceSlug)}
				/>
			))}
		</ReviewRowList>
	);
}

export interface ObservationRowProps {
	workspaceSlug: string;
	observation: ReviewObservation;
	/** The record behind `observation.practiceSlug`, which the practice link shows as a hover card. */
	practice?: Practice;
	/**
	 * What the link carries into the detail screen. Omitted on the Observations list, where the whole
	 * current search is carried forward so the reader's filters survive the round trip; passed on the
	 * review and reviewed-work screens, whose own search params mean nothing on this route.
	 */
	scope?: ReviewScopeSearch;
}

/**
 * The feedback tally stays a sentence on the meta line rather than becoming badges: coloured counts
 * on every row would drown the two badges that mean something — a shortfall's severity, and an
 * observation judged against a practice that has since changed.
 */
export function ObservationRow({
	workspaceSlug,
	observation,
	practice,
	scope,
}: ObservationRowProps) {
	return (
		<ReviewRow
			status={observationResult(observation)}
			title={
				<Link
					to="/w/$workspaceSlug/admin/practices/reviews/observations/$observationId"
					params={{ workspaceSlug, observationId: observation.id }}
					search={scope ?? ((previous) => previous)}
				>
					{observation.title}
				</Link>
			}
			meta={
				<>
					<ReviewRowMeta
						items={[
							<ReviewPracticeLink
								key="practice"
								workspaceSlug={workspaceSlug}
								practiceSlug={observation.practiceSlug}
								practiceName={observation.practiceName}
								area={observation.area}
								practice={practice}
							/>,
							<ReviewArtifactLabel key="work" artifact={observation.artifact} />,
							// No tooltip inside a row: the title link is stretched over the whole row, so a
							// hover target underneath it can be neither hovered nor clicked. The exact
							// instant is one click away, on the observation itself.
							<RelativeTime key="observed" value={observation.observedAt} tooltip={false} />,
						]}
					/>
					<p>
						<FeedbackCountsSummary counts={observation.feedbackDisposition} prefix="Feedback:" />
					</p>
				</>
			}
			chips={[
				// The two flags fire on unusual rows only, so they are free chips: reserving their width
				// spent it on every row to align a column that was blank most of the way down. Passing
				// them first puts them left of the reserved columns, which therefore keep their x
				// whether or not a flag fired. See {@link ReviewRowChip}.
				{
					key: "flags",
					node: (
						<>
							<ClaimCurrentnessBadge currentness={observation.claimCurrentness} />
							<ObservationOriginBadge origin={observation.origin} />
						</>
					),
				},
				{ key: "person", width: "lg:w-36", node: <ReviewPerson person={observation.subject} /> },
				// Severity rides in the result's slot rather than one of its own: it exists only where
				// the result is a shortfall, so its own column would be blank on every other row, and it
				// is read as a qualifier of the result — beside it where it fits, wrapped under it where
				// it does not, which costs no height in a row this tall.
				{
					key: "result",
					width: "lg:w-44",
					node: <ObservationResultBadge observation={observation} />,
				},
			]}
		/>
	);
}

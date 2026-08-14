import { Link } from "@tanstack/react-router";
import { ScanSearchIcon } from "lucide-react";
import type { ReviewObservation } from "@/api/types.gen";
import { RelativeTime } from "@/components/common/RelativeTime";
import { observationResult } from "@/components/practice-vocabulary/observation-result";
import {
	Empty,
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
import type { ReviewScopeSearch } from "./review-search";

export type ObservationResultsState =
	| { status: "loading" }
	| { status: "empty"; filtered: boolean }
	| { status: "ready"; observations: ReviewObservation[] };

export interface ObservationResultsProps {
	workspaceSlug: string;
	state: ObservationResultsState;
}

/**
 * The observations a filter selected, as one row each.
 *
 * <p>Replaces `FindingResults`, which rendered the same list twice — a four-column table above `xl`
 * and a card list below it — with different fields in each and a skeleton matching neither.
 */
export function ObservationResults({ workspaceSlug, state }: ObservationResultsProps) {
	if (state.status === "loading") return <ReviewResultsSkeleton label="Loading observations" />;
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
							? "Try removing a filter to broaden the results."
							: "Observations appear after a practice review completes."}
					</EmptyDescription>
				</EmptyHeader>
			</Empty>
		);
	}

	return (
		<ReviewRowList label="Observations, newest first">
			{state.observations.map((observation) => (
				<ObservationRow
					key={observation.id}
					workspaceSlug={workspaceSlug}
					observation={observation}
				/>
			))}
		</ReviewRowList>
	);
}

export interface ObservationRowProps {
	workspaceSlug: string;
	observation: ReviewObservation;
	/**
	 * What the link carries into the detail screen. Omitted on the Observations list, where the whole
	 * current search is carried forward so the reader's filters survive the round trip; passed on the
	 * review and reviewed-work screens, whose own search params mean nothing on this route.
	 */
	scope?: ReviewScopeSearch;
}

/**
 * One observation: what it concluded, what it says, which practice and whose work.
 *
 * <p>The practice is a link into its definition with the prose behind it on hover, which is the one
 * thing a reader of an observation cannot otherwise get to — every review surface named a practice
 * and none of them reached one. The area's colour rides on that link rather than taking a line of
 * its own.
 *
 * <p>The feedback tally stays a sentence on the meta line rather than becoming badges. Five coloured
 * counts on every row would drown the two badges that mean something: a shortfall's severity, and an
 * observation judged against a practice that has since changed.
 */
export function ObservationRow({ workspaceSlug, observation, scope }: ObservationRowProps) {
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
			chips={
				<>
					<ReviewPerson person={observation.subject} />
					<ObservationResultBadge observation={observation} />
					<ClaimCurrentnessBadge currentness={observation.claimCurrentness} />
					<ObservationOriginBadge origin={observation.origin} />
				</>
			}
		/>
	);
}

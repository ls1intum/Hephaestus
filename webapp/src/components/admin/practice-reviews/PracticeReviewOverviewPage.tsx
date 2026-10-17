import type { ReactNode } from "react";

import type { Practice } from "@/api/types.gen";
import {
	REVIEW_RANGE_DEFS,
	type ReviewRange,
	REVIEW_RANGE_OPTIONS,
} from "@/components/admin/practice-reviews/review-range";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { RangeControls } from "@/components/common/RangeControls";
import { Section } from "@/components/layout/Section";

import type {
	DeliveryBreakdownReads,
	ReviewAttentionReads,
} from "@/hooks/use-practice-review-overview";
import { DeliveryBreakdownCard } from "./DeliveryBreakdownCard";
import { deliveryFailures, finishedInRange } from "./overview-counts";
import { type PracticeCountsFilter, PracticeCountsTable } from "./PracticeCountsTable";
import type { OutcomeScope } from "./review-outcomes";
import type { PreviousReviewPeriodState, ReviewOverviewState } from "./review-states";
import { ReviewAttention } from "./ReviewAttention";
import { ReviewPipeline } from "./ReviewPipeline";
import { ReviewQueueCard } from "./ReviewQueueCard";
import type { ReviewQueueState } from "./ReviewQueueNow";
import { ReviewTabIntro } from "./ReviewTabIntro";
import type { ReviewTimingState } from "./ReviewTimeCard";

export interface PracticeReviewOverviewPageProps {
	workspaceSlug: string;
	range: ReviewRange;
	onRangeChange: (range: ReviewRange) => void;
	/** The range as the lists' day filters, so every count opens the rows it counts. */
	scope: OutcomeScope;
	overview: ReviewOverviewState;
	/** The period before the range, which the stages' totals are set against. */
	previous: PreviousReviewPeriodState;
	/** The decisions an admin owes, whenever the feedback was composed. */
	attention: ReviewAttentionReads;
	delivery: DeliveryBreakdownReads;
	/** How long the range's reviews took, read apart from the overview. */
	timing: ReviewTimingState;
	/** The queue as it stands now, whatever the range, above the review time. */
	queue: ReviewQueueState;
	/** The workspace's practices, for how many recorded nothing; absent while they load. */
	practices: Practice[] | undefined;
	/** Leads the page: whatever stops reviews from running. */
	banner?: ReactNode;
	/** The Practices table's filters, as the route keeps them in the URL. */
	practicesFilter?: PracticeCountsFilter;
}

/**
 * The home of Practice reviews, in the order an admin's questions come: can reviews run, must I act,
 * what is in the queue and how long reviews take, what the reviews did, what reached the developers,
 * and which practices did it. Each area is a few cards, each card one question. The range heads the
 * page because it governs everything below it except what the queue holds now, which is read as it
 * stands and says so, and the approvals, which are owed whenever they were composed.
 */
export function PracticeReviewOverviewPage({
	workspaceSlug,
	range,
	onRangeChange,
	scope,
	overview,
	previous,
	attention,
	delivery,
	timing,
	queue,
	practices,
	banner,
	practicesFilter,
}: PracticeReviewOverviewPageProps) {
	const rangeDef = REVIEW_RANGE_DEFS[range];
	const finished = finishedInRange(overview, scope);
	return (
		<div className="space-y-10">
			<div className="space-y-4">
				<div className="flex flex-wrap items-center justify-between gap-2">
					<ReviewTabIntro tab="overview" />
					<RangeControls
						options={REVIEW_RANGE_OPTIONS}
						range={range}
						onRangeChange={onRangeChange}
						updating={overview.status === "ready" && overview.stale}
					/>
				</div>
				{banner}
			</div>
			<ReviewAttention {...attention} />
			<Section
				title="Queue"
				size="lg"
				description={`What waits and runs now. What finished and how long reviews took ${rangeDef.during}.`}
			>
				<ReviewQueueCard
					workspaceSlug={workspaceSlug}
					queue={queue}
					timing={timing}
					finished={finished}
					label={rangeDef.label}
					during={rangeDef.during}
				/>
			</Section>
			<Section title="What the reviews did" size="lg" description={rangeDef.label}>
				{overview.status === "error" ? (
					<QueryErrorAlert
						error={overview.error}
						title="We could not load what the reviews did"
						onRetry={overview.onRetry}
					/>
				) : (
					<ReviewPipeline
						workspaceSlug={workspaceSlug}
						state={overview}
						previous={previous}
						previousName={rangeDef.previous}
						scope={scope}
						stages={["reviews", "results"]}
					/>
				)}
			</Section>
			{/* The Delivery cards count from the overview too: its failure is said once, above. */}
			{overview.status !== "error" && (
				<Section title="Delivery" size="lg" description={rangeDef.label}>
					<ReviewPipeline
						aria-label="Delivery"
						workspaceSlug={workspaceSlug}
						state={overview}
						previous={previous}
						previousName={rangeDef.previous}
						scope={scope}
						stages={["feedback"]}
						lastTile={
							<DeliveryBreakdownCard
								workspaceSlug={workspaceSlug}
								scope={scope}
								range={rangeDef}
								overview={overview}
								delivered={delivery.delivered}
								withheld={delivery.withheld}
								failedDeliveries={deliveryFailures(scope, overview)}
							/>
						}
					/>
				</Section>
			)}
			{overview.status !== "error" && (
				<Section
					title="Practices"
					size="lg"
					description={`How the reviews judged each practice ${rangeDef.during}, the ones that need a look first.`}
				>
					<PracticeCountsTable state={overview} practices={practices} filter={practicesFilter} />
				</Section>
			)}
		</div>
	);
}

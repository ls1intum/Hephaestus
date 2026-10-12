import { MessageSquareTextIcon } from "lucide-react";

import { cn } from "cn";
import type { ReviewFeedbackCounts, ReviewFeedbackLocationCounts } from "@/api/types.gen";
import { InlineLink } from "@/components/common/InlineLink";
import type { PanelState } from "@/components/common/panel-state";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { statusToneClass, statusValues } from "@/components/common/status-def";
import { Section } from "@/components/layout/Section";
import {
	DELIVERY_FAMILY_DEFS,
	DELIVERY_FAMILY_STATES,
	type DeliveryFamily,
} from "@/components/practice-vocabulary/delivery-family-defs";
import {
	FEEDBACK_LOCATION_DEFS,
	type FeedbackLocation,
} from "@/components/practice-vocabulary/feedback-location-defs";
import {
	Empty,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
import { Skeleton } from "@/components/ui/skeleton";
import {
	Table,
	TableBody,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
} from "@/components/ui/table";

import { OutcomeMix } from "./OutcomeMix";
import {
	familyCount,
	feedbackSlots,
	OUTCOME_LEGEND_LABELS,
	type ReviewListTarget,
	slotsTotal,
} from "./review-outcomes";
import { ReviewListLink } from "./ReviewListLink";

export interface FeedbackRecipientSummaryProps {
	workspaceSlug: string;
	recipientUserId: number;
	/** The developer's name; absent until the people or a row of theirs have arrived. */
	recipientName: string | undefined;
	/** Their feedback by location and delivery state, every location with none left out. */
	state: PanelState<{ counts: ReviewFeedbackLocationCounts[] }>;
}

/**
 * The delivery families a row of the table spells out. Awaiting approval and unconfirmed feedback
 * count in the row's total and in the mix above it, which is where a reader meets them.
 */
const COLUMNS = ["DELIVERED", "PREPARED", "WITHHELD", "FAILED"] as const satisfies DeliveryFamily[];

/** Below the table's `@md` the total and the delivered count stay; the list behind them has the rest. */
const SECONDARY = "hidden @md:table-cell";

/**
 * All the feedback addressed to one developer, whatever the list is filtered to: how it was
 * delivered, and where it appears, by provider. Every count opens the list filtered to exactly the
 * feedback it counts. Admins see the counts and never the words of private feedback.
 */
export function FeedbackRecipientSummary({
	workspaceSlug,
	recipientUserId,
	recipientName,
	state,
}: FeedbackRecipientSummaryProps) {
	const who = recipientName ?? "this developer";
	return (
		<Section
			level={3}
			title={`Feedback for ${who}`}
			description="Everything addressed to them, by delivery and by channel. Each count opens the feedback it counts."
		>
			<SummaryBody
				workspaceSlug={workspaceSlug}
				recipientUserId={recipientUserId}
				who={who}
				state={state}
			/>
		</Section>
	);
}

function SummaryBody({
	workspaceSlug,
	recipientUserId,
	who,
	state,
}: Omit<FeedbackRecipientSummaryProps, "recipientName"> & { who: string }) {
	if (state.status === "error") {
		return (
			<QueryErrorAlert
				error={state.error}
				title={`We could not count the feedback for ${who}`}
				onRetry={state.onRetry}
			/>
		);
	}
	if (state.status === "loading") {
		return <SummarySkeleton />;
	}
	const rows = statusValues(FEEDBACK_LOCATION_DEFS).flatMap((location) => {
		const row = state.counts.find((counts) => counts.location === location);
		return row ? [row] : [];
	});
	if (rows.length === 0) {
		return (
			<Empty variant="outlined">
				<EmptyHeader>
					<EmptyMedia variant="icon">
						<MessageSquareTextIcon />
					</EmptyMedia>
					<EmptyTitle>No feedback for {who} yet</EmptyTitle>
					<EmptyDescription>
						Feedback addressed to them appears here after reviews run.
					</EmptyDescription>
				</EmptyHeader>
			</Empty>
		);
	}
	let all = emptyCounts();
	for (const row of rows) {
		all = addCounts(all, row.feedback);
	}
	const families = feedbackSlots(all, { recipientUserId });
	return (
		<div className="space-y-4">
			<OutcomeMix
				total={slotsTotal(families)}
				slots={families}
				noun={(count) => (count === 1 ? "piece of feedback" : "pieces of feedback")}
				label={OUTCOME_LEGEND_LABELS.feedback}
			/>
			<div className="@container rounded-xl border bg-card">
				<Table>
					<TableHeader>
						<TableRow variant="static">
							<TableHead>Channel</TableHead>
							<TableHead numeric className="text-right">
								Feedback
							</TableHead>
							{COLUMNS.map((family, index) => (
								<TableHead
									key={family}
									numeric
									className={cn(index > 0 && SECONDARY, "text-right")}
								>
									{DELIVERY_FAMILY_DEFS[family].label}
								</TableHead>
							))}
						</TableRow>
					</TableHeader>
					<TableBody>
						{rows.map((row) => (
							<LocationRow
								key={row.location}
								workspaceSlug={workspaceSlug}
								recipientUserId={recipientUserId}
								row={row}
							/>
						))}
					</TableBody>
				</Table>
			</div>
		</div>
	);
}

function LocationRow({
	workspaceSlug,
	recipientUserId,
	row,
}: {
	workspaceSlug: string;
	recipientUserId: number;
	row: ReviewFeedbackLocationCounts;
}) {
	const def = FEEDBACK_LOCATION_DEFS[row.location];
	const Icon = def.icon;
	const location: FeedbackLocation[] = [row.location];
	const total = slotsTotal(feedbackSlots(row.feedback));
	return (
		<TableRow variant="static">
			<TableCell className="whitespace-normal">
				<span className="inline-flex min-w-0 items-center gap-2">
					<Icon
						aria-hidden
						className={cn("size-3.5 shrink-0", statusToneClass(def.badgeVariant))}
					/>
					<span className="min-w-0 break-words">{def.label}</span>
				</span>
			</TableCell>
			<TableCell numeric className="text-right">
				<CountLink
					workspaceSlug={workspaceSlug}
					count={total}
					target={feedbackTarget(recipientUserId, { location })}
				/>
			</TableCell>
			{COLUMNS.map((family, index) => (
				<TableCell key={family} numeric className={cn(index > 0 && SECONDARY, "text-right")}>
					<CountLink
						workspaceSlug={workspaceSlug}
						count={familyCount(row.feedback, family)}
						target={feedbackTarget(recipientUserId, {
							location,
							deliveryState: [...DELIVERY_FAMILY_STATES[family]],
						})}
					/>
				</TableCell>
			))}
		</TableRow>
	);
}

/** A count that opens the rows it counts; a zero opens nothing and reads muted. */
function CountLink({
	workspaceSlug,
	count,
	target,
}: {
	workspaceSlug: string;
	count: number;
	target: ReviewListTarget;
}) {
	if (count === 0) {
		return <span className="text-muted-foreground">0</span>;
	}
	return (
		<InlineLink
			tone="count"
			render={<ReviewListLink workspaceSlug={workspaceSlug} destination={target} />}
		>
			{count}
		</InlineLink>
	);
}

/** The Feedback list filtered to this developer and nothing else but `search`. */
function feedbackTarget(
	recipientUserId: number,
	search: Extract<ReviewListTarget, { list: "feedback" }>["search"],
): ReviewListTarget {
	return { list: "feedback", search: { recipientUserId, ...search } };
}

function SummarySkeleton() {
	return (
		<div className="space-y-4" aria-hidden>
			<div className="space-y-2">
				<Skeleton className="h-7 w-40" />
				<Skeleton className="h-4 w-64" />
			</div>
			<div className="space-y-2 rounded-xl border bg-card p-3">
				{Array.from({ length: 3 }, (_, index) => (
					<Skeleton key={index} className="h-5 w-full" />
				))}
			</div>
		</div>
	);
}

function emptyCounts(): ReviewFeedbackCounts {
	return {
		awaitingApproval: 0,
		prepared: 0,
		partiallyDelivered: 0,
		partiallyFailed: 0,
		delivered: 0,
		superseded: 0,
		suppressed: 0,
		failed: 0,
		discarded: 0,
		unconfirmed: 0,
	};
}

function addCounts(a: ReviewFeedbackCounts, b: ReviewFeedbackCounts): ReviewFeedbackCounts {
	return {
		awaitingApproval: a.awaitingApproval + b.awaitingApproval,
		prepared: a.prepared + b.prepared,
		partiallyDelivered: a.partiallyDelivered + b.partiallyDelivered,
		partiallyFailed: a.partiallyFailed + b.partiallyFailed,
		delivered: a.delivered + b.delivered,
		superseded: a.superseded + b.superseded,
		suppressed: a.suppressed + b.suppressed,
		failed: a.failed + b.failed,
		discarded: a.discarded + b.discarded,
		unconfirmed: a.unconfirmed + b.unconfirmed,
	};
}

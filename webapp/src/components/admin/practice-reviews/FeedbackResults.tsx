import { Link } from "@tanstack/react-router";
import { MessageSquareTextIcon } from "lucide-react";
import type { ReviewFeedback } from "@/api/types.gen";
import { RelativeTime } from "@/components/common/RelativeTime";
import { deliveryOutcome } from "@/components/practice-vocabulary/delivery-outcome-defs";
import { DELIVERY_PLACE_DEFS } from "@/components/practice-vocabulary/delivery-place-defs";
import { StatusBadge } from "@/components/practice-vocabulary/StatusBadge";
import { withholdingReasonSentence } from "@/components/practice-vocabulary/withholding-defs";
import {
	Empty,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
import { feedbackPreviewText } from "./feedback-preview";
import { ReviewArtifactLabel } from "./ReviewArtifact";
import { ReviewPerson } from "./ReviewPerson";
import { ReviewResultsSkeleton } from "./ReviewResultsSkeleton";
import { ReviewRow, ReviewRowList, ReviewRowMeta } from "./ReviewRow";
import type { ReviewScopeSearch } from "./review-search";

export type FeedbackResultsState =
	| { status: "loading" }
	| { status: "empty"; filtered: boolean }
	| { status: "ready"; feedback: ReviewFeedback[] };

export interface FeedbackResultsProps {
	workspaceSlug: string;
	state: FeedbackResultsState;
}

export function FeedbackResults({ workspaceSlug, state }: FeedbackResultsProps) {
	if (state.status === "loading") return <ReviewResultsSkeleton label="Loading feedback" />;
	if (state.status === "empty") {
		return (
			<Empty className="border">
				<EmptyHeader>
					<EmptyMedia variant="icon">
						<MessageSquareTextIcon />
					</EmptyMedia>
					<EmptyTitle>
						{state.filtered ? "No feedback matches these filters" : "No feedback yet"}
					</EmptyTitle>
					<EmptyDescription>
						{state.filtered
							? "Try removing a filter to broaden the results."
							: "Delivered and withheld feedback appears here after reviews run."}
					</EmptyDescription>
				</EmptyHeader>
			</Empty>
		);
	}

	return (
		<ReviewRowList label="Feedback, newest first">
			{state.feedback.map((item) => (
				<FeedbackRow key={item.id} workspaceSlug={workspaceSlug} feedback={item} />
			))}
		</ReviewRowList>
	);
}

export interface FeedbackRowProps {
	workspaceSlug: string;
	feedback: ReviewFeedback;
	/** See `ObservationRow`: the list carries its filters forward, a scoped section carries its scope. */
	scope?: ReviewScopeSearch;
}

/**
 * One piece of feedback: what it says, where it was headed, and what became of it.
 *
 * <p>The row's name is the feedback's own opening words. It used to be "Feedback for Ada Lovelace",
 * computed from the recipient — so a page of twenty-five rows was twenty-five near-identical titles
 * and the only distinguishing text was a clamped preview *below* the link. The person is a chip on
 * the right, where it is scanned rather than read, and the preview is promoted to the one thing that
 * tells two rows apart.
 *
 * <p>Place and outcome stay separate, as everywhere else: the badge says what happened, the meta
 * line says where it was going. A withheld row also carries its own precise reason, because the
 * outcome badge only says that something stopped it.
 */
export function FeedbackRow({ workspaceSlug, feedback, scope }: FeedbackRowProps) {
	const place = DELIVERY_PLACE_DEFS[feedback.channel];
	return (
		<ReviewRow
			status={deliveryOutcome(feedback)}
			title={
				<Link
					to="/w/$workspaceSlug/admin/practices/reviews/delivery/$feedbackId"
					params={{ workspaceSlug, feedbackId: feedback.id }}
					search={scope ?? ((previous) => previous)}
					className="line-clamp-2"
				>
					{/* Flattened, not printed raw: the wire preview is 320 characters of the Markdown note,
					    which lands inside a fenced code block on any feedback of ordinary length. Feedback
					    that opens straight into that block has a body but no prose to show for it, which
					    is not the same state as feedback nobody has composed yet. */}
					{feedbackPreviewText(feedback) ??
						(feedback.bodyPreview
							? "Opens with a quote from the work…"
							: "No feedback text was composed")}
				</Link>
			}
			meta={
				<>
					<ReviewRowMeta
						items={[
							place.label,
							<ReviewArtifactLabel key="work" artifact={feedback.artifact} />,
							`${feedback.observationCount} ${feedback.observationCount === 1 ? "observation" : "observations"}`,
							// See `ObservationRow`: no hover target under a stretched row link.
							<RelativeTime key="composed" value={feedback.createdAt} tooltip={false} />,
						]}
					/>
					{feedback.suppressionReason && (
						<p>{withholdingReasonSentence(feedback.suppressionReason)}</p>
					)}
				</>
			}
			chips={
				<>
					<ReviewPerson person={feedback.recipient} />
					<StatusBadge def={deliveryOutcome(feedback)} />
				</>
			}
		/>
	);
}

import { Link } from "@tanstack/react-router";
import { MessageSquareTextIcon, ScanSearchIcon } from "lucide-react";
import { useId } from "react";
import type { AgentJob, ReviewFeedback, ReviewObservation } from "@/api/types.gen";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import {
	Empty,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
import type { KnownArtifactKind } from "@/lib/artifact-kinds";
import { FeedbackRow } from "./FeedbackResults";
import { ObservationRow } from "./ObservationResults";
import { ReviewResultsSkeleton } from "./ReviewResultsSkeleton";
import { ReviewRowList } from "./ReviewRow";

export type ReviewSectionState<T> =
	| { status: "loading" }
	| { status: "error"; error: unknown; onRetry: () => void }
	| { status: "pending" }
	| { status: "ready"; items: T[]; total: number };

/**
 * Says what a review declined to do, in place of what it found.
 *
 * Deliberately not "nothing was found": the review never got as far as looking, so reading its empty
 * result as a clean bill of health would be exactly backwards. The product's name is not in it —
 * an operator wants to know what happened to the work, not which service shrugged.
 */
const INSUFFICIENT_EVIDENCE_EXPLANATION =
	"The review stopped before it assessed anything, because the material it needed was missing, unreadable, out of date, or not something it was allowed to read. No practice was judged — this is not a review that looked and found nothing.";

export interface ReviewOutputScope {
	agentJobId?: string;
	/** An artifact of a kind this build does not know still renders; it just cannot scope a link. */
	artifactKind?: KnownArtifactKind;
	artifactId?: number;
}

export interface ReviewOutputSectionsProps {
	workspaceSlug: string;
	scope: ReviewOutputScope;
	feedback: ReviewSectionState<ReviewFeedback>;
	observations: ReviewSectionState<ReviewObservation>;
	/**
	 * Distinguishes "looked and found nothing" from "declined to look". Omitted by aggregate views,
	 * which span several runs and so have no single outcome.
	 */
	outcome?: AgentJob["reviewOutcome"];
}

/**
 * What one review, or one piece of work, produced — the same two sections in the same order on both
 * screens that show them.
 *
 * <p>Each section renders the very rows the corresponding list renders. They used to be a third and
 * fourth hand-built row layout, with their own titles, their own field order and no severity on an
 * observation, so the same record looked like a different kind of thing depending on which screen
 * had reached it. The `context` prop that decided whether to print a timestamp is gone with them:
 * both screens show one, because a row without a date is a row you cannot place.
 */
export function ReviewOutputSections({
	workspaceSlug,
	scope,
	feedback,
	observations,
	outcome,
}: ReviewOutputSectionsProps) {
	return (
		<>
			<ObservationsSection
				workspaceSlug={workspaceSlug}
				scope={scope}
				state={observations}
				outcome={outcome}
			/>
			<FeedbackSection
				workspaceSlug={workspaceSlug}
				scope={scope}
				state={feedback}
				outcome={outcome}
			/>
		</>
	);
}

function FeedbackSection({
	workspaceSlug,
	scope,
	state,
	outcome,
}: {
	outcome?: AgentJob["reviewOutcome"];
	workspaceSlug: string;
	scope: ReviewOutputScope;
	state: ReviewSectionState<ReviewFeedback>;
}) {
	const items = state.status === "ready" ? state.items : [];
	const headingId = useId();
	return (
		<section aria-labelledby={headingId} className="space-y-3">
			<SectionHeader
				id={headingId}
				title="Feedback"
				to="/w/$workspaceSlug/admin/practices/reviews/delivery"
				workspaceSlug={workspaceSlug}
				scope={scope}
				total={state.status === "ready" ? state.total : 0}
				shown={items.length}
			/>
			{state.status === "loading" ? (
				<ReviewResultsSkeleton label="Loading feedback" rows={2} />
			) : state.status === "error" ? (
				<QueryErrorAlert
					error={state.error}
					title="Couldn't load feedback"
					onRetry={state.onRetry}
				/>
			) : state.status === "pending" ? (
				<p className="text-sm text-muted-foreground">
					Feedback will appear when the review finishes.
				</p>
			) : items.length === 0 ? (
				<Empty className="border">
					<EmptyHeader>
						<EmptyMedia variant="icon">
							<MessageSquareTextIcon />
						</EmptyMedia>
						<EmptyTitle>
							{outcome === "INSUFFICIENT_EVIDENCE" ? "Nothing was assessed" : "No feedback"}
						</EmptyTitle>
						{outcome === "INSUFFICIENT_EVIDENCE" && (
							<EmptyDescription>{INSUFFICIENT_EVIDENCE_EXPLANATION}</EmptyDescription>
						)}
					</EmptyHeader>
				</Empty>
			) : (
				<ReviewRowList label="Feedback">
					{items.map((item) => (
						<FeedbackRow
							key={item.id}
							workspaceSlug={workspaceSlug}
							feedback={item}
							scope={scope}
						/>
					))}
				</ReviewRowList>
			)}
		</section>
	);
}

function ObservationsSection({
	workspaceSlug,
	scope,
	state,
	outcome,
}: {
	outcome?: AgentJob["reviewOutcome"];
	workspaceSlug: string;
	scope: ReviewOutputScope;
	state: ReviewSectionState<ReviewObservation>;
}) {
	const items = state.status === "ready" ? state.items : [];
	const headingId = useId();
	return (
		<section aria-labelledby={headingId} className="space-y-3">
			<SectionHeader
				id={headingId}
				title="Observations"
				to="/w/$workspaceSlug/admin/practices/reviews/observations"
				workspaceSlug={workspaceSlug}
				scope={scope}
				total={state.status === "ready" ? state.total : 0}
				shown={items.length}
			/>
			{state.status === "loading" ? (
				<ReviewResultsSkeleton label="Loading observations" rows={2} />
			) : state.status === "error" ? (
				<QueryErrorAlert
					error={state.error}
					title="Couldn't load observations"
					onRetry={state.onRetry}
				/>
			) : state.status === "pending" ? (
				<p className="text-sm text-muted-foreground">
					Observations will appear when the review finishes.
				</p>
			) : items.length === 0 ? (
				<Empty className="border">
					<EmptyHeader>
						<EmptyMedia variant="icon">
							<ScanSearchIcon />
						</EmptyMedia>
						<EmptyTitle>
							{outcome === "INSUFFICIENT_EVIDENCE"
								? "Nothing was assessed"
								: "No observations were recorded"}
						</EmptyTitle>
						{outcome === "INSUFFICIENT_EVIDENCE" && (
							<EmptyDescription>{INSUFFICIENT_EVIDENCE_EXPLANATION}</EmptyDescription>
						)}
					</EmptyHeader>
				</Empty>
			) : (
				<ReviewRowList label="Observations">
					{items.map((observation) => (
						<ObservationRow
							key={observation.id}
							workspaceSlug={workspaceSlug}
							observation={observation}
							scope={scope}
						/>
					))}
				</ReviewRowList>
			)}
		</section>
	);
}

interface SectionHeaderProps {
	id: string;
	title: string;
	to:
		| "/w/$workspaceSlug/admin/practices/reviews/delivery"
		| "/w/$workspaceSlug/admin/practices/reviews/observations";
	workspaceSlug: string;
	scope: ReviewOutputScope;
	total: number;
	shown: number;
}

function SectionHeader({ id, title, to, workspaceSlug, scope, total, shown }: SectionHeaderProps) {
	return (
		<div className="flex flex-wrap items-end justify-between gap-2">
			<h3 id={id} className="text-lg font-semibold">
				{title}
			</h3>
			{total > shown && (
				<Link
					className="text-sm font-medium underline underline-offset-4"
					to={to}
					params={{ workspaceSlug }}
					search={scope}
				>
					See all {total} {title.toLowerCase()}
				</Link>
			)}
		</div>
	);
}

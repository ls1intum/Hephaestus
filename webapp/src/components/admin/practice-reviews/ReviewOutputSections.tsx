import { Link } from "@tanstack/react-router";
import { MessageSquareTextIcon, ScanSearchIcon } from "lucide-react";
import { useId } from "react";
import type { AgentJob, ReviewFeedback, ReviewObservation } from "@/api/types.gen";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
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
import {
	Item,
	ItemContent,
	ItemDescription,
	ItemFooter,
	ItemGroup,
	ItemTitle,
} from "@/components/ui/item";
import { Spinner } from "@/components/ui/spinner";
import type { KnownArtifactKind } from "@/lib/artifact-kinds";
import { ClaimCurrentnessBadge, FindingResultBadge } from "./ReviewBadges";
import { ReviewPracticeLabel } from "./ReviewPracticeLabel";
import { subjectLabel } from "./review-format";

export type ReviewSectionState<T> =
	| { status: "loading" }
	| { status: "error"; error: unknown; onRetry: () => void }
	| { status: "pending" }
	| { status: "ready"; items: T[]; total: number };

const INSUFFICIENT_EVIDENCE_EXPLANATION =
	"Hephaestus skipped automated review because required evidence was missing, unreadable, out of date, or not authorized. No practice was assessed, so this is not a result of finding nothing.";

export interface ReviewOutputScope {
	agentJobId?: string;
	/** An artifact of a kind this build does not know still renders; it just cannot scope a link. */
	artifactKind?: KnownArtifactKind;
	artifactId?: number;
}

export interface ReviewOutputSectionsProps {
	workspaceSlug: string;
	scope: ReviewOutputScope;
	context: "review" | "target";
	feedback: ReviewSectionState<ReviewFeedback>;
	findings: ReviewSectionState<ReviewObservation>;
	/**
	 * Distinguishes "looked and found nothing" from "declined to look". Omitted by aggregate views,
	 * which span several runs and so have no single outcome.
	 */
	outcome?: AgentJob["reviewOutcome"];
}

export function ReviewOutputSections({
	workspaceSlug,
	scope,
	context,
	feedback,
	findings,
	outcome,
}: ReviewOutputSectionsProps) {
	return (
		<>
			<FindingsSection
				workspaceSlug={workspaceSlug}
				scope={scope}
				context={context}
				state={findings}
				outcome={outcome}
			/>
			<FeedbackSection
				workspaceSlug={workspaceSlug}
				scope={scope}
				context={context}
				state={feedback}
				outcome={outcome}
			/>
		</>
	);
}

function FeedbackSection({
	workspaceSlug,
	scope,
	context,
	state,
	outcome,
}: {
	outcome?: AgentJob["reviewOutcome"];
	workspaceSlug: string;
	scope: ReviewOutputScope;
	context: "review" | "target";
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
				<Spinner aria-label="Loading feedback" />
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
				<ItemGroup>
					{items.map((item) => (
						<div key={item.id} role="listitem">
							<Item
								variant="outline"
								render={
									<Link
										to="/w/$workspaceSlug/admin/practices/reviews/delivery/$feedbackId"
										params={{ workspaceSlug, feedbackId: item.id }}
										search={scope}
									/>
								}
							>
								<ItemContent className="min-w-0">
									<ItemTitle className="w-full min-w-0 line-clamp-none break-words">
										Feedback for {subjectLabel(item.recipient)}
									</ItemTitle>
									{item.bodyPreview && (
										<ItemDescription className="line-clamp-2 text-sm text-foreground">
											{item.bodyPreview}
										</ItemDescription>
									)}
									<p className="text-xs text-muted-foreground">
										{DELIVERY_PLACE_DEFS[item.channel].label}
										{item.suppressionReason &&
											` · ${withholdingReasonSentence(item.suppressionReason)}`}
									</p>
									{context === "target" && (
										<span className="text-xs text-muted-foreground">
											Composed <RelativeTime value={item.createdAt} />
										</span>
									)}
								</ItemContent>
								<ItemFooter className="justify-start sm:basis-auto sm:justify-end">
									<StatusBadge def={deliveryOutcome(item)} />
								</ItemFooter>
							</Item>
						</div>
					))}
				</ItemGroup>
			)}
		</section>
	);
}

function FindingsSection({
	workspaceSlug,
	scope,
	context,
	state,
	outcome,
}: {
	outcome?: AgentJob["reviewOutcome"];
	workspaceSlug: string;
	scope: ReviewOutputScope;
	context: "review" | "target";
	state: ReviewSectionState<ReviewObservation>;
}) {
	const items = state.status === "ready" ? state.items : [];
	const headingId = useId();
	return (
		<section aria-labelledby={headingId} className="space-y-3">
			<SectionHeader
				id={headingId}
				title="Observations"
				to="/w/$workspaceSlug/admin/practices/reviews/findings"
				workspaceSlug={workspaceSlug}
				scope={scope}
				total={state.status === "ready" ? state.total : 0}
				shown={items.length}
			/>
			{state.status === "loading" ? (
				<Spinner aria-label="Loading observations" />
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
				<ItemGroup>
					{items.map((finding) => (
						<div key={finding.id} role="listitem">
							<Item
								variant="outline"
								render={
									<Link
										to="/w/$workspaceSlug/admin/practices/reviews/findings/$findingId"
										params={{ workspaceSlug, findingId: finding.id }}
										search={scope}
									/>
								}
							>
								<ItemContent className="min-w-0">
									<ItemTitle className="w-full min-w-0 line-clamp-none break-words">
										{finding.title}
									</ItemTitle>
									<ReviewPracticeLabel area={finding.area} practiceName={finding.practiceName} />
									{context === "target" && (
										<span className="text-xs text-muted-foreground">
											Observed <RelativeTime value={finding.observedAt} />
										</span>
									)}
								</ItemContent>
								<ItemFooter className="justify-start sm:basis-auto sm:justify-end">
									<FindingResultBadge finding={finding} />
									<ClaimCurrentnessBadge currentness={finding.claimCurrentness} />
								</ItemFooter>
							</Item>
						</div>
					))}
				</ItemGroup>
			)}
		</section>
	);
}

interface SectionHeaderProps {
	id: string;
	title: string;
	to:
		| "/w/$workspaceSlug/admin/practices/reviews/delivery"
		| "/w/$workspaceSlug/admin/practices/reviews/findings";
	workspaceSlug: string;
	scope: ReviewOutputScope;
	total: number;
	shown: number;
}

function SectionHeader({ id, title, to, workspaceSlug, scope, total, shown }: SectionHeaderProps) {
	return (
		<div className="flex flex-wrap items-end justify-between gap-2">
			<div>
				<h3 id={id} className="text-lg font-semibold">
					{title}
				</h3>
			</div>
			{total > shown && (
				<Link
					className="text-sm font-medium underline"
					to={to}
					params={{ workspaceSlug }}
					search={scope}
				>
					View all {total} {title.toLowerCase()}
				</Link>
			)}
		</div>
	);
}

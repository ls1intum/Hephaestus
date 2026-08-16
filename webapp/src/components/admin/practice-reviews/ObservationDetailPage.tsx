import { Link } from "@tanstack/react-router";
import { MessageSquareTextIcon } from "lucide-react";
import type { GetPracticeReviewObservationResponse, Practice } from "@/api/types.gen";
import { MissingRecordEmpty } from "@/components/common/MissingRecordEmpty";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
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
import { Spinner } from "@/components/ui/spinner";
import { ObservationEvidence } from "./ObservationEvidence";
import { ReviewArtifactLink, reviewArtifactTypeSlug } from "./ReviewArtifact";
import {
	ClaimCurrentnessAlert,
	ClaimCurrentnessBadge,
	ObservationResultBadge,
} from "./ReviewBadges";
import { ReviewBreadcrumbs } from "./ReviewBreadcrumbs";
import {
	ReviewDetailHeader,
	ReviewFact,
	ReviewFactGrid,
	ReviewProvenanceLine,
} from "./ReviewDetailHeader";
import { ReviewPerson } from "./ReviewPerson";
import { ReviewPracticeLink } from "./ReviewPracticeLink";
import { ReviewRow, ReviewRowList, ReviewRowMeta } from "./ReviewRow";
import { confidenceLabel } from "./review-format";
import { type ObservationsSearch, reviewScopeSearch } from "./review-search";

export interface ObservationDetailPageProps {
	workspaceSlug: string;
	/** What the reader was filtering on the Observations list, carried into the links out of here. */
	search: ObservationsSearch;
	/** The record this page is about, or `undefined` while it is unknown. */
	observation: GetPracticeReviewObservationResponse | undefined;
	isLoading: boolean;
	error: unknown;
	onRetry?: () => void;
	/**
	 * The workspace's practices, which the practice this was judged against shows as a hover card.
	 * Optional in effect: a page without it still links, it just cannot show the prose.
	 */
	practices: Practice[] | undefined;
}

export function ObservationDetailPage({
	workspaceSlug,
	search,
	observation,
	isLoading,
	error,
	onRetry,
	practices,
}: ObservationDetailPageProps) {
	const breadcrumbs = (
		<ReviewBreadcrumbs
			workspaceSlug={workspaceSlug}
			section={{
				label: "Observations",
				link: (
					<Link
						to="/w/$workspaceSlug/admin/practices/reviews/observations"
						params={{ workspaceSlug }}
						search={(previous) => previous}
					/>
				),
			}}
		/>
	);

	if (isLoading)
		return (
			<article className="min-w-0 max-w-4xl space-y-8">
				{breadcrumbs}
				<div className="flex min-h-64 items-center justify-center">
					<Spinner className="size-7" />
				</div>
			</article>
		);
	if (error) {
		return (
			<article className="min-w-0 max-w-4xl space-y-8">
				{breadcrumbs}
				<QueryErrorAlert error={error} title="Couldn't load this observation" onRetry={onRetry} />
			</article>
		);
	}
	// Not the alert: no record and no error is a query that never came back, and the alert would read
	// the absent status as a lost connection. See `MissingRecordEmpty`.
	if (!observation) {
		return (
			<article className="min-w-0 max-w-4xl space-y-8">
				{breadcrumbs}
				<MissingRecordEmpty title="This observation hasn't loaded" onRetry={onRetry} />
			</article>
		);
	}
	// No slug means a kind this build has no route for; the artifact still renders, unlinked.
	const artifactSlug = observation.artifact
		? reviewArtifactTypeSlug(observation.artifact.type)
		: undefined;

	return (
		<article className="min-w-0 max-w-4xl space-y-8">
			{breadcrumbs}
			<ReviewDetailHeader
				chips={
					<>
						<ObservationResultBadge observation={observation} />
						<ClaimCurrentnessBadge currentness={observation.claimCurrentness} />
					</>
				}
				title={observation.title}
				provenance={
					<ReviewProvenanceLine
						workspaceSlug={workspaceSlug}
						agentJobId={observation.agentJobId}
						verb="Observed"
						at={observation.observedAt}
					/>
				}
			/>
			<ClaimCurrentnessAlert currentness={observation.claimCurrentness} />

			<ReviewFactGrid>
				<ReviewFact label="Practice">
					<ReviewPracticeLink
						workspaceSlug={workspaceSlug}
						practiceSlug={observation.practiceSlug}
						practiceName={observation.practiceName}
						area={observation.area}
						practice={practices?.find((practice) => practice.slug === observation.practiceSlug)}
					/>
				</ReviewFact>
				<ReviewFact label="Developer">
					<ReviewPerson person={observation.subject} />
				</ReviewFact>
				<ReviewFact label="Reviewed work">
					<div className="space-y-1">
						<ReviewArtifactLink artifact={observation.artifact} />
						<p className="break-words text-muted-foreground">{observation.artifact.title}</p>
						{artifactSlug && (
							<Link
								className="font-medium underline underline-offset-4"
								to="/w/$workspaceSlug/admin/practices/reviews/targets/$artifactKind/$artifactId"
								params={{
									workspaceSlug,
									artifactKind: artifactSlug,
									artifactId: String(observation.artifact.id),
								}}
							>
								See everything reviewed on this work
							</Link>
						)}
					</div>
				</ReviewFact>
				<ReviewFact label="Confidence">{confidenceLabel(observation.confidence)}</ReviewFact>
			</ReviewFactGrid>

			{observation.reasoning && (
				<section aria-labelledby="reasoning-heading" className="space-y-2">
					<h3 id="reasoning-heading" className="text-lg font-semibold">
						Why this was raised
					</h3>
					<p className="whitespace-pre-wrap text-sm leading-relaxed">{observation.reasoning}</p>
				</section>
			)}

			<section aria-labelledby="evidence-heading" className="space-y-3">
				<h3 id="evidence-heading" className="text-lg font-semibold">
					Evidence
				</h3>
				<ObservationEvidence
					evidence={observation.evidence}
					detector={observation.evidence?.detector}
				/>
			</section>

			<section aria-labelledby="linked-feedback-heading" className="space-y-3">
				<h3 id="linked-feedback-heading" className="text-lg font-semibold">
					Feedback from this observation
				</h3>
				{observation.feedback.length === 0 ? (
					<Empty className="border">
						<EmptyHeader>
							<EmptyMedia variant="icon">
								<MessageSquareTextIcon />
							</EmptyMedia>
							<EmptyTitle>Nothing was said to anybody about this</EmptyTitle>
							<EmptyDescription>
								The observation was recorded and no feedback was composed from it.
							</EmptyDescription>
						</EmptyHeader>
					</Empty>
				) : (
					<ReviewRowList label="Feedback from this observation">
						{observation.feedback.map((feedback) => (
							<ReviewRow
								key={feedback.feedbackId}
								status={deliveryOutcome(feedback)}
								title={
									<Link
										to="/w/$workspaceSlug/admin/practices/reviews/delivery/$feedbackId"
										params={{ workspaceSlug, feedbackId: feedback.feedbackId }}
										search={reviewScopeSearch(search)}
									>
										{/* Named by what it is to *this* observation. Titling it with the delivery
										    place would say nothing about the thing the link opens, and repeat
										    the fact the meta line beside it already carries. */}
										{feedback.role === "PRIMARY"
											? "Feedback about this observation"
											: "Feedback this observation supports"}
									</Link>
								}
								// The place is plain text in the meta line, exactly where `FeedbackRow` puts it,
								// and only the outcome is a chip. Side by side the two collided: on the
								// conversation lane a delivered unit drew `BotMessageSquareIcon` twice, under
								// "In conversation" and "Delivered in conversation" — and the second is word for
								// word a refinement of the first, because every lane-specific outcome label must
								// begin with the state it refines. Two rows built from one record should not have
								// two layouts either.
								meta={
									<>
										<ReviewRowMeta items={[DELIVERY_PLACE_DEFS[feedback.channel].label]} />
										{feedback.suppressionReason && (
											<p>{withholdingReasonSentence(feedback.suppressionReason)}</p>
										)}
									</>
								}
								chips={[
									{
										key: "outcome",
										width: "lg:w-48",
										node: <StatusBadge def={deliveryOutcome(feedback)} />,
									},
								]}
							/>
						))}
					</ReviewRowList>
				)}
			</section>
		</article>
	);
}

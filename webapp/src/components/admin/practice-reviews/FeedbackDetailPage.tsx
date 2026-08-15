import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { ScanSearchIcon } from "lucide-react";
import { getPracticeReviewFeedbackOptions } from "@/api/@tanstack/react-query.gen";
import type { ReviewPlacement } from "@/api/types.gen";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { DeliveryTrace } from "@/components/practice-vocabulary/DeliveryTrace";
import { codeCitationLocator } from "@/components/practice-vocabulary/evidence-source-defs";
import { observationResult } from "@/components/practice-vocabulary/observation-result";
import { PLACEMENT_DEFS } from "@/components/practice-vocabulary/placement-defs";
import {
	Empty,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
import { Spinner } from "@/components/ui/spinner";
import { FeedbackBody } from "./FeedbackBody";
import { ReviewArtifactLink, reviewArtifactTypeSlug } from "./ReviewArtifact";
import { ClaimCurrentnessBadge, ObservationResultBadge } from "./ReviewBadges";
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
import { subjectLabel } from "./review-format";
import { type FeedbackSearch, reviewScopeSearch } from "./review-search";

export interface FeedbackDetailPageProps {
	workspaceSlug: string;
	feedbackId: string;
	search: FeedbackSearch;
}

export function FeedbackDetailPage({ workspaceSlug, feedbackId, search }: FeedbackDetailPageProps) {
	const query = useQuery({
		...getPracticeReviewFeedbackOptions({ path: { workspaceSlug, feedbackId } }),
	});
	const breadcrumbs = (
		<ReviewBreadcrumbs
			workspaceSlug={workspaceSlug}
			section={{
				label: "Delivery",
				link: (
					<Link
						to="/w/$workspaceSlug/admin/practices/reviews/delivery"
						params={{ workspaceSlug }}
						search={(previous) => previous}
					/>
				),
			}}
		/>
	);

	if (query.isLoading)
		return (
			<article className="min-w-0 max-w-4xl space-y-8">
				{breadcrumbs}
				<div className="flex min-h-64 items-center justify-center">
					<Spinner className="size-7" />
				</div>
			</article>
		);
	if (query.isError || !query.data) {
		return (
			<article className="min-w-0 max-w-4xl space-y-8">
				{breadcrumbs}
				<QueryErrorAlert
					error={query.error}
					title="Couldn't load this feedback"
					onRetry={() => query.refetch()}
				/>
			</article>
		);
	}
	const feedback = query.data;
	const subjectDiffers = feedback.subject && feedback.subject.id !== feedback.recipient?.id;
	// No slug means a kind this build has no route for; the artifact still renders, unlinked.
	const artifactSlug = feedback.artifact
		? reviewArtifactTypeSlug(feedback.artifact.type)
		: undefined;
	const anchoredPlacements = feedback.placements.filter((placement) => placement.anchorPath);

	return (
		<article className="min-w-0 max-w-4xl space-y-8">
			{breadcrumbs}
			{/* No outcome chip in the header: the trace below states it, and the card around the text
			    states it again for text that was not sent. */}
			<ReviewDetailHeader
				title={`Feedback for ${subjectLabel(feedback.recipient)}`}
				provenance={
					<ReviewProvenanceLine
						workspaceSlug={workspaceSlug}
						agentJobId={feedback.agentJobId}
						verb="Composed"
						at={feedback.createdAt}
					/>
				}
			/>

			<ReviewFactGrid>
				<ReviewFact label={subjectDiffers ? "Addressed to" : "Developer"}>
					<div className="space-y-1">
						<ReviewPerson person={feedback.recipient} />
						{subjectDiffers && <ReviewPerson person={feedback.subject} prefix="About" />}
					</div>
				</ReviewFact>
				<ReviewFact label="Reviewed work">
					<div className="space-y-1">
						<ReviewArtifactLink artifact={feedback.artifact} />
						{feedback.artifact && (
							<p className="break-words text-muted-foreground">{feedback.artifact.title}</p>
						)}
						{feedback.artifact && artifactSlug && (
							<Link
								className="font-medium underline underline-offset-4"
								to="/w/$workspaceSlug/admin/practices/reviews/targets/$artifactKind/$artifactId"
								params={{
									workspaceSlug,
									artifactKind: artifactSlug,
									artifactId: String(feedback.artifact.id),
								}}
							>
								See everything reviewed on this work
							</Link>
						)}
					</div>
				</ReviewFact>
			</ReviewFactGrid>

			<section aria-labelledby="feedback-body-heading" className="space-y-3">
				<h3 id="feedback-body-heading" className="text-lg font-semibold">
					What it says
				</h3>
				<FeedbackBody feedback={feedback} />
			</section>

			<section aria-labelledby="delivery-heading" className="space-y-3">
				<h3 id="delivery-heading" className="text-lg font-semibold">
					What became of it
				</h3>
				<DeliveryTrace feedback={feedback} />
				{anchoredPlacements.length > 0 && (
					<div className="space-y-1 rounded-lg border p-3">
						<p className="text-sm font-medium">Where it was anchored</p>
						<ul className="space-y-1">
							{anchoredPlacements.map((placement) => (
								<li key={placement.id} className="text-sm text-muted-foreground">
									<span>{PLACEMENT_DEFS[placement.placementType].label}: </span>
									<code className="break-all">{anchorLabel(placement)}</code>
								</li>
							))}
						</ul>
					</div>
				)}
				{feedback.replacesId && (
					<p className="text-sm text-muted-foreground">
						<Link
							to="/w/$workspaceSlug/admin/practices/reviews/delivery/$feedbackId"
							params={{ workspaceSlug, feedbackId: feedback.replacesId }}
							search={reviewScopeSearch(search)}
							className="font-medium text-foreground underline underline-offset-4"
						>
							See the feedback this replaced
						</Link>
					</p>
				)}
			</section>

			<section aria-labelledby="source-observations-heading" className="space-y-3">
				<h3 id="source-observations-heading" className="text-lg font-semibold">
					What it was based on
				</h3>
				{feedback.observations.length === 0 ? (
					<Empty className="border">
						<EmptyHeader>
							<EmptyMedia variant="icon">
								<ScanSearchIcon />
							</EmptyMedia>
							<EmptyTitle>No observations are linked to this feedback</EmptyTitle>
							<EmptyDescription>
								The observations behind it were not recorded, so there is nothing to check it
								against.
							</EmptyDescription>
						</EmptyHeader>
					</Empty>
				) : (
					<ReviewRowList label="Observations behind this feedback">
						{feedback.observations.map((observation) => (
							<ReviewRow
								key={observation.observationId}
								status={observationResult(observation)}
								title={
									<Link
										to="/w/$workspaceSlug/admin/practices/reviews/observations/$observationId"
										params={{ workspaceSlug, observationId: observation.observationId }}
										search={reviewScopeSearch(search)}
									>
										{observation.title}
									</Link>
								}
								meta={
									<ReviewRowMeta
										items={[
											<ReviewPracticeLink
												key="practice"
												workspaceSlug={workspaceSlug}
												practiceSlug={observation.practiceSlug}
												practiceName={observation.practiceName}
												area={observation.area}
											/>,
											observation.role === "PRIMARY"
												? "What this feedback is about"
												: "Supporting this feedback",
										]}
									/>
								}
								chips={[
									{
										key: "result",
										width: "lg:w-64",
										node: <ObservationResultBadge observation={observation} />,
									},
									{
										key: "currentness",
										width: "lg:w-44",
										node: <ClaimCurrentnessBadge currentness={observation.claimCurrentness} />,
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

/**
 * The file and lines an inline note is anchored to; only inline placements have one.
 *
 * A `FILE` anchor carries a path and no line, so it stays a bare path rather than picking up a
 * coordinate it does not have.
 */
function anchorLabel(placement: ReviewPlacement): string {
	const { anchorPath, anchorStartLine, anchorEndLine } = placement;
	if (!anchorPath || !anchorStartLine) return anchorPath ?? "";
	return codeCitationLocator({
		path: anchorPath,
		startLine: anchorStartLine,
		endLine: anchorEndLine,
	});
}

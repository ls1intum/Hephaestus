import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { ScanSearchIcon } from "lucide-react";
import { getPracticeReviewFeedbackOptions } from "@/api/@tanstack/react-query.gen";
import type { ReviewPlacement } from "@/api/types.gen";
import { DetailRow } from "@/components/common/DetailRow";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { RelativeTime } from "@/components/common/RelativeTime";
import { DeliveryTrace } from "@/components/practice-vocabulary/DeliveryTrace";
import { PLACEMENT_DEFS } from "@/components/practice-vocabulary/placement-defs";
import {
	Accordion,
	AccordionContent,
	AccordionItem,
	AccordionTrigger,
} from "@/components/ui/accordion";
import { Badge } from "@/components/ui/badge";
import { Empty, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty";
import { Item, ItemContent, ItemFooter, ItemGroup, ItemTitle } from "@/components/ui/item";
import { Spinner } from "@/components/ui/spinner";
import { FeedbackMessage } from "./FeedbackMessage";
import { ReviewArtifactLink, reviewArtifactTypeSlug } from "./ReviewArtifact";
import { ClaimCurrentnessBadge, FindingResultBadge } from "./ReviewBadges";
import { ReviewBreadcrumbs } from "./ReviewBreadcrumbs";
import { ReviewPerson } from "./ReviewPerson";
import { ReviewPracticeLabel } from "./ReviewPracticeLabel";
import { ReviewTechnicalDetails } from "./ReviewTechnicalDetails";
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
			current="Feedback"
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

	return (
		<article className="min-w-0 max-w-4xl space-y-8">
			{breadcrumbs}
			<header className="space-y-4">
				<div className="space-y-1">
					<h2 className="break-words text-2xl font-semibold tracking-tight">
						Feedback for {subjectLabel(feedback.recipient)}
					</h2>
					<p className="text-sm text-muted-foreground">
						Composed <RelativeTime value={feedback.createdAt} />
					</p>
				</div>
				<div className="grid gap-3 lg:grid-cols-2">
					{subjectDiffers && (
						<div className="space-y-2">
							<ReviewPerson person={feedback.recipient} prefix="To" display="full" />
							<ReviewPerson person={feedback.subject} prefix="About" display="full" />
						</div>
					)}
					<div className="min-w-0 space-y-2">
						<ReviewArtifactLink artifact={feedback.artifact} display="full" />
						{feedback.artifact && artifactSlug && (
							<Link
								className="text-xs font-medium underline"
								to="/w/$workspaceSlug/admin/practices/reviews/targets/$artifactKind/$artifactId"
								params={{
									workspaceSlug,
									artifactKind: artifactSlug,
									artifactId: String(feedback.artifact.id),
								}}
							>
								View all observations and feedback for this work
							</Link>
						)}
					</div>
				</div>
			</header>

			<section aria-labelledby="feedback-body-heading" className="space-y-3">
				<h3 id="feedback-body-heading" className="text-lg font-semibold">
					Feedback
				</h3>
				<FeedbackMessage feedback={feedback} />
				{feedback.body && (
					<Accordion aria-label="Feedback source">
						<AccordionItem value="source">
							<AccordionTrigger>View Markdown source</AccordionTrigger>
							<AccordionContent>
								<pre className="max-h-96 overflow-auto whitespace-pre-wrap rounded-md bg-muted p-4 text-xs">
									{feedback.body}
								</pre>
							</AccordionContent>
						</AccordionItem>
					</Accordion>
				)}
			</section>

			<section aria-labelledby="source-observations-heading" className="space-y-3">
				<div>
					<h3 id="source-observations-heading" className="text-lg font-semibold">
						Observations behind this feedback
					</h3>
				</div>
				{feedback.observations.length === 0 ? (
					<Empty className="border">
						<EmptyHeader>
							<EmptyMedia variant="icon">
								<ScanSearchIcon />
							</EmptyMedia>
							<EmptyTitle>No observations are linked to this feedback</EmptyTitle>
						</EmptyHeader>
					</Empty>
				) : (
					<ItemGroup>
						{feedback.observations.map((observation) => (
							<div key={observation.observationId} role="listitem">
								<Item
									variant="outline"
									render={
										<Link
											to="/w/$workspaceSlug/admin/practices/reviews/findings/$findingId"
											params={{ workspaceSlug, findingId: observation.observationId }}
											search={reviewScopeSearch(search)}
										/>
									}
								>
									<ItemContent className="min-w-0">
										<ItemTitle className="w-full min-w-0 line-clamp-none break-words">
											{observation.title}
										</ItemTitle>
										<ReviewPracticeLabel
											area={observation.area}
											practiceName={observation.practiceName}
										/>
									</ItemContent>
									<ItemFooter className="justify-start sm:basis-auto sm:justify-end">
										<Badge variant="outline">
											{observation.role === "PRIMARY"
												? "Main observation"
												: "Supporting observation"}
										</Badge>
										<FindingResultBadge finding={observation} />
										<ClaimCurrentnessBadge currentness={observation.claimCurrentness} />
									</ItemFooter>
								</Item>
							</div>
						))}
					</ItemGroup>
				)}
			</section>

			<section aria-labelledby="delivery-location-heading" className="space-y-3">
				<h3 id="delivery-location-heading" className="text-lg font-semibold">
					Delivery
				</h3>
				<DeliveryTrace feedback={feedback} />
				{feedback.placements.some((placement) => placement.anchorPath) && (
					<dl className="divide-y border-t">
						{feedback.placements.map((placement) =>
							placement.anchorPath ? (
								<DetailRow key={placement.id} label={PLACEMENT_DEFS[placement.placementType].label}>
									<code className="break-all">{anchorLabel(placement)}</code>
								</DetailRow>
							) : null,
						)}
					</dl>
				)}
			</section>

			<ReviewTechnicalDetails>
				<dl className="divide-y">
					<DetailRow label="Feedback ID">
						<code>{feedback.id}</code>
					</DetailRow>
					<DetailRow label="Review">
						<Link
							className="underline"
							to="/w/$workspaceSlug/admin/practices/reviews/$jobId"
							params={{ workspaceSlug, jobId: feedback.agentJobId }}
						>
							{feedback.agentJobId}
						</Link>
					</DetailRow>
					<DetailRow label="Subject">{subjectLabel(feedback.subject)}</DetailRow>
					{feedback.replacesId && (
						<DetailRow label="Replaces">
							<code>{feedback.replacesId}</code>
						</DetailRow>
					)}
					{feedback.threadKey && (
						<DetailRow label="Continuity key">
							<code>{feedback.threadKey}</code>
						</DetailRow>
					)}
				</dl>
			</ReviewTechnicalDetails>
		</article>
	);
}

/** The file and lines an inline note is anchored to; only inline placements have one. */
function anchorLabel(placement: ReviewPlacement): string {
	const { anchorPath, anchorStartLine, anchorEndLine } = placement;
	if (!anchorStartLine) return anchorPath ?? "";
	const span =
		anchorEndLine && anchorEndLine !== anchorStartLine
			? `${anchorStartLine}–${anchorEndLine}`
			: `${anchorStartLine}`;
	return `${anchorPath}:${span}`;
}

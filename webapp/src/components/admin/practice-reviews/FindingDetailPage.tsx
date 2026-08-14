import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { InfoIcon, MessageSquareTextIcon } from "lucide-react";
import { getPracticeReviewObservationOptions } from "@/api/@tanstack/react-query.gen";
import { DetailRow } from "@/components/common/DetailRow";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { RelativeTime } from "@/components/common/RelativeTime";
import { deliveryOutcome } from "@/components/practice-vocabulary/delivery-outcome-defs";
import { DELIVERY_PLACE_DEFS } from "@/components/practice-vocabulary/delivery-place-defs";
import { StatusBadge } from "@/components/practice-vocabulary/StatusBadge";
import { withholdingReasonSentence } from "@/components/practice-vocabulary/withholding-defs";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Empty, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty";
import {
	Item,
	ItemContent,
	ItemDescription,
	ItemFooter,
	ItemGroup,
	ItemTitle,
} from "@/components/ui/item";
import { Spinner } from "@/components/ui/spinner";
import { FindingEvidence } from "./FindingEvidence";
import { ReviewArtifactLink, reviewArtifactTypeSlug } from "./ReviewArtifact";
import { ClaimCurrentnessAlert, ClaimCurrentnessBadge, FindingResultBadge } from "./ReviewBadges";
import { ReviewBreadcrumbs } from "./ReviewBreadcrumbs";
import { ReviewPerson } from "./ReviewPerson";
import { ReviewPracticeLabel } from "./ReviewPracticeLabel";
import { ReviewTechnicalDetails } from "./ReviewTechnicalDetails";
import { confidenceLabel } from "./review-format";
import { type FindingsSearch, reviewScopeSearch } from "./review-search";

export interface FindingDetailPageProps {
	workspaceSlug: string;
	findingId: string;
	search: FindingsSearch;
}

export function FindingDetailPage({ workspaceSlug, findingId, search }: FindingDetailPageProps) {
	const query = useQuery({
		...getPracticeReviewObservationOptions({
			path: { workspaceSlug, observationId: findingId },
		}),
	});
	const breadcrumbs = (
		<ReviewBreadcrumbs
			workspaceSlug={workspaceSlug}
			section={{
				label: "Observations",
				link: (
					<Link
						to="/w/$workspaceSlug/admin/practices/reviews/findings"
						params={{ workspaceSlug }}
						search={(previous) => previous}
					/>
				),
			}}
			current="Observation"
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
					title="Couldn't load this observation"
					onRetry={() => query.refetch()}
				/>
			</article>
		);
	}
	const finding = query.data;
	// No slug means a kind this build has no route for; the artifact still renders, unlinked.
	const artifactSlug = finding.artifact ? reviewArtifactTypeSlug(finding.artifact.type) : undefined;

	return (
		<article className="min-w-0 max-w-4xl space-y-8">
			{breadcrumbs}
			<header className="space-y-4">
				<div className="space-y-2">
					<div className="flex flex-wrap items-center gap-2">
						<FindingResultBadge finding={finding} />
						<ClaimCurrentnessBadge currentness={finding.claimCurrentness} />
					</div>
					<h2 className="break-words text-2xl font-semibold tracking-tight">{finding.title}</h2>
					<ReviewPracticeLabel
						area={finding.area}
						practiceName={finding.practiceName}
						display="full"
					/>
				</div>
				<div className="grid gap-3 lg:grid-cols-2">
					<ReviewPerson person={finding.subject} display="full" />
					<div className="min-w-0 space-y-2">
						<ReviewArtifactLink artifact={finding.artifact} display="full" />
						{artifactSlug && (
							<Link
								className="text-xs font-medium underline"
								to="/w/$workspaceSlug/admin/practices/reviews/targets/$artifactKind/$artifactId"
								params={{
									workspaceSlug,
									artifactKind: artifactSlug,
									artifactId: String(finding.artifact.id),
								}}
							>
								View all observations and feedback for this work
							</Link>
						)}
					</div>
				</div>
			</header>
			<ClaimCurrentnessAlert currentness={finding.claimCurrentness} />

			<Alert>
				<InfoIcon />
				<AlertTitle>AI-generated observation</AlertTitle>
				<AlertDescription>Verify this observation against the evidence.</AlertDescription>
			</Alert>
			{finding.reasoning && (
				<section aria-labelledby="review-heading" className="space-y-2">
					<h3 id="review-heading" className="text-lg font-semibold">
						Hephaestus review
					</h3>
					<p className="whitespace-pre-wrap text-sm leading-relaxed">{finding.reasoning}</p>
				</section>
			)}
			<section aria-labelledby="evidence-heading" className="space-y-3">
				<h3 id="evidence-heading" className="text-lg font-semibold">
					Evidence
				</h3>
				<FindingEvidence evidence={finding.evidence} />
			</section>

			<section aria-labelledby="created-feedback-heading" className="space-y-3">
				<div>
					<h3 id="created-feedback-heading" className="text-lg font-semibold">
						Feedback created from this observation
					</h3>
				</div>
				{finding.feedback.length === 0 ? (
					<Empty className="border">
						<EmptyHeader>
							<EmptyMedia variant="icon">
								<MessageSquareTextIcon />
							</EmptyMedia>
							<EmptyTitle>No feedback was created</EmptyTitle>
						</EmptyHeader>
					</Empty>
				) : (
					<ItemGroup>
						{finding.feedback.map((feedback) => (
							<div key={feedback.feedbackId} role="listitem">
								<Item
									variant="outline"
									render={
										<Link
											to="/w/$workspaceSlug/admin/practices/reviews/delivery/$feedbackId"
											params={{ workspaceSlug, feedbackId: feedback.feedbackId }}
											search={reviewScopeSearch(search)}
										/>
									}
								>
									<ItemContent className="min-w-0">
										<ItemTitle className="w-full min-w-0">
											{DELIVERY_PLACE_DEFS[feedback.channel].label}
										</ItemTitle>
										<ItemDescription>
											Composed <RelativeTime value={feedback.createdAt} />
											{feedback.suppressionReason &&
												` · ${withholdingReasonSentence(feedback.suppressionReason)}`}
										</ItemDescription>
									</ItemContent>
									<ItemFooter className="justify-start sm:basis-auto sm:justify-end">
										<Badge variant="outline">
											{feedback.role === "PRIMARY" ? "Main observation" : "Supporting observation"}
										</Badge>
										<StatusBadge def={deliveryOutcome(feedback)} />
									</ItemFooter>
								</Item>
							</div>
						))}
					</ItemGroup>
				)}
			</section>

			<ReviewTechnicalDetails>
				<dl className="divide-y">
					<DetailRow label="Observation ID">
						<code>{finding.id}</code>
					</DetailRow>
					<DetailRow label="Review">
						<Link
							className="underline"
							to="/w/$workspaceSlug/admin/practices/reviews/$jobId"
							params={{ workspaceSlug, jobId: finding.agentJobId }}
						>
							{finding.agentJobId}
						</Link>
					</DetailRow>
					<DetailRow label="Observed">
						<RelativeTime value={finding.observedAt} />
					</DetailRow>
					<DetailRow label="Confidence">{confidenceLabel(finding.confidence)}</DetailRow>
					{finding.practiceRevisionId && (
						<DetailRow label="Criteria revision">{finding.practiceRevisionId}</DetailRow>
					)}
					{finding.recurrenceKey && (
						<DetailRow label="Recurrence key">
							<code>{finding.recurrenceKey}</code>
						</DetailRow>
					)}
					<DetailRow label="Raw evidence">
						<pre className="max-h-80 overflow-auto whitespace-pre-wrap text-xs">
							{JSON.stringify(finding.evidence, null, 2)}
						</pre>
					</DetailRow>
				</dl>
			</ReviewTechnicalDetails>
		</article>
	);
}

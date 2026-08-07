import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { MapPinIcon, ScanSearchIcon } from "lucide-react";
import { getPracticeReviewFeedbackOptions } from "@/api/@tanstack/react-query.gen";
import type { ReviewPlacement } from "@/api/types.gen";
import { DetailRow } from "@/components/common/DetailRow";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { RelativeTime } from "@/components/common/RelativeTime";
import {
	Accordion,
	AccordionContent,
	AccordionItem,
	AccordionTrigger,
} from "@/components/ui/accordion";
import { Badge } from "@/components/ui/badge";
import { Empty, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty";
import {
	Item,
	ItemContent,
	ItemDescription,
	ItemFooter,
	ItemGroup,
	ItemMedia,
	ItemTitle,
} from "@/components/ui/item";
import { Spinner } from "@/components/ui/spinner";
import { FeedbackMessage } from "./FeedbackMessage";
import { ReviewArtifactLink, reviewArtifactTypeSlug } from "./ReviewArtifact";
import { ClaimCurrentnessBadge, FindingResultBadge } from "./ReviewBadges";
import { ReviewBreadcrumbs } from "./ReviewBreadcrumbs";
import { ReviewPerson } from "./ReviewPerson";
import { ReviewPracticeLabel } from "./ReviewPracticeLabel";
import { ReviewTechnicalDetails } from "./ReviewTechnicalDetails";
import { CHANNEL_LABELS, PLACEMENT_TYPE_LABELS, subjectLabel } from "./review-format";
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
			current="Message"
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
					title="Couldn't load this message"
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
						Message for {subjectLabel(feedback.recipient)}
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
								View all findings and feedback for this work
							</Link>
						)}
					</div>
				</div>
			</header>

			<section aria-labelledby="feedback-message-heading" className="space-y-3">
				<h3 id="feedback-message-heading" className="text-lg font-semibold">
					Message
				</h3>
				<FeedbackMessage
					body={feedback.body}
					deliveryState={feedback.deliveryState}
					suppressionReason={feedback.suppressionReason}
				/>
				{feedback.body && (
					<Accordion aria-label="Message source">
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

			<section aria-labelledby="source-findings-heading" className="space-y-3">
				<div>
					<h3 id="source-findings-heading" className="text-lg font-semibold">
						Findings behind this message
					</h3>
				</div>
				{feedback.findings.length === 0 ? (
					<Empty className="border">
						<EmptyHeader>
							<EmptyMedia variant="icon">
								<ScanSearchIcon />
							</EmptyMedia>
							<EmptyTitle>No findings are linked to this message</EmptyTitle>
						</EmptyHeader>
					</Empty>
				) : (
					<ItemGroup>
						{feedback.findings.map((finding) => (
							<div key={finding.findingId} role="listitem">
								<Item
									variant="outline"
									render={
										<Link
											to="/w/$workspaceSlug/admin/practices/reviews/findings/$findingId"
											params={{ workspaceSlug, findingId: finding.findingId }}
											search={reviewScopeSearch(search)}
										/>
									}
								>
									<ItemContent className="min-w-0">
										<ItemTitle className="w-full min-w-0 line-clamp-none break-words">
											{finding.title}
										</ItemTitle>
										<ReviewPracticeLabel area={finding.area} practiceName={finding.practiceName} />
									</ItemContent>
									<ItemFooter className="justify-start sm:basis-auto sm:justify-end">
										<Badge variant="outline">
											{finding.role === "PRIMARY" ? "Main finding" : "Supporting finding"}
										</Badge>
										<FindingResultBadge finding={finding} />
										<ClaimCurrentnessBadge currentness={finding.claimCurrentness} />
									</ItemFooter>
								</Item>
							</div>
						))}
					</ItemGroup>
				)}
			</section>

			<section aria-labelledby="delivery-location-heading" className="space-y-3">
				<div>
					<h3 id="delivery-location-heading" className="text-lg font-semibold">
						Delivery
					</h3>
					<p className="text-sm text-muted-foreground">
						Channel: {CHANNEL_LABELS[feedback.channel]}
					</p>
				</div>
				{feedback.placements.length > 0 ? (
					<ItemGroup>
						{feedback.placements.map((placement) => (
							<PlacementItem key={placement.id} placement={placement} />
						))}
					</ItemGroup>
				) : (
					<p className="text-sm text-muted-foreground">This message was not posted.</p>
				)}
			</section>

			<ReviewTechnicalDetails>
				<dl className="divide-y">
					<DetailRow label="Message ID">
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
					<DetailRow label="Channel">{CHANNEL_LABELS[feedback.channel]}</DetailRow>
					{feedback.deliveredAt && (
						<DetailRow label="Delivered">
							<RelativeTime value={feedback.deliveredAt} />
						</DetailRow>
					)}
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

function PlacementItem({ placement }: { placement: ReviewPlacement }) {
	const location = placement.anchorPath
		? `${placement.anchorPath}${placement.anchorStartLine ? `:${placement.anchorStartLine}${placement.anchorEndLine && placement.anchorEndLine !== placement.anchorStartLine ? `–${placement.anchorEndLine}` : ""}` : ""}`
		: undefined;
	return (
		<Item variant="outline" role="listitem">
			<ItemMedia variant="icon">
				<MapPinIcon />
			</ItemMedia>
			<ItemContent>
				<ItemTitle>{PLACEMENT_TYPE_LABELS[placement.placementType]}</ItemTitle>
				{location && <ItemDescription>{location}</ItemDescription>}
			</ItemContent>
		</Item>
	);
}

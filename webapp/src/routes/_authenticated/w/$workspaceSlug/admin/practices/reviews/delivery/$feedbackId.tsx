import { useMutation, useQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { toast } from "sonner";
import {
	decideFeedbackProposalMutation,
	getPracticeReviewFeedbackOptions,
	listPracticesOptions,
} from "@/api/@tanstack/react-query.gen";
import { FeedbackDetailPage } from "@/components/admin/practice-reviews/FeedbackDetailPage";
import {
	type ProposalRejectionReason,
	ProposalReviewPage,
} from "@/components/admin/practice-reviews/ProposalReviewPage";
import { reviewArtifactLabel } from "@/components/admin/practice-reviews/ReviewArtifact";
import { subjectLabel } from "@/components/admin/practice-reviews/review-format";
import { placementLabel } from "@/components/practice-vocabulary/placement-defs";
import { workspaceAdminHead } from "@/lib/page-title";
import { problemDetailOf } from "@/lib/problem-detail";

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/practices/reviews/delivery/$feedbackId",
)({
	head: workspaceAdminHead("Feedback details"),
	component: FeedbackDetailRoute,
});

function FeedbackDetailRoute() {
	const { workspaceSlug, feedbackId } = Route.useParams();
	const search = Route.useSearch();

	const feedbackQueryResult = useQuery({
		...getPracticeReviewFeedbackOptions({ path: { workspaceSlug, feedbackId } }),
	});
	const practicesQuery = useQuery({ ...listPracticesOptions({ path: { workspaceSlug } }) });
	const decision = useMutation({
		...decideFeedbackProposalMutation(),
		onSuccess: async (_, variables) => {
			toast.success(
				variables.body.decision === "APPROVED"
					? "Feedback approved for delivery"
					: "Proposal rejected",
			);
			await feedbackQueryResult.refetch();
		},
		onError: (error) =>
			toast.error("Couldn't decide this proposal", { description: problemDetailOf(error) }),
	});

	const feedback = feedbackQueryResult.data;
	if (feedback?.deliveryState === "AWAITING_APPROVAL") {
		const firstPlacement = feedback.placements[0];
		return (
			<ProposalReviewPage
				proposal={{
					id: feedback.id,
					practiceNames: Array.from(
						new Set(feedback.observations.map((observation) => observation.practiceName)),
					),
					recipientName: subjectLabel(feedback.recipient),
					body: feedback.body ?? "No feedback text was composed.",
					artifact: feedback.artifact
						? {
								label: reviewArtifactLabel(feedback.artifact),
								title: feedback.artifact.title,
								repositoryName: feedback.artifact.repositoryName ?? "Repository unavailable",
								url: feedback.artifact.url,
							}
						: undefined,
					placement: placementLabel(feedback.channel, firstPlacement?.placementType),
					evidence: feedback.observations.map((observation) => ({
						id: observation.observationId,
						practiceName: observation.practiceName,
						excerpt: observation.summary,
						url: `/w/${workspaceSlug}/admin/practices/reviews/observations/${observation.observationId}`,
					})),
				}}
				isDeciding={decision.isPending}
				onApprove={(id) =>
					decision.mutate({
						path: { workspaceSlug, feedbackId: id },
						body: { decision: "APPROVED" },
					})
				}
				onReject={(id, rejectionReason?: ProposalRejectionReason) =>
					decision.mutate({
						path: { workspaceSlug, feedbackId: id },
						body: { decision: "REJECTED", rejectionReason },
					})
				}
			/>
		);
	}

	return (
		<FeedbackDetailPage
			workspaceSlug={workspaceSlug}
			search={search}
			feedback={feedback}
			isLoading={feedbackQueryResult.isLoading}
			error={feedbackQueryResult.isError ? feedbackQueryResult.error : undefined}
			onRetry={() => feedbackQueryResult.refetch()}
			practices={practicesQuery.data}
		/>
	);
}

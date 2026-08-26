import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { toast } from "sonner";
import {
	decideFeedbackProposalMutation,
	getPracticeReviewFeedbackOptions,
	getPracticeReviewFeedbackQueryKey,
	listPracticeReviewFeedbackQueryKey,
	listPracticesOptions,
} from "@/api/@tanstack/react-query.gen";
import { FeedbackDetailPage } from "@/components/admin/practice-reviews/FeedbackDetailPage";
import {
	type ProposalRejectionReason,
	ProposalReviewPage,
} from "@/components/admin/practice-reviews/ProposalReviewPage";
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
	const queryClient = useQueryClient();
	const detailKey = getPracticeReviewFeedbackQueryKey({ path: { workspaceSlug, feedbackId } });

	const feedbackQueryResult = useQuery({
		...getPracticeReviewFeedbackOptions({ path: { workspaceSlug, feedbackId } }),
		refetchInterval: (query) => {
			const feedback = query.state.data;
			return feedback?.deliveryState === "PREPARED" ||
				(feedback?.deliveryState === "PARTIALLY_DELIVERED" && !feedback.suppressionReason)
				? 2_000
				: false;
		},
	});
	const practicesQuery = useQuery({ ...listPracticesOptions({ path: { workspaceSlug } }) });
	const decision = useMutation({
		...decideFeedbackProposalMutation(),
		onSuccess: async (_, variables) => {
			toast.success(
				variables.body.decision === "APPROVED"
					? "Review approved. Delivery is being checked."
					: "Review rejected",
			);
			await Promise.all([
				queryClient.invalidateQueries({ queryKey: detailKey }),
				queryClient.invalidateQueries({
					queryKey: listPracticeReviewFeedbackQueryKey({ path: { workspaceSlug } }),
				}),
			]);
		},
		onError: (error) => {
			toast.error("Couldn't decide this review", { description: problemDetailOf(error) });
		},
	});

	const feedback = feedbackQueryResult.data;
	if (feedback?.deliveryState === "AWAITING_APPROVAL") {
		return (
			<ProposalReviewPage
				workspaceSlug={workspaceSlug}
				feedback={feedback}
				practices={practicesQuery.data}
				isDeciding={decision.isPending}
				onApprove={(id) =>
					decision.mutate({
						path: { workspaceSlug, feedbackId: id },
						body: { decision: "APPROVED" },
					})
				}
				onReject={(id, rejectionReason?: ProposalRejectionReason, rejectionNote?: string) =>
					decision.mutate({
						path: { workspaceSlug, feedbackId: id },
						body: { decision: "REJECTED", rejectionReason, rejectionNote },
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
			onRetry={() => void feedbackQueryResult.refetch()}
			practices={practicesQuery.data}
		/>
	);
}

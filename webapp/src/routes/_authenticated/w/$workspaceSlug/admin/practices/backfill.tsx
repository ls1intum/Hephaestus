import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { History } from "lucide-react";
import { toast } from "sonner";
import {
	listBackfillRunsOptions,
	listBackfillRunsQueryKey,
	preflightBackfillRunMutation,
	updateBackfillRunStatusMutation,
} from "@/api/@tanstack/react-query.gen";
import type { CreateReviewBackfillRunRequest } from "@/api/types.gen";
import { PracticeReviewBackfill } from "@/components/admin/practices/PracticeReviewBackfill";
import { PageHeader } from "@/components/core/PageHeader";
import { PageLayout } from "@/components/core/PageLayout";
import { workspaceAdminHead } from "@/lib/page-title";
import { problemDetailOf } from "@/lib/problem-detail";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/practices/backfill")({
	head: workspaceAdminHead("Review past work"),
	component: BackfillContainer,
});

/**
 * A running campaign advances in the background, so the page polls while one is live and stops as soon
 * as nothing is moving — refetching a settled list forever would cost more than the feature.
 */
const ACTIVE_POLL_MS = 15_000;

function BackfillContainer() {
	const queryClient = useQueryClient();
	const { workspaceSlug } = Route.useParams();
	const runsQueryKey = listBackfillRunsQueryKey({ path: { workspaceSlug } });

	const runsQuery = useQuery({
		...listBackfillRunsOptions({ path: { workspaceSlug } }),
		refetchInterval: (query) =>
			query.state.data?.some((run) => run.status === "RUNNING" || run.status === "PAUSED")
				? ACTIVE_POLL_MS
				: false,
	});

	const invalidate = () => queryClient.invalidateQueries({ queryKey: runsQueryKey });

	const preflight = useMutation({
		...preflightBackfillRunMutation(),
		onSuccess: () => {
			void invalidate();
		},
		onError: (error) => {
			toast.error("Couldn't estimate this backfill", { description: problemDetailOf(error) });
		},
	});

	const updateStatus = useMutation({
		...updateBackfillRunStatusMutation(),
		onSuccess: (run) => {
			void invalidate();
			if (run.status === "RUNNING") {
				toast.success("Backfill started");
			} else if (run.status === "CANCELLED") {
				toast.success("Backfill stopped");
			}
		},
		onError: (error) => {
			toast.error("Couldn't update this backfill", { description: problemDetailOf(error) });
		},
	});

	return (
		<PageLayout>
			<PageHeader
				icon={<History />}
				title="Review past work"
				description="Measure work that existed before practice reviews were switched on."
			/>
			<div className="max-w-3xl">
				<PracticeReviewBackfill
					runs={runsQuery.data ?? []}
					isLoading={runsQuery.isLoading}
					isError={runsQuery.isError}
					onRetry={() => void runsQuery.refetch()}
					isEstimating={preflight.isPending}
					onEstimate={(request: CreateReviewBackfillRunRequest) =>
						preflight.mutate({ path: { workspaceSlug }, body: request })
					}
					isUpdating={updateStatus.isPending}
					onConfirm={(runId) =>
						updateStatus.mutate({ path: { workspaceSlug, runId }, body: { status: "RUNNING" } })
					}
					onCancel={(runId) =>
						updateStatus.mutate({ path: { workspaceSlug, runId }, body: { status: "CANCELLED" } })
					}
				/>
			</div>
		</PageLayout>
	);
}

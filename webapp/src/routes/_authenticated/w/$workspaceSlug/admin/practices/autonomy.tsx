import { useQuery } from "@tanstack/react-query";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { Gauge } from "lucide-react";
import { z } from "zod";
import {
	getPracticeReviewSettingsOptions,
	listPracticesOptions,
	reviewTierRollupOptions,
} from "@/api/@tanstack/react-query.gen";
import { ReviewAutonomyPage } from "@/components/admin/practices/review-autonomy/ReviewAutonomyPage";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { PageHeader } from "@/components/core/PageHeader";
import { PageLayout } from "@/components/core/PageLayout";
import { Spinner } from "@/components/ui/spinner";
import { usePracticeReviewSettingsMutation } from "@/hooks/use-practice-review-settings";
import { useReviewAutonomyMutations } from "@/hooks/use-review-autonomy-mutations";
import { workspaceAdminHead } from "@/lib/page-title";

const autonomySearchSchema = z.object({
	overrides: z.boolean().optional().catch(undefined),
});

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/practices/autonomy")({
	head: workspaceAdminHead("Review autonomy"),
	validateSearch: autonomySearchSchema,
	component: ReviewAutonomyRoute,
});

function ReviewAutonomyRoute() {
	const { workspaceSlug } = Route.useParams();
	const { overrides } = Route.useSearch();
	const navigate = useNavigate({ from: Route.fullPath });

	const settingsQuery = useQuery({
		...getPracticeReviewSettingsOptions({ path: { workspaceSlug } }),
	});
	const rollupQuery = useQuery({ ...reviewTierRollupOptions({ path: { workspaceSlug } }) });
	const practicesQuery = useQuery({ ...listPracticesOptions({ path: { workspaceSlug } }) });

	const autonomy = useReviewAutonomyMutations(workspaceSlug);
	const updateSettings = usePracticeReviewSettingsMutation(workspaceSlug, {
		success: "Review settings updated",
		error: "Failed to update review settings",
	});

	const pendingQueries =
		settingsQuery.isPending || rollupQuery.isPending || practicesQuery.isPending;
	const error = settingsQuery.error ?? rollupQuery.error ?? practicesQuery.error;

	return (
		<PageLayout>
			<PageHeader
				icon={<Gauge />}
				title="Review autonomy"
				description="Decide how much Hephaestus does on its own — once for the workspace, or as an exception for an area or a single practice."
			/>
			{pendingQueries ? (
				<div className="flex h-64 items-center justify-center">
					<Spinner className="size-8" />
				</div>
			) : error || !settingsQuery.data || !rollupQuery.data || !practicesQuery.data ? (
				<QueryErrorAlert
					error={error}
					title="Couldn't load the review tiers"
					onRetry={() => {
						settingsQuery.refetch();
						rollupQuery.refetch();
						practicesQuery.refetch();
					}}
				/>
			) : (
				<ReviewAutonomyPage
					workspaceSlug={workspaceSlug}
					settings={settingsQuery.data}
					rollup={rollupQuery.data}
					practices={practicesQuery.data}
					pending={{
						workspace: updateSettings.isPending,
						areaSlugs: autonomy.pendingAreaSlugs,
						practiceSlugs: autonomy.pendingPracticeSlugs,
						bulk: autonomy.bulk,
					}}
					overridesOnly={overrides === true}
					onOverridesOnlyChange={(next) =>
						navigate({ search: { overrides: next ? true : undefined } })
					}
					onSetWorkspaceDefault={(defaultReviewTier) =>
						updateSettings.mutate({ path: { workspaceSlug }, body: { defaultReviewTier } })
					}
					onClearWorkspaceDefault={() =>
						updateSettings.mutate({
							path: { workspaceSlug },
							body: { reset: ["DEFAULT_REVIEW_TIER"] },
						})
					}
					onSetFeedbackReach={(feedbackReach) =>
						updateSettings.mutate({ path: { workspaceSlug }, body: { feedbackReach } })
					}
					onClearFeedbackReach={() =>
						updateSettings.mutate({ path: { workspaceSlug }, body: { reset: ["FEEDBACK_REACH"] } })
					}
					onSetAreaTier={(areaSlug, reviewTier) =>
						autonomy.setAreaTier.mutate({ path: { workspaceSlug, areaSlug }, body: { reviewTier } })
					}
					// Omitting the field is how the wire says "hold no tier here": the generated request
					// types it as optional, and the server reads an absent field as clear-to-inherit.
					onClearAreaTier={(areaSlug) =>
						autonomy.setAreaTier.mutate({ path: { workspaceSlug, areaSlug }, body: {} })
					}
					onSetPracticeTier={(practiceSlug, reviewTier) =>
						autonomy.setPracticeTier.mutate({
							path: { workspaceSlug, practiceSlug },
							body: { reviewTier },
						})
					}
					onClearPracticeTier={(practiceSlug) =>
						autonomy.setPracticeTier.mutate({ path: { workspaceSlug, practiceSlug }, body: {} })
					}
					onBulkSetTier={(practiceSlugs, tier) => {
						void autonomy.setManyPracticeTiers(practiceSlugs, tier);
					}}
				/>
			)}
		</PageLayout>
	);
}

import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import {
	getPracticeReviewSettingsOptions,
	listAgentsOptions,
	listPracticesOptions,
	reviewTierRollupOptions,
} from "@/api/@tanstack/react-query.gen";
import { ReviewAutonomyPage } from "@/components/admin/practices/review-autonomy/ReviewAutonomyPage";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { buttonVariants } from "@/components/ui/button";
import { Item, ItemActions, ItemContent, ItemDescription, ItemTitle } from "@/components/ui/item";
import { Spinner } from "@/components/ui/spinner";
import { usePracticeReviewSettingsMutation } from "@/hooks/use-practice-review-settings";
import { useReviewAutonomyMutations } from "@/hooks/use-review-autonomy-mutations";
import { type ReviewModelState, reviewModelRunnable } from "./review-readiness";

export interface ReviewHowMuchSectionProps {
	workspaceSlug: string;
	overridesOnly: boolean;
	onOverridesOnlyChange: (next: boolean) => void;
}

/** Queries live here rather than on the route, so they do not fire until this tab is opened. */
export function ReviewHowMuchSection({
	workspaceSlug,
	overridesOnly,
	onOverridesOnlyChange,
}: ReviewHowMuchSectionProps) {
	const settingsQuery = useQuery({
		...getPracticeReviewSettingsOptions({ path: { workspaceSlug } }),
	});
	const rollupQuery = useQuery({ ...reviewTierRollupOptions({ path: { workspaceSlug } }) });
	const practicesQuery = useQuery({ ...listPracticesOptions({ path: { workspaceSlug } }) });
	const bindingsQuery = useQuery({ ...listAgentsOptions({ path: { workspaceSlug } }) });

	const autonomy = useReviewAutonomyMutations(workspaceSlug);
	const updateSettings = usePracticeReviewSettingsMutation(workspaceSlug, {
		success: "Review settings updated",
		error: "Failed to update review settings",
	});

	const pendingQueries =
		settingsQuery.isPending || rollupQuery.isPending || practicesQuery.isPending;
	const error = settingsQuery.error ?? rollupQuery.error ?? practicesQuery.error;

	return (
		<div className="space-y-6">
			<ReviewModelNote
				workspaceSlug={workspaceSlug}
				binding={bindingsQuery.data?.find((agent) => agent.purpose === "PRACTICE_REVIEW")}
				isLoading={bindingsQuery.isLoading}
				isError={bindingsQuery.isError}
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
					overridesOnly={overridesOnly}
					onOverridesOnlyChange={onOverridesOnlyChange}
					onSetWorkspaceDefault={(defaultReviewTier) =>
						updateSettings.mutate({ path: { workspaceSlug }, body: { defaultReviewTier } })
					}
					onClearWorkspaceDefault={() =>
						updateSettings.mutate({
							path: { workspaceSlug },
							body: { reset: ["DEFAULT_REVIEW_TIER"] },
						})
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
		</div>
	);
}

/**
 * The model binding, read-only: every tier below is a decision about what an AI model will do and
 * means nothing until one is bound, but AI models owns the field and stays its only writer.
 */
function ReviewModelNote({
	workspaceSlug,
	binding,
	isLoading,
	isError,
}: { workspaceSlug: string } & ReviewModelState) {
	const runnable = reviewModelRunnable({ binding, isLoading, isError });
	let state: string;
	if (isLoading) state = "Checking readiness…";
	else if (isError) state = "Couldn't check whether it is ready.";
	else if (runnable) state = "Ready to run reviews.";
	else if (binding) state = "The bound model is turned off or was removed, so no review can run.";
	else state = "No model is bound, so no review can run at any tier below.";

	return (
		<Item variant="outline" size="sm">
			<ItemContent>
				<ItemTitle>Review model</ItemTitle>
				<ItemDescription>{state}</ItemDescription>
			</ItemContent>
			<ItemActions>
				<Link
					to="/w/$workspaceSlug/admin/models"
					params={{ workspaceSlug }}
					className={buttonVariants({ variant: "outline", size: "sm" })}
				>
					{runnable ? "Change on AI models" : "Set up on AI models"}
				</Link>
			</ItemActions>
		</Item>
	);
}

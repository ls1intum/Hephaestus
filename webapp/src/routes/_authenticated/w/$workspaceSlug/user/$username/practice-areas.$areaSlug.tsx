import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import {
	getObservationOptions,
	getPracticeAreaStatusesOptions,
	getReflectionOptions,
	getWorkspaceOptions,
	listAreasOptions,
	listLearnerPracticesOptions,
	listPracticeAreaReviewHistoryInfiniteOptions,
	listPracticeAreaReviewHistoryInfiniteQueryKey,
	listPracticeAreaTrendOptions,
	rateFeedbackHelpfulnessMutation,
	removeFeedbackHelpfulnessRatingMutation,
} from "@/api/@tanstack/react-query.gen";
import {
	type ActivityFilters,
	PracticeAreaDetailPage,
} from "@/components/profile/PracticeAreaDetailPage";
import type { ObservationDetailState } from "@/components/profile/review-history";
import { useAuth } from "@/integrations/auth/AuthContext";
import { problemDetailOf } from "@/lib/problem-detail";
import { toScmProviderType } from "@/lib/provider";

const ACTIVITY_PAGE_SIZE = 10;

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/user/$username/practice-areas/$areaSlug",
)({
	component: PracticeAreaDetail,
});

function PracticeAreaDetail() {
	const { workspaceSlug, username, areaSlug } = Route.useParams();
	const { isCurrentUser } = useAuth();
	const navigate = useNavigate();
	const queryClient = useQueryClient();
	const isOwnProfile = isCurrentUser(username);
	const [openObservationId, setOpenObservationId] = useState<string>();
	const [selectedPracticeSlug, setSelectedPracticeSlug] = useState<string>();
	const [activityFilters, setActivityFilters] = useState<ActivityFilters>({
		sources: [],
		severities: [],
	});

	// The status endpoint derives the CALLER's standing, so this page only exists on the own
	// profile — a foreign profile's URL bounces back to that profile.
	useEffect(() => {
		if (!isOwnProfile) {
			navigate({
				to: "/w/$workspaceSlug/user/$username",
				params: { workspaceSlug, username },
				replace: true,
			});
		}
	}, [isOwnProfile, workspaceSlug, username, navigate]);

	const enabled = Boolean(workspaceSlug) && isOwnProfile;

	// Same queries (and therefore the same cache entries) as the profile overview: navigating from
	// a card renders instantly from cache, and a hard refresh on this URL fetches on its own.
	const workspaceQuery = useQuery({
		...getWorkspaceOptions({ path: { workspaceSlug } }),
		enabled,
	});
	const areasQuery = useQuery({
		...listAreasOptions({
			path: { workspaceSlug },
			query: { visibleInPracticeDashboardsOnly: true },
		}),
		enabled,
	});
	const statusesQuery = useQuery({
		...getPracticeAreaStatusesOptions({ path: { workspaceSlug } }),
		enabled,
	});
	// The area's practices (catalog) and the caller's per-practice standing (reflection surface).
	const practicesQuery = useQuery({
		...listLearnerPracticesOptions({ path: { workspaceSlug } }),
		enabled,
	});
	const reflectionQuery = useQuery({
		...getReflectionOptions({ path: { workspaceSlug } }),
		enabled,
	});
	const trendQuery = useQuery({
		...listPracticeAreaTrendOptions({ path: { workspaceSlug, areaSlug } }),
		enabled,
	});
	// Complete newest-first review moments for this area. Selecting a practice or an integration
	// filter changes the query key and restarts run-level pagination on the server.
	const reviewHistoryRequest = {
		path: { workspaceSlug, areaSlug },
		query: {
			size: ACTIVITY_PAGE_SIZE,
			practiceSlug: selectedPracticeSlug,
			artifactTypes: activityFilters.sources.length > 0 ? activityFilters.sources : undefined,
			severities: activityFilters.severities.length > 0 ? activityFilters.severities : undefined,
		},
	};
	const activityQuery = useInfiniteQuery({
		...listPracticeAreaReviewHistoryInfiniteOptions(reviewHistoryRequest),
		initialPageParam: 0,
		getNextPageParam: (lastPage) => {
			const currentPage = lastPage.page?.number ?? 0;
			const totalPages = lastPage.page?.totalPages ?? 0;
			return currentPage + 1 < totalPages ? currentPage + 1 : undefined;
		},
		enabled,
	});
	const invalidateReviewHistory = () =>
		queryClient.invalidateQueries({
			queryKey: listPracticeAreaReviewHistoryInfiniteQueryKey(reviewHistoryRequest),
		});
	const rateFeedbackMutation = useMutation({
		...rateFeedbackHelpfulnessMutation(),
		onSuccess: invalidateReviewHistory,
		onError: (error) => toast.error(problemDetailOf(error, "Could not save your feedback rating")),
	});
	const removeFeedbackRatingMutation = useMutation({
		...removeFeedbackHelpfulnessRatingMutation(),
		onSuccess: invalidateReviewHistory,
		onError: (error) =>
			toast.error(problemDetailOf(error, "Could not remove your feedback rating")),
	});
	// Guidance/reasoning are large text fields the list omits — fetched only when a row is expanded.
	const observationQuery = useQuery({
		...getObservationOptions({
			path: { workspaceSlug, observationId: openObservationId ?? "" },
		}),
		enabled: enabled && Boolean(openObservationId),
	});

	if (!isOwnProfile) {
		return null;
	}

	const area = areasQuery.data?.find((candidate) => candidate.slug === areaSlug);
	const status = statusesQuery.data?.find((candidate) => candidate.areaSlug === areaSlug);
	const practices = practicesQuery.data?.filter((practice) => practice.areaSlug === areaSlug);
	const reflectionForArea = (reflectionQuery.data ?? []).filter(
		(practice) => practice.areaSlug === areaSlug,
	);
	const practiceStandings = Object.fromEntries(
		reflectionForArea.map((practice) => [practice.slug, practice.standing]),
	);
	const practiceTrends = Object.fromEntries(
		(trendQuery.data?.practices ?? []).map((practiceTrend) => [practiceTrend.slug, practiceTrend]),
	);
	// Reflection items are already priority-sorted by the server. Prefer delivered guidance and fall
	// back to the finding headline so selecting a practice always reveals a real, traceable action.
	const practiceNextSteps = Object.fromEntries(
		reflectionForArea.map((practice) => {
			const firstAction = practice.toWorkOn[0];
			const deliveredGuidance = firstAction?.deliveredFeedback?.trim();
			const findingTitle = firstAction?.title.trim();
			const distinctTitle = findingTitle !== practice.name.trim() ? findingTitle : undefined;
			return [practice.slug, deliveredGuidance || distinctTitle];
		}),
	);
	const reviewHistory = (activityQuery.data?.pages ?? []).flatMap((page) => page.content ?? []);

	const observationDetail: ObservationDetailState | undefined = openObservationId
		? {
				isLoading: observationQuery.isPending,
				detail: observationQuery.data,
				error: observationQuery.error ?? undefined,
			}
		: undefined;

	return (
		<PracticeAreaDetailPage
			area={area}
			status={status}
			practices={practices}
			practiceStandings={practiceStandings}
			practiceTrends={practiceTrends}
			areaTrend={trendQuery.data?.area}
			practiceNextSteps={practiceNextSteps}
			selectedPracticeSlug={selectedPracticeSlug}
			onSelectPractice={(practiceSlug) => {
				setSelectedPracticeSlug(practiceSlug);
				setOpenObservationId(undefined);
			}}
			providerType={toScmProviderType(workspaceQuery.data?.providerType)}
			reviewHistory={reviewHistory}
			isActivityLoading={activityQuery.isPending}
			activityError={activityQuery.error ?? undefined}
			onRetryActivity={() => activityQuery.refetch()}
			activityFilters={activityFilters}
			onActivityFiltersChange={(filters) => {
				setActivityFilters(filters);
				setOpenObservationId(undefined);
			}}
			hasMoreActivity={activityQuery.hasNextPage}
			isLoadingMoreActivity={activityQuery.isFetchingNextPage}
			onLoadMoreActivity={() => activityQuery.fetchNextPage()}
			openObservationId={openObservationId}
			observationDetail={observationDetail}
			onToggleObservation={(observationId) =>
				setOpenObservationId((current) => (current === observationId ? undefined : observationId))
			}
			onRateFeedback={(feedbackId, helpful) => {
				if (helpful === undefined) {
					removeFeedbackRatingMutation.mutate({ path: { workspaceSlug, feedbackId } });
					return;
				}
				rateFeedbackMutation.mutate({
					path: { workspaceSlug, feedbackId },
					body: { helpful },
				});
			}}
			pendingFeedbackId={
				rateFeedbackMutation.isPending
					? rateFeedbackMutation.variables?.path.feedbackId
					: removeFeedbackRatingMutation.isPending
						? removeFeedbackRatingMutation.variables?.path.feedbackId
						: undefined
			}
			isLoading={
				workspaceQuery.isPending ||
				areasQuery.isPending ||
				statusesQuery.isPending ||
				practicesQuery.isPending ||
				reflectionQuery.isPending ||
				trendQuery.isPending
			}
			error={
				workspaceQuery.error ??
				areasQuery.error ??
				statusesQuery.error ??
				practicesQuery.error ??
				reflectionQuery.error ??
				trendQuery.error ??
				undefined
			}
			onRetry={() => {
				if (workspaceQuery.isError) workspaceQuery.refetch();
				if (areasQuery.isError) areasQuery.refetch();
				if (statusesQuery.isError) statusesQuery.refetch();
				if (practicesQuery.isError) practicesQuery.refetch();
				if (reflectionQuery.isError) reflectionQuery.refetch();
				if (trendQuery.isError) trendQuery.refetch();
			}}
			onBack={() =>
				navigate({
					to: "/w/$workspaceSlug/user/$username",
					params: { workspaceSlug, username },
				})
			}
		/>
	);
}

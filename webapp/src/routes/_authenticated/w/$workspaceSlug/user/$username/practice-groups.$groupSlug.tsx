import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import {
	deleteFeedbackResponseMutation,
	getObservationOptions,
	getPracticeGroupTrendOptions,
	listGroupsOptions,
	listPracticeGroupReviewRunsInfiniteOptions,
	listPracticeGroupReviewRunsInfiniteQueryKey,
	listPracticeGroupStandingsOptions,
	listPracticeStandingsOptions,
	listReviewedPracticesOptions,
	replaceFeedbackResponseMutation,
} from "@/api/@tanstack/react-query.gen";
import {
	PracticeGroupDetailPage,
	type ReviewRunFilters,
} from "@/components/profile/PracticeGroupDetailPage";
import type { ObservationDetailState } from "@/components/profile/review-runs";
import { useAuth } from "@/integrations/auth/AuthContext";
import { loadedPages } from "@/integrations/tanstack-query/spring-page";
import { problemDetailOf } from "@/lib/problem-detail";

const ACTIVITY_PAGE_SIZE = 10;

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/user/$username/practice-groups/$groupSlug",
)({
	component: PracticeGroupDetail,
});

function PracticeGroupDetail() {
	const { workspaceSlug, username, groupSlug } = Route.useParams();
	const { isCurrentUser } = useAuth();
	const navigate = useNavigate();
	const queryClient = useQueryClient();
	const isOwnProfile = isCurrentUser(username);
	const [openObservationId, setOpenObservationId] = useState<string>();
	const [selectedPracticeSlug, setSelectedPracticeSlug] = useState<string>();
	const [activityFilters, setActivityFilters] = useState<ReviewRunFilters>({
		sources: [],
		severities: [],
	});

	useEffect(() => {
		if (!isOwnProfile) {
			void navigate({
				to: "/w/$workspaceSlug/user/$username",
				params: { workspaceSlug, username },
				replace: true,
			});
		}
	}, [isOwnProfile, workspaceSlug, username, navigate]);

	const enabled = Boolean(workspaceSlug) && isOwnProfile;

	const groupsQuery = useQuery({
		...listGroupsOptions({
			path: { workspaceSlug },
			query: { visibleInPracticeDashboardsOnly: true },
		}),
		enabled,
	});
	const statusesQuery = useQuery({
		...listPracticeGroupStandingsOptions({ path: { workspaceSlug } }),
		enabled,
	});
	const practicesQuery = useQuery({
		...listReviewedPracticesOptions({ path: { workspaceSlug } }),
		enabled,
	});
	const standingsQuery = useQuery({
		...listPracticeStandingsOptions({ path: { workspaceSlug } }),
		enabled,
	});
	const trendQuery = useQuery({
		...getPracticeGroupTrendOptions({ path: { workspaceSlug, groupSlug } }),
		enabled,
	});
	const reviewRunsRequest = {
		path: { workspaceSlug, groupSlug },
		query: {
			size: ACTIVITY_PAGE_SIZE,
			practiceSlug: selectedPracticeSlug,
			artifactKinds: activityFilters.sources.length > 0 ? activityFilters.sources : undefined,
			severities: activityFilters.severities.length > 0 ? activityFilters.severities : undefined,
		},
	};
	const activityQuery = useInfiniteQuery({
		...listPracticeGroupReviewRunsInfiniteOptions(reviewRunsRequest),
		initialPageParam: 0,
		getNextPageParam: (lastPage) => (lastPage.hasNext ? (lastPage.page ?? 0) + 1 : undefined),
		enabled,
	});
	const invalidateReviewRuns = () =>
		queryClient.invalidateQueries({
			queryKey: listPracticeGroupReviewRunsInfiniteQueryKey(reviewRunsRequest),
		});
	const replaceResponseMutation = useMutation({
		...replaceFeedbackResponseMutation(),
		onSuccess: invalidateReviewRuns,
		onError: (error) =>
			toast.error(problemDetailOf(error, "Could not save your feedback response")),
	});
	const deleteResponseMutation = useMutation({
		...deleteFeedbackResponseMutation(),
		onSuccess: invalidateReviewRuns,
		onError: (error) =>
			toast.error(problemDetailOf(error, "Could not withdraw your feedback response")),
	});
	const observationQuery = useQuery({
		...getObservationOptions({
			path: { workspaceSlug, observationId: openObservationId ?? "" },
		}),
		enabled: enabled && Boolean(openObservationId),
	});

	if (!isOwnProfile) {
		return null;
	}

	const group = groupsQuery.data?.find((candidate) => candidate.slug === groupSlug);
	const standing = statusesQuery.data?.find((candidate) => candidate.groupSlug === groupSlug);
	const practices = practicesQuery.data?.filter((practice) => practice.groupSlug === groupSlug);
	const standingsForGroup = (standingsQuery.data ?? []).filter(
		(practice) => practice.groupSlug === groupSlug,
	);
	const practiceStandings = Object.fromEntries(
		standingsForGroup.map((practice) => [practice.slug, practice.standing]),
	);
	const practiceTrends = Object.fromEntries(
		(trendQuery.data?.practices ?? []).map((practiceTrend) => [practiceTrend.slug, practiceTrend]),
	);
	const practiceNextSteps = Object.fromEntries(
		standingsForGroup.map((practice) => {
			const firstAction = practice.toWorkOn[0];
			const deliveredGuidance = firstAction?.deliveredFeedback?.trim();
			const findingTitle = firstAction?.title.trim();
			const distinctTitle = findingTitle !== practice.name.trim() ? findingTitle : undefined;
			const nextStep = [deliveredGuidance, distinctTitle].find(
				(value) => value !== undefined && value !== "",
			);
			return [practice.slug, nextStep];
		}),
	);
	const reviewRuns = loadedPages(activityQuery.data).flatMap((page) => page.content);

	const observationDetail: ObservationDetailState | undefined = openObservationId
		? {
				isLoading: observationQuery.isPending,
				detail: observationQuery.data,
				error: observationQuery.error ?? undefined,
			}
		: undefined;

	return (
		<PracticeGroupDetailPage
			group={group}
			standing={standing}
			practices={practices}
			practiceStandings={practiceStandings}
			practiceTrends={practiceTrends}
			groupTrend={trendQuery.data?.group}
			practiceNextSteps={practiceNextSteps}
			selectedPracticeSlug={selectedPracticeSlug}
			onSelectPractice={(practiceSlug) => {
				setSelectedPracticeSlug(practiceSlug);
				setOpenObservationId(undefined);
			}}
			reviewRuns={reviewRuns}
			isReviewRunsLoading={activityQuery.isPending}
			reviewRunsError={activityQuery.error ?? undefined}
			onRetryReviewRuns={() => void activityQuery.refetch()}
			reviewRunFilters={activityFilters}
			onReviewRunFiltersChange={(filters) => {
				setActivityFilters(filters);
				setOpenObservationId(undefined);
			}}
			hasMoreReviewRuns={activityQuery.hasNextPage}
			isLoadingMoreReviewRuns={activityQuery.isFetchingNextPage}
			onLoadMoreReviewRuns={() => void activityQuery.fetchNextPage()}
			openObservationId={openObservationId}
			observationDetail={observationDetail}
			onToggleObservation={(observationId) =>
				setOpenObservationId((current) => (current === observationId ? undefined : observationId))
			}
			onChangeUsefulness={(observation, usefulness) => {
				const feedbackId = observation.feedbackId;
				if (!feedbackId) return;
				const resolution = observation.feedbackResolution;
				const comment = observation.feedbackResponseComment;

				if (usefulness === undefined && resolution === undefined) {
					deleteResponseMutation.mutate({ path: { workspaceSlug, feedbackId } });
					return;
				}
				replaceResponseMutation.mutate({
					path: { workspaceSlug, feedbackId },
					body: {
						usefulness,
						resolution,
						comment,
					},
				});
			}}
			pendingFeedbackId={
				replaceResponseMutation.isPending
					? replaceResponseMutation.variables.path.feedbackId
					: deleteResponseMutation.isPending
						? deleteResponseMutation.variables.path.feedbackId
						: undefined
			}
			isLoading={
				groupsQuery.isPending ||
				statusesQuery.isPending ||
				practicesQuery.isPending ||
				standingsQuery.isPending ||
				trendQuery.isPending
			}
			error={
				groupsQuery.error ??
				statusesQuery.error ??
				practicesQuery.error ??
				standingsQuery.error ??
				trendQuery.error ??
				undefined
			}
			onRetry={() => {
				if (groupsQuery.isError) void groupsQuery.refetch();
				if (statusesQuery.isError) void statusesQuery.refetch();
				if (practicesQuery.isError) void practicesQuery.refetch();
				if (standingsQuery.isError) void standingsQuery.refetch();
				if (trendQuery.isError) void trendQuery.refetch();
			}}
			onBack={() =>
				void navigate({
					to: "/w/$workspaceSlug/user/$username",
					params: { workspaceSlug, username },
				})
			}
		/>
	);
}

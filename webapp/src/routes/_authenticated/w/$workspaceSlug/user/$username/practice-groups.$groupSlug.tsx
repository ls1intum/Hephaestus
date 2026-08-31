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
import type { PracticeStanding } from "@/api/types.gen";
import {
	PracticeGroupDetailPage,
	type ReviewRunFeedState,
} from "@/components/profile/PracticeGroupDetailPage";
import {
	isEmptyFeedbackResponse,
	type ObservationDetailState,
} from "@/components/profile/review-runs";
import { useAuth } from "@/integrations/auth/AuthContext";
import { loadedPages } from "@/integrations/tanstack-query/spring-page";
import { problemDetailOf } from "@/lib/problem-detail";

const ACTIVITY_PAGE_SIZE = 10;

/**
 * The one thing a developer should do next for a practice: the guidance a review actually delivered,
 * or the observation's own title when it says something the practice name does not already say.
 */
function nextStepOf(practiceStanding?: PracticeStanding): string | undefined {
	const firstAction = practiceStanding?.toWorkOn[0];
	if (!firstAction) return undefined;
	const deliveredGuidance = firstAction.deliveredFeedback?.trim();
	const observationTitle = firstAction.title.trim();
	const distinctTitle =
		observationTitle !== practiceStanding.name.trim() ? observationTitle : undefined;
	return [deliveredGuidance, distinctTitle].find((value) => value !== undefined && value !== "");
}

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
	const standingsBySlug = new Map(
		(standingsQuery.data ?? [])
			.filter((practice) => practice.groupSlug === groupSlug)
			.map((practice) => [practice.slug, practice]),
	);
	const trendsBySlug = new Map(
		(trendQuery.data?.practices ?? []).map((practiceTrend) => [practiceTrend.slug, practiceTrend]),
	);
	// Joined here rather than handed down as four slug-keyed records: this is the only place that
	// holds all four answers at once, so it is the only place that can align them.
	const practices = practicesQuery.data
		?.filter((practice) => practice.groupSlug === groupSlug)
		.map((practice) => {
			const practiceStanding = standingsBySlug.get(practice.slug);
			return {
				...practice,
				standing: practiceStanding?.standing,
				trend: trendsBySlug.get(practice.slug),
				nextStep: nextStepOf(practiceStanding),
			};
		});
	// One value instead of seven flags: the page cannot be handed "loading and failed" at once, and an
	// error always arrives with the retry that clears it.
	const reviewRunFeed: ReviewRunFeedState = activityQuery.isPending
		? { status: "loading" }
		: activityQuery.error
			? {
					status: "error",
					error: activityQuery.error,
					onRetry: () => void activityQuery.refetch(),
				}
			: {
					status: "ready",
					runs: loadedPages(activityQuery.data).flatMap((page) => page.content),
					hasMore: activityQuery.hasNextPage,
					isLoadingMore: activityQuery.isFetchingNextPage,
					onLoadMore: () => void activityQuery.fetchNextPage(),
				};

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
			groupTrend={trendQuery.data?.group}
			selectedPracticeSlug={selectedPracticeSlug}
			onSelectPractice={(practiceSlug) => {
				setSelectedPracticeSlug(practiceSlug);
				setOpenObservationId(undefined);
			}}
			feed={reviewRunFeed}
			skeletonRows={ACTIVITY_PAGE_SIZE}
			openObservationId={openObservationId}
			observationDetail={observationDetail}
			onToggleObservation={(observationId) =>
				setOpenObservationId((current) => (current === observationId ? undefined : observationId))
			}
			onRespond={(observation, response) => {
				const feedbackId = observation.feedbackId;
				if (!feedbackId) return;
				// An answer with nothing left in it is a withdrawal, not an empty update — the endpoint
				// replaces what it receives, so storing a blank response would keep a row saying nothing.
				if (isEmptyFeedbackResponse(response)) {
					deleteResponseMutation.mutate({ path: { workspaceSlug, feedbackId } });
					return;
				}
				replaceResponseMutation.mutate({
					path: { workspaceSlug, feedbackId },
					body: response,
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

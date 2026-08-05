import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import {
	getObservationOptions,
	getPracticeAreaStatusesOptions,
	getReflectionOptions,
	getWorkspaceOptions,
	listAreasOptions,
	listLearnerPracticesOptions,
	listObservationsInfiniteOptions,
} from "@/api/@tanstack/react-query.gen";
import {
	type ActivityFilters,
	type ActivitySort,
	type ObservationDetailState,
	PracticeAreaDetailPage,
} from "@/components/profile/PracticeAreaDetailPage";
import { useAuth } from "@/integrations/auth/AuthContext";
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
	const isOwnProfile = isCurrentUser(username);
	const [openObservationId, setOpenObservationId] = useState<string>();
	const [selectedPracticeSlug, setSelectedPracticeSlug] = useState<string>();
	const [activityFilters, setActivityFilters] = useState<ActivityFilters>({
		sources: [],
		severities: [],
	});
	const [activitySort, setActivitySort] = useState<ActivitySort>({
		by: "DATE",
		direction: "DESC",
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
		...listAreasOptions({ path: { workspaceSlug }, query: { activeOnly: true } }),
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
	// Raw newest-first observation feed for this area. Selecting a practice node or an integration
	// filter changes the query key, which restarts pagination server-side — no client-side slicing.
	const activityQuery = useInfiniteQuery({
		...listObservationsInfiniteOptions({
			path: { workspaceSlug },
			query: {
				areaSlug,
				displayableOnly: true,
				size: ACTIVITY_PAGE_SIZE,
				practiceSlug: selectedPracticeSlug,
				artifactTypes: activityFilters.sources.length > 0 ? activityFilters.sources : undefined,
				severities: activityFilters.severities.length > 0 ? activityFilters.severities : undefined,
				sort: activitySort.by,
				direction: activitySort.direction,
			},
		}),
		initialPageParam: 0,
		getNextPageParam: (lastPage) => (lastPage.last ? undefined : (lastPage.number ?? 0) + 1),
		enabled,
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
	const practiceTrajectories = Object.fromEntries(
		reflectionForArea.map((practice) => [practice.slug, practice.trajectory]),
	);
	const activity = (activityQuery.data?.pages ?? []).flatMap((page) => page.content ?? []);

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
			practiceTrajectories={practiceTrajectories}
			selectedPracticeSlug={selectedPracticeSlug}
			onSelectPractice={(practiceSlug) => {
				setSelectedPracticeSlug(practiceSlug);
				setOpenObservationId(undefined);
			}}
			providerType={toScmProviderType(workspaceQuery.data?.providerType)}
			activity={activity}
			isActivityLoading={activityQuery.isPending}
			activityError={activityQuery.error ?? undefined}
			onRetryActivity={() => activityQuery.refetch()}
			activityFilters={activityFilters}
			onActivityFiltersChange={(filters) => {
				setActivityFilters(filters);
				setOpenObservationId(undefined);
			}}
			activitySort={activitySort}
			onActivitySortChange={(sort) => {
				setActivitySort(sort);
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
			isLoading={
				workspaceQuery.isPending ||
				areasQuery.isPending ||
				statusesQuery.isPending ||
				practicesQuery.isPending ||
				reflectionQuery.isPending
			}
			error={
				workspaceQuery.error ??
				areasQuery.error ??
				statusesQuery.error ??
				practicesQuery.error ??
				reflectionQuery.error ??
				undefined
			}
			onRetry={() => {
				if (workspaceQuery.isError) workspaceQuery.refetch();
				if (areasQuery.isError) areasQuery.refetch();
				if (statusesQuery.isError) statusesQuery.refetch();
				if (practicesQuery.isError) practicesQuery.refetch();
				if (reflectionQuery.isError) reflectionQuery.refetch();
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

import { useQuery } from "@tanstack/react-query";
import {
	createFileRoute,
	Navigate,
	retainSearchParams,
	stripSearchParams,
} from "@tanstack/react-router";

import { getOwnResultOptions } from "@/api/@tanstack/react-query.gen";
import type { GroupRef } from "@/api/types.gen";
import { combinePanelStates, queryLoadState } from "@/components/common/panel-state";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { parseDetailStack } from "@/components/layout/detail-drawer/detail-stack";
import { useDetailStack } from "@/components/layout/detail-drawer/use-detail-stack";
import { isFocusSwitchStep } from "@/components/practice-focus/practice-focus";
import { ProfileFocusFooter } from "@/components/practice-focus/profile-focus-footer";
import { usePracticeFocusSwitch } from "@/components/practice-focus/use-practice-focus-switch";
import {
	composeGroupOverview,
	composeOverview,
} from "@/components/practice-profile/compose-overview";
import {
	PRACTICE_PROFILE_LEVEL_KINDS,
	openLevelId,
	PRACTICE_PROFILE_LEVEL_PARAMS,
	PRACTICE_PROFILE_SEARCH_DEFAULTS,
	PRACTICE_PROFILE_SEARCH_PARAMS,
	practiceGroupLevel,
	practiceLevel,
	type PracticeProfileSearch,
	practiceProfileSearchSchema,
	reviewLevel,
} from "@/components/practice-profile/practice-profile-search";
import { PracticeGroupDetailDrawer } from "@/components/practice-profile/PracticeGroupDetailDrawer";
import { PracticeProfilePage } from "@/components/practice-profile/PracticeProfilePage";
import { RefusalFixLink } from "@/components/practice-trace/RefusalFixLink";
import { TraceRefusalAlert } from "@/components/practice-trace/TraceRefusalAlert";
import { ReviewResultRow } from "@/components/profile/ReviewResultRow";
import type { FeedbackResponseControls } from "@/components/profile/reviews";
import { Skeleton } from "@/components/ui/skeleton";
import { REVIEW_PAGE_SIZE, usePracticeGroupDetail } from "@/hooks/use-practice-group-detail";
import { usePracticePageFeedback } from "@/hooks/use-practice-page-feedback";
import { usePracticeProfileOverview } from "@/hooks/use-practice-profile-overview";
import { usePracticeProfileReviews } from "@/hooks/use-practice-profile-reviews";
import { usePracticeStandings } from "@/hooks/use-practice-standings";
import { useWorkspaceFeatures } from "@/hooks/use-workspace-features";
import { pageHead } from "@/lib/page-title";
import { hasText } from "@/lib/text";
import { hasMinimumWorkspaceRole } from "@/lib/workspace-roles";
import { useAuth } from "@/runtime/auth/AuthContext";
import { workspaceMembershipQueryOptions } from "@/runtime/auth/guard";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/practice-profile")({
	component: PracticeProfile,
	head: pageHead("Practice profile"),
	validateSearch: practiceProfileSearchSchema,
	search: {
		middlewares: [
			// The shortest address that means the same is the one a reader shares. Outermost, so it
			// also strips the defaults the retain below copies into a link that names no search,
			// such as the sidebar's.
			stripSearchParams(PRACTICE_PROFILE_SEARCH_DEFAULTS),
			retainSearchParams(PRACTICE_PROFILE_SEARCH_PARAMS),
		],
	},
});

function PracticeProfile() {
	// A user view reads the developer's page and answers nothing on their behalf.
	const readOnly = useAuth().userView !== undefined;
	const { workspaceSlug } = Route.useParams();
	const membership = useQuery(workspaceMembershipQueryOptions(workspaceSlug));
	const isAdmin = !readOnly && hasMinimumWorkspaceRole(membership.data?.role, "ADMIN");
	const search = Route.useSearch();

	const detailStack = parseDetailStack(search.detail, PRACTICE_PROFILE_LEVEL_KINDS);
	const stackControls = useDetailStack({
		levelParams: PRACTICE_PROFILE_LEVEL_PARAMS,
		replaceOnClose: isFocusSwitchStep,
	});

	const setView = (view: Partial<PracticeProfileSearch>) => stackControls.setView(view);

	const featureState = useWorkspaceFeatures(workspaceSlug);
	const focusSwitch = usePracticeFocusSwitch(workspaceSlug);
	const {
		groups,
		emptyGroups,
		groupStandings,
		practiceStandings,
		reviewedPractices,
		practicesByGroup,
		...standings
	} = usePracticeStandings(workspaceSlug);
	const { overview, ...overviewQuery } = usePracticeProfileOverview(workspaceSlug);
	// The cards: read here, which is also what delivers the unread ones — so the read waits for a
	// definite "this workspace reviews practices", or it would mark feedback delivered for a reader
	// the answer below is about to send away.
	const feedback = usePracticePageFeedback({
		workspaceSlug,
		groups,
		enabled: featureState.practicesEnabled === true,
	});
	const openReviewId = openLevelId(detailStack, "review");
	const reviews = usePracticeProfileReviews({
		workspaceSlug,
		listOpen: openLevelId(detailStack, "reviews") !== undefined || openReviewId !== undefined,
		reviewId: openReviewId,
		itemKind: search.reviewKind,
		since: search.reviewSince,
	});
	const detail = usePracticeGroupDetail({
		workspaceSlug,
		groupSlug: openLevelId(detailStack, "practice-group"),
		practiceSlug: openLevelId(detailStack, "practice"),
		practiceStandings,
		reviewedPractices,
	});

	// The page and every level over it show the three together, so they load and fail as one;
	// whether this workspace reviews practices at all is read with them, since without that answer
	// nothing here is known to exist.
	const page = combinePanelStates([
		queryLoadState({ ...featureState, isPending: featureState.isLoading }),
		standings.state,
		overviewQuery.state,
		feedback.state,
	]);

	// Only a definite "off" redirects: the surface exists only where practices review the work, and
	// sending someone away before the answer arrives would bounce them out of a workspace that does.
	if (featureState.practicesEnabled === false) {
		return <Navigate to="/w/$workspaceSlug" params={{ workspaceSlug }} replace />;
	}

	const composed = composeOverview(overview);
	// A user view reads the developer's page; it never spends their workspace's budget.
	const onReviewNow = readOnly ? undefined : reviews.onReviewNow;
	const openGroup = (group: GroupRef) => stackControls.open(practiceGroupLevel(group.slug));
	const groupOf = (practiceSlug: string) => {
		const groupSlug = practiceStandings.find((entry) => entry.slug === practiceSlug)?.groupSlug;
		return groups.find((group) => group.slug === groupSlug);
	};
	// One level in, one level out: a practice is its group, then the practice over it, so the
	// level's back arrow lands on the group and the browser's Back button agrees with it. A list
	// that is open stays underneath. A practice whose standing this workspace does not carry opens
	// alone and the level says so.
	const openPractice = (practiceSlug: string) => {
		const groupSlug = practiceStandings.find((entry) => entry.slug === practiceSlug)?.groupSlug;
		void stackControls.push([
			...(hasText(groupSlug) ? [practiceGroupLevel(groupSlug)] : []),
			practiceLevel(practiceSlug),
		]);
	};

	return (
		<>
			<PracticeProfilePage
				latestReview={composed.latestReview}
				practices={practiceStandings}
				groups={groups}
				feedbackCards={feedback.cards}
				ratingProps={readOnly ? undefined : feedback.ratingProps}
				onOpenGroup={openGroup}
				onOpenPractice={openPractice}
				feedbackTab={search.feedback}
				onFeedbackTabChange={(tab) => setView({ feedback: tab })}
				state={page}
			/>
			<PracticeGroupDetailDrawer
				detail={readOnly ? { ...detail, responses: undefined } : detail}
				detailStack={detailStack}
				allPracticeGroups={{
					practicesByGroup,
					sentences: composed.groupSentences,
					sort: search.dir,
					onSortChange: (dir) => setView({ dir }),
					onOpenGroup: openGroup,
					onOpenPractice: (slug) => openPractice(slug),
				}}
				onClose={stackControls.close}
				groups={groups}
				emptyGroups={emptyGroups}
				groupStandings={groupStandings}
				state={page}
				feedbackCards={feedback.cards}
				groupOverview={(groupSlug) => composeGroupOverview(overview, groupSlug)}
				ratingProps={readOnly ? undefined : feedback.ratingProps}
				onOpenPractice={(practiceSlug) => stackControls.open(practiceLevel(practiceSlug))}
				practiceTab={search.practiceTab}
				skeletonRows={REVIEW_PAGE_SIZE}
				reviews={{
					list: {
						feed: reviews.list,
						positions: reviews.positions,
						onOpenReview: (reviewId) => stackControls.open(reviewLevel(reviewId)),
						kind: search.reviewKind,
						onKindChange: (reviewKind) => setView({ reviewKind }),
						since: search.reviewSince,
						onSinceChange: (reviewSince) => setView({ reviewSince }),
						onReviewNow,
						requesting: reviews.requesting,
					},
					open: {
						state: reviews.open,
						onReviewNow,
						requesting: reviews.requesting,
						refusal: reviews.refusal && (
							<TraceRefusalAlert
								refusal={reviews.refusal}
								fix={
									isAdmin &&
									reviews.refusal.reason !== null && (
										<RefusalFixLink workspaceSlug={workspaceSlug} reason={reviews.refusal.reason} />
									)
								}
							/>
						),
						tab: search.reviewTab,
						onTabChange: (reviewTab) => setView({ reviewTab }),
						filters: { group: search.reviewGroup, practice: search.reviewPractice },
						onFiltersChange: (next) =>
							setView({ reviewGroup: next.group, reviewPractice: next.practice }),
						focusResultId: search.reviewResult,
						renderResult: (resultId) => (
							<OwnResultDetail
								workspaceSlug={workspaceSlug}
								resultId={resultId}
								responses={readOnly ? undefined : detail.responses}
							/>
						),
						eventLabel: (name) => name,
						groupOf,
						onOpenPractice: openPractice,
					},
				}}
				onSelectionChange={setView}
				// The way to the same group or practice across the workspace. Not in a user view,
				// where that page would count the viewer instead of the developer.
				levelFooter={
					readOnly
						? undefined
						: (entry) => (
								<ProfileFocusFooter
									entry={entry}
									stack={detailStack}
									groupOf={(slug) => groupOf(slug)?.slug}
									nameOf={(focus) =>
										focus.practiceSlug === undefined
											? groups.find((each) => each.slug === focus.groupSlug)?.name
											: practiceStandings.find((each) => each.slug === focus.practiceSlug)?.name
									}
									onSwitch={focusSwitch.toWorkspace}
								/>
							)
				}
			/>
		</>
	);
}

function OwnResultDetail({
	workspaceSlug,
	resultId,
	responses,
}: {
	workspaceSlug: string;
	resultId: string;
	responses?: FeedbackResponseControls;
}) {
	const result = useQuery(getOwnResultOptions({ path: { workspaceSlug, resultId } }));
	if (result.isError) {
		return (
			<QueryErrorAlert
				error={result.error}
				title="We could not load this result"
				onRetry={() => {
					void result.refetch();
				}}
			/>
		);
	}
	if (result.isPending) {
		return <Skeleton className="h-24 w-full" />;
	}
	return (
		<ReviewResultRow
			result={result.data}
			defaultOpen
			showPracticeName={false}
			responses={responses}
		/>
	);
}

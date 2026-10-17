import { type UseQueryResult, useQuery } from "@tanstack/react-query";
import { createFileRoute, Link } from "@tanstack/react-router";

import type { WorkspaceMembership } from "@/api/types.gen";
import { ACTIVITY_CATEGORY_DEFS } from "@/components/activity/activity-kind-defs";
import { periodFromSearch, periodSearch } from "@/components/activity/activity-period";
import {
	ACTIVITY_SEARCH_DEFAULTS,
	type ActivitySearch,
	activitySearchSchema,
	PERIOD_SEARCH_KEYS,
	parseActivityStack,
	SELF_ACTIVITY_LEVEL_KINDS,
} from "@/components/activity/activity-search";
import { ActivityDetailDrawer } from "@/components/activity/ActivityDetailDrawer";
import { type ActivityAccount, ActivityPage } from "@/components/activity/ActivityPage";
import { workLogTitle } from "@/components/activity/work-log-markdown";
import { useDetailStack } from "@/components/layout/detail-drawer/use-detail-stack";
import { buttonVariants } from "@/components/ui/button";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import { useActivityPerson, useActivityWork, useOpenWork } from "@/hooks/use-activity";
import { pageHead } from "@/lib/page-title";
import { type ProviderType, toScmProviderType } from "@/lib/provider/provider-terms";
import { useSearchState, carriedSearchParams } from "@/lib/search-params";
import { hasText } from "@/lib/text";
import { useAuth } from "@/runtime/auth/AuthContext";
import { workspaceMembershipQueryOptions } from "@/runtime/auth/guard";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/activity")({
	component: Activity,
	head: pageHead("Activity"),
	validateSearch: activitySearchSchema,
	search: {
		middlewares: carriedSearchParams<ActivitySearch>(PERIOD_SEARCH_KEYS, ACTIVITY_SEARCH_DEFAULTS),
	},
});

function Activity() {
	const { workspaceSlug } = Route.useParams();
	const search = Route.useSearch();
	const setSearch = useSearchState();
	const { userView } = useAuth();
	const { workspaces } = useActiveWorkspaceSlug();
	const workspace = workspaces.find((candidate) => candidate.workspaceSlug === workspaceSlug);
	const providerType = toScmProviderType(workspace?.providerType);
	const membership = useQuery(workspaceMembershipQueryOptions(workspaceSlug));
	const login = membership.data && providerLogin(membership.data, providerType);

	const detailStack = parseActivityStack(search.detail, SELF_ACTIVITY_LEVEL_KINDS);
	const stackControls = useDetailStack();
	const openCategory = detailStack.at(-1)?.target;

	const period = periodFromSearch(search);
	const scope = {
		workspaceSlug,
		period,
		own: true as const,
		enabled: login !== undefined,
	};
	const category = openCategory?.kind === "activity" ? openCategory.category : undefined;
	const openWork = useOpenWork({ workspaceSlug, login });
	const overview = useActivityPerson(scope);
	const timeline = useActivityWork({
		...scope,
		// Your own copy is headed by what it is; a member's or a team's names whose it is.
		copy: { title: "Activity", providerType, people: false },
	});
	const categoryWorkLog = useActivityWork({
		...scope,
		kinds: category ? ACTIVITY_CATEGORY_DEFS[category].kinds : undefined,
		copy: {
			title: workLogTitle(
				category ? ACTIVITY_CATEGORY_DEFS[category].label(providerType) : "Activity",
			),
			providerType,
			people: false,
		},
		enabled: scope.enabled && category !== undefined,
	});

	return (
		<>
			<ActivityPage
				providerType={providerType}
				account={accountOf(membership, login, userView === undefined)}
				period={period}
				onPeriodChange={(next) => {
					void setSearch((previous) => ({ ...previous, ...periodSearch(next) }), {
						state: true,
						replace: true,
					});
				}}
				openWork={openWork}
				overview={overview}
				timeline={timeline}
			/>
			<ActivityDetailDrawer
				stack={login === undefined ? [] : detailStack}
				onClose={stackControls.close}
				pageLabel="Activity"
				providerType={providerType}
				period={period}
				owner={{ login, overview, workLog: timeline, categoryWorkLog }}
			/>
		</>
	);
}

// Activity uses this workspace provider's identity, including in user view; the sign-in identity can differ.
function providerLogin(
	membership: WorkspaceMembership,
	providerType: ProviderType,
): string | undefined {
	const identityLogin = membership.account.identities.find(
		(identity) => identity.provider === providerType,
	)?.login;
	return hasText(identityLogin) ? identityLogin : undefined;
}

/**
 * Whose activity the page reads. The link to connect an account is offered only to the account's own
 * reader: in a user view, the settings it would open are the viewer's.
 */
function accountOf(
	membership: UseQueryResult<WorkspaceMembership>,
	login: string | undefined,
	offerSettings: boolean,
): ActivityAccount {
	if (membership.isPending) {
		return { status: "loading" };
	}
	if (membership.isError) {
		return {
			status: "error",
			error: membership.error,
			onRetry: () => {
				void membership.refetch();
			},
		};
	}
	if (login !== undefined) {
		return { status: "ready", login };
	}
	return {
		status: "none",
		settingsLink: offerSettings ? (
			<Link
				to="/settings"
				hash="linked-accounts-heading"
				className={buttonVariants({ variant: "outline" })}
			>
				Connect an account
			</Link>
		) : undefined,
	};
}

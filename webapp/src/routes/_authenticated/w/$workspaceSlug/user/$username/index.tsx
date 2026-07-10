import { createFileRoute, redirect, retainSearchParams, useNavigate } from "@tanstack/react-router";
import { getCurrentUserMembershipOptions } from "@/api/@tanstack/react-query.gen";
import { ProfilePage } from "@/components/profile/ProfilePage";
import {
	type ProfileSearchInput,
	type ProfileSearchParams,
	profileSearchSchema,
	useProfileData,
} from "@/hooks/use-profile-data";
import { useWorkspaceFeatures } from "@/hooks/use-workspace-features";
import { resolveCurrentUser } from "@/integrations/auth/guard";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/user/$username/")({
	// The self-view is the unified workspace home. Redirect the viewer's OWN page there BEFORE render
	// so no self queries fire and no empty frame flashes — there is one canonical self surface.
	beforeLoad: async ({ context, params }) => {
		// The URL param is an SCM login in THIS workspace's provider, so compare against the same
		// identity the home self-view renders (membership userLogin via useWorkspaceAccess), not the
		// global auth username — the two differ when the viewer signed up via another provider or was
		// renamed since signup. The membership query is shared/cached with the rest of the app; fall
		// back to the auth username when membership can't be resolved (e.g. non-member).
		const membership = await context.queryClient
			.ensureQueryData({
				...getCurrentUserMembershipOptions({ path: { workspaceSlug: params.workspaceSlug } }),
				retry: false,
			})
			.catch(() => null);
		const selfLogin =
			membership?.userLogin ?? (await resolveCurrentUser(context.queryClient))?.username;
		if (selfLogin && selfLogin.toLowerCase() === params.username.toLowerCase()) {
			throw redirect({ to: "/w/$workspaceSlug", params: { workspaceSlug: params.workspaceSlug } });
		}
	},
	component: UserProfile,
	validateSearch: profileSearchSchema,
	search: {
		middlewares: [retainSearchParams(["after", "before", "monitorRepositories", "monitorLimit"])],
	},
});

/**
 * The other-developer profile (mentor/peer view): identity + the member-visible Activity Monitor.
 * It NEVER renders the private practice-reflection cards — those live only on the self-view
 * (workspace home) and are fetched via the server-gated `GET /practices/reports/me`. The viewer's
 * own page is redirected to the unified home in `beforeLoad`, so reaching this component implies the
 * profile belongs to a DIFFERENT developer.
 */
function UserProfile() {
	const { username, workspaceSlug } = Route.useParams();
	const { achievementsEnabled } = useWorkspaceFeatures();
	const search = Route.useSearch();
	const navigate = useNavigate({ from: Route.fullPath });

	const updateSearch = (updater: (prev: ProfileSearchParams) => ProfileSearchInput) => {
		navigate({ search: updater });
	};

	const profile = useProfileData({ workspaceSlug, username, search, updateSearch });

	return (
		<ProfilePage
			providerType={profile.providerType}
			profileData={profile.profileData}
			activityMonitorData={profile.activityMonitorData}
			activityMonitorFilters={profile.activityMonitorFilters}
			onActivityMonitorFiltersChange={profile.handleActivityMonitorFiltersChange}
			isLoading={profile.isLoading}
			error={profile.isError}
			username={username}
			currUserIsDashboardUser={false}
			workspaceSlug={workspaceSlug}
			after={profile.after}
			before={profile.before}
			onTimeframeChange={profile.handleTimeframeChange}
			schedule={profile.schedule}
			achievementsEnabled={achievementsEnabled}
		/>
	);
}

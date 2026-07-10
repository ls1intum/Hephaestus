import { useQuery } from "@tanstack/react-query";
import { createFileRoute, retainSearchParams, useNavigate } from "@tanstack/react-router";
import { getMyPracticeReportOptions } from "@/api/@tanstack/react-query.gen";
import { PracticeReflectionSection } from "@/components/practices/reflection/PracticeReflectionSection";
import { ProfilePage } from "@/components/profile/ProfilePage";
import { NoWorkspace } from "@/components/workspace/NoWorkspace";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import {
	type ProfileSearchInput,
	type ProfileSearchParams,
	profileSearchSchema,
	useProfileData,
} from "@/hooks/use-profile-data";
import { useWorkspaceAccess } from "@/hooks/use-workspace-access";
import { useWorkspaceFeatures } from "@/hooks/use-workspace-features";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/")({
	component: WorkspaceHome,
	validateSearch: profileSearchSchema,
	search: {
		middlewares: [retainSearchParams(["after", "before", "monitorRepositories", "monitorLimit"])],
	},
});

function WorkspaceHome() {
	const { workspaceSlug, isLoading: isWorkspaceLoading } = useActiveWorkspaceSlug();
	const workspaceAccess = useWorkspaceAccess();
	const currentLogin = workspaceAccess.userLogin;
	const { achievementsEnabled, practicesEnabled } = useWorkspaceFeatures(workspaceSlug);
	const search = Route.useSearch();
	const navigate = useNavigate({ from: Route.fullPath });
	const slug = workspaceSlug ?? "";
	const hasWorkspace = Boolean(workspaceSlug);

	const updateSearch = (updater: (prev: ProfileSearchParams) => ProfileSearchInput) => {
		navigate({ search: updater });
	};

	const profile = useProfileData({
		workspaceSlug: slug,
		username: currentLogin ?? "",
		search,
		updateSearch,
	});

	const reflectionQuery = useQuery({
		...getMyPracticeReportOptions({ path: { workspaceSlug: slug } }),
		enabled: hasWorkspace && practicesEnabled,
	});

	const practicesSection = practicesEnabled ? (
		<PracticeReflectionSection
			practices={reflectionQuery.data}
			isLoading={reflectionQuery.isPending && hasWorkspace}
			isError={reflectionQuery.isError}
			onRetry={() => reflectionQuery.refetch()}
		/>
	) : null;

	if (!isWorkspaceLoading && !hasWorkspace) {
		return <NoWorkspace />;
	}

	if (hasWorkspace && !currentLogin && !workspaceAccess.isLoading) {
		return (
			<div className="container mx-auto flex max-w-3xl flex-col gap-6 py-6">
				<p className="text-sm text-muted-foreground">
					Your profile is still syncing.
					{practicesEnabled ? " Your practice feedback is shown below." : ""}
				</p>
				{practicesSection}
			</div>
		);
	}

	return (
		<ProfilePage
			providerType={profile.providerType}
			profileData={profile.profileData}
			activityMonitorData={profile.activityMonitorData}
			activityMonitorFilters={profile.activityMonitorFilters}
			onActivityMonitorFiltersChange={profile.handleActivityMonitorFiltersChange}
			isLoading={isWorkspaceLoading || workspaceAccess.isLoading || profile.isLoading}
			error={profile.isError}
			username={currentLogin ?? ""}
			currUserIsDashboardUser
			workspaceSlug={slug}
			after={profile.after}
			before={profile.before}
			onTimeframeChange={profile.handleTimeframeChange}
			schedule={profile.schedule}
			achievementsEnabled={achievementsEnabled}
			practicesSlot={practicesSection ?? undefined}
		/>
	);
}

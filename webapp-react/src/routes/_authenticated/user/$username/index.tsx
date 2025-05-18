import { getUserProfileOptions } from "@/api/@tanstack/react-query.gen";
import { ProfilePage } from "@/components/profile/ProfilePage";
import { useAuth } from "@/integrations/auth/AuthContext";
import { useQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";

export const Route = createFileRoute("/_authenticated/user/$username/")({
	component: UserProfile,
});

function UserProfile() {
	const { username } = Route.useParams();
	const { isCurrentUser } = useAuth();

	// Check if current user is the dashboard user
	const currUserIsDashboardUser = isCurrentUser(username);

	// Query for user profile data
	const profileQuery = useQuery({
		...getUserProfileOptions({
			path: { login: username },
		}),
		enabled: Boolean(username),
	});

	return (
		<ProfilePage
			profileData={profileQuery.data}
			isLoading={profileQuery.isLoading || profileQuery.isFetching}
			error={profileQuery.isError}
			username={username}
			currUserIsDashboardUser={currUserIsDashboardUser}
		/>
	);
}

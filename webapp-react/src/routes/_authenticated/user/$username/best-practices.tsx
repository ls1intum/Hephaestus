import {
	getActivityByUserOptions,
	getActivityByUserQueryKey,
	getUserProfileOptions,
} from "@/api/@tanstack/react-query.gen";
import { detectBadPracticesByUser } from "@/api/sdk.gen";
import { PracticesPage } from "@/components/practices/PracticesPage";
import { useAuth } from "@/integrations/auth/AuthContext";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";

export const Route = createFileRoute(
	"/_authenticated/user/$username/best-practices",
)({
	component: BestPracticesContainer,
});

export function BestPracticesContainer() {
	const { username } = Route.useParams();
	const { isCurrentUser } = useAuth();
	const queryClient = useQueryClient();

	// Check if current user is the dashboard user
	const currUserIsDashboardUser = isCurrentUser(username);

	// Query for activity data
	const activityQuery = useQuery({
		...getActivityByUserOptions({
			path: { login: username },
		}),
		enabled: Boolean(username),
	});

	// Query for user profile data to get display name
	const profileQuery = useQuery({
		...getUserProfileOptions({
			path: { login: username },
		}),
		enabled: Boolean(username),
	});

	// Mutation for detecting bad practices
	const detectMutation = useMutation({
		mutationFn: () =>
			detectBadPracticesByUser({
				path: { login: username },
			}),
		onSuccess: () => {
			queryClient.invalidateQueries({
				queryKey: getActivityByUserQueryKey({
					path: { login: username },
				}),
			});
		},
	});

	// Get user's display name from profile data
	const displayName = profileQuery.data?.userInfo?.name;

	return (
		<PracticesPage
			activityData={activityQuery.data}
			isLoading={activityQuery.isLoading || activityQuery.isFetching}
			isDetectingBadPractices={detectMutation.isPending}
			username={username}
			displayName={displayName}
			currUserIsDashboardUser={currUserIsDashboardUser}
			onDetectBadPractices={detectMutation.mutate}
		/>
	);
}

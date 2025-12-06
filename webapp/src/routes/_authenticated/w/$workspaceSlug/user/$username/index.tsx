import { useQuery } from "@tanstack/react-query";
import {
	createFileRoute,
	retainSearchParams,
	useNavigate,
} from "@tanstack/react-router";
import { zodValidator } from "@tanstack/zod-adapter";
import { endOfISOWeek, formatISO, startOfISOWeek } from "date-fns";
import { z } from "zod";
import { getUserProfileOptions } from "@/api/@tanstack/react-query.gen";
import { ProfilePage } from "@/components/profile/ProfilePage";
import { useAuth } from "@/integrations/auth/AuthContext";

const today = new Date();
const profileDefaultAfter = formatISO(startOfISOWeek(today));
const profileDefaultBefore = formatISO(endOfISOWeek(today));

const profileSearchSchema = z.object({
	after: z.string().default(profileDefaultAfter),
	before: z.string().optional().default(profileDefaultBefore),
});

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/user/$username/",
)({
	component: UserProfile,
	validateSearch: zodValidator(profileSearchSchema),
	search: {
		middlewares: [retainSearchParams(["after", "before"])],
	},
});

function UserProfile() {
	const { username, workspaceSlug } = Route.useParams();
	const { isCurrentUser } = useAuth();
	const { after, before } = Route.useSearch();
	const navigate = useNavigate({ from: Route.fullPath });

	// Check if current user is the dashboard user
	const currUserIsDashboardUser = isCurrentUser(username);

	// Query for user profile data
	const profileQuery = useQuery({
		...getUserProfileOptions({
			path: { workspaceSlug, login: username },
			query: {
				after: after ? new Date(after) : undefined,
				before: before ? new Date(before) : undefined,
			},
		}),
		enabled: Boolean(username) && Boolean(workspaceSlug),
	});

	const handleTimeframeChange = (nextAfter: string, nextBefore?: string) => {
		const resolvedBefore = nextBefore ?? formatISO(new Date());
		navigate({
			search: (prev) => ({
				...prev,
				after: nextAfter,
				before: resolvedBefore,
			}),
		});
	};

	return (
		<ProfilePage
			profileData={profileQuery.data}
			isLoading={profileQuery.isLoading || profileQuery.isFetching}
			error={profileQuery.isError}
			username={username}
			currUserIsDashboardUser={currUserIsDashboardUser}
			workspaceSlug={workspaceSlug}
			after={after}
			before={before}
			onTimeframeChange={handleTimeframeChange}
		/>
	);
}

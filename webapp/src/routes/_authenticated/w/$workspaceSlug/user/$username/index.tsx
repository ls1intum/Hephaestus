import { useQuery } from "@tanstack/react-query";
import {
	createFileRoute,
	retainSearchParams,
	useNavigate,
} from "@tanstack/react-router";
import { zodValidator } from "@tanstack/zod-adapter";
import { formatISO, startOfISOWeek } from "date-fns";
import { z } from "zod";
import { getUserProfileOptions } from "@/api/@tanstack/react-query.gen";
import { ProfilePage } from "@/components/profile/ProfilePage";
import { useAuth } from "@/integrations/auth/AuthContext";

const profileSearchSchema = z.object({
	after: z.string().default(() => formatISO(startOfISOWeek(new Date()))),
	before: z.string().optional(),
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

	const parseDateParam = (value?: string) => {
		if (!value) return undefined;
		const parsed = new Date(value);
		return Number.isNaN(parsed.getTime()) ? undefined : parsed;
	};

	// Check if current user is the dashboard user
	const currUserIsDashboardUser = isCurrentUser(username);

	// Query for user profile data
	const profileQuery = useQuery({
		...getUserProfileOptions({
			path: { workspaceSlug, login: username },
			query: {
				after: parseDateParam(after),
				before: parseDateParam(before),
			},
		}),
		placeholderData: (previousData) => previousData,
		enabled: Boolean(username) && Boolean(workspaceSlug),
	});

	const handleTimeframeChange = (nextAfter: string, nextBefore?: string) => {
		navigate({
			search: (prev) => ({
				...prev,
				after: nextAfter,
				before: nextBefore,
			}),
		});
	};

	return (
		<ProfilePage
			profileData={profileQuery.data}
			isLoading={profileQuery.isPending && !profileQuery.data}
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

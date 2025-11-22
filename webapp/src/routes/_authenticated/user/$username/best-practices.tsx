import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { toast } from "sonner";
import {
	detectBadPracticesByUserMutation,
	detectBadPracticesForPullRequestMutation,
	getActivityByUserOptions,
	getActivityByUserQueryKey,
	getUserProfileOptions,
	provideFeedbackForBadPracticeMutation,
	resolveBadPracticeMutation,
} from "@/api/@tanstack/react-query.gen";
import type { BadPracticeFeedback } from "@/api/types.gen";
import { PracticesPage } from "@/components/practices/PracticesPage";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import { useAuth } from "@/integrations/auth/AuthContext";

export const Route = createFileRoute(
	"/_authenticated/user/$username/best-practices",
)({
	component: BestPracticesContainer,
});

export function BestPracticesContainer() {
	const { username } = Route.useParams();
	const { isCurrentUser } = useAuth();
	const queryClient = useQueryClient();
	const { workspaceSlug } = useActiveWorkspaceSlug();
	const slug = workspaceSlug ?? "";
	const hasWorkspace = Boolean(workspaceSlug);

	// Check if current user is the dashboard user
	const currUserIsDashboardUser = isCurrentUser(username);

	// Query for activity data
	const activityQuery = useQuery({
		...getActivityByUserOptions({
			path: { workspaceSlug: slug, login: username },
		}),
		enabled: hasWorkspace && Boolean(username),
	});

	// Query for user profile data to get display name
	const profileQuery = useQuery({
		...getUserProfileOptions({
			path: { login: username },
		}),
		enabled: Boolean(username),
	});

	// Mutation for detecting bad practices
	const detect = useMutation({
		...detectBadPracticesByUserMutation(),
		onSuccess: () => {
			queryClient.invalidateQueries({
				queryKey: getActivityByUserQueryKey({
					path: { workspaceSlug: slug, login: username },
				}),
			});
		},
		onError: () => {
			toast.error(
				"Your pull requests have not changed since the last detection. Try changing status or description, then run the detection again.",
			);
		},
	});

	// Mutation for resolving bad practices
	const resolveBadPractice = useMutation({
		...resolveBadPracticeMutation(),
		onSuccess: () => {
			queryClient.invalidateQueries({
				queryKey: getActivityByUserQueryKey({
					path: { workspaceSlug: slug, login: username },
				}),
			});
		},
		onError: () => {
			toast.error("Failed to update practice status");
		},
	});

	// Mutation for providing feedback on bad practices
	const provideFeedbackForBadPractice = useMutation({
		...provideFeedbackForBadPracticeMutation(),
		onSuccess: () => {
			queryClient.invalidateQueries({
				queryKey: getActivityByUserQueryKey({
					path: { workspaceSlug: slug, login: username },
				}),
			});
			toast.success("Feedback submitted successfully");
		},
		onError: () => {
			toast.error("Failed to submit feedback");
		},
	});

	// Mutation for detecting bad practices for a specific pull request
	const detectBadPracticesForPullRequest = useMutation({
		...detectBadPracticesForPullRequestMutation(),
		onSuccess: () => {
			queryClient.invalidateQueries({
				queryKey: getActivityByUserQueryKey({
					path: { workspaceSlug: slug, login: username },
				}),
			});
		},
		onError: () => {
			toast.error(
				"This pull request has not changed since the last detection. Try changing status or description, then run the detection again.",
			);
		},
	});

	// Get user's display name from profile data
	const displayName = profileQuery.data?.userInfo?.name;

	const onDetectBadPractices = () => {
		if (!hasWorkspace) {
			return;
		}
		detect.mutate({
			path: { workspaceSlug: slug, login: username },
		});
	};

	const onDetectBadPracticesForPullRequest = (pullRequestId: number) => {
		if (!hasWorkspace) {
			return;
		}
		detectBadPracticesForPullRequest.mutate({
			path: { workspaceSlug: slug, pullRequestId },
		});
	};

	const onResolveBadPracticeAsFixed = (badPracticeId: number) => {
		if (!hasWorkspace) {
			return;
		}
		resolveBadPractice.mutate({
			path: { workspaceSlug: slug, badPracticeId },
			query: { state: "FIXED" },
		});
	};

	const onResolveBadPracticeAsWontFix = (badPracticeId: number) => {
		if (!hasWorkspace) {
			return;
		}
		resolveBadPractice.mutate({
			path: { workspaceSlug: slug, badPracticeId },
			query: { state: "WONT_FIX" },
		});
	};

	const onResolveBadPracticeAsWrong = (badPracticeId: number) => {
		if (!hasWorkspace) {
			return;
		}
		resolveBadPractice.mutate({
			path: { workspaceSlug: slug, badPracticeId },
			query: { state: "WRONG" },
		});
	};

	const onProvideBadPracticeFeedback = (
		badPracticeId: number,
		feedback: BadPracticeFeedback,
	) => {
		if (!hasWorkspace) {
			return;
		}
		provideFeedbackForBadPractice.mutate({
			path: { workspaceSlug: slug, badPracticeId },
			body: feedback,
		});
	};

	return (
		<PracticesPage
			activityData={activityQuery.data}
			isLoading={activityQuery.isLoading}
			isDetectingBadPractices={
				detect.isPending || detectBadPracticesForPullRequest.isPending
			}
			username={username}
			displayName={displayName}
			currUserIsDashboardUser={currUserIsDashboardUser}
			onDetectBadPractices={onDetectBadPractices}
			onDetectBadPracticesForPullRequest={onDetectBadPracticesForPullRequest}
			onResolveBadPracticeAsFixed={onResolveBadPracticeAsFixed}
			onResolveBadPracticeAsWontFix={onResolveBadPracticeAsWontFix}
			onResolveBadPracticeAsWrong={onResolveBadPracticeAsWrong}
			onProvideBadPracticeFeedback={onProvideBadPracticeFeedback}
		/>
	);
}

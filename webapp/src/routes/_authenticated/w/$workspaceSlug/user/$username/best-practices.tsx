import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useEffect } from "react";
import { toast } from "sonner";
import {
	detectForPullRequestMutation,
	detectForUserMutation,
	getBadPracticesForUserOptions,
	getBadPracticesForUserQueryKey,
	getUserProfileOptions,
	provideFeedbackMutation,
	resolveMutation,
} from "@/api/@tanstack/react-query.gen";
import type { BadPracticeFeedback } from "@/api/types.gen";
import { PracticesPage } from "@/components/practices/PracticesPage";
import { Spinner } from "@/components/ui/spinner";
import { NoWorkspace } from "@/components/workspace/NoWorkspace";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import { useWorkspaceFeatures } from "@/hooks/use-workspace-features";
import { useAuth } from "@/integrations/auth/AuthContext";
import { getProviderTerms } from "@/lib/provider";

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/user/$username/best-practices",
)({
	component: BestPracticesContainer,
});

export function BestPracticesContainer() {
	const { username } = Route.useParams();
	const { isCurrentUser } = useAuth();
	const queryClient = useQueryClient();
	const navigate = useNavigate();
	const { workspaceSlug, providerType } = useActiveWorkspaceSlug();
	const { practicesEnabled, isLoading: featuresLoading } = useWorkspaceFeatures();
	const terms = getProviderTerms(providerType);
	const slug = workspaceSlug ?? "";
	const hasWorkspace = Boolean(workspaceSlug);
	const showNoWorkspace = !hasWorkspace;

	// Check if current user is the dashboard user
	const currUserIsDashboardUser = isCurrentUser(username);

	// Query for activity data
	const activityQuery = useQuery({
		...getBadPracticesForUserOptions({
			path: { workspaceSlug: slug, login: username },
		}),
		enabled: hasWorkspace && Boolean(username),
	});

	// Query for user profile data to get display name
	const profileQuery = useQuery({
		...getUserProfileOptions({
			path: { workspaceSlug: slug, login: username },
		}),
		enabled: hasWorkspace && Boolean(username),
	});

	// Mutation for detecting bad practices
	const detect = useMutation({
		...detectForUserMutation(),
		onSuccess: () => {
			queryClient.invalidateQueries({
				queryKey: getBadPracticesForUserQueryKey({
					path: { workspaceSlug: slug, login: username },
				}),
			});
		},
		onError: () => {
			toast.error(
				`Your ${terms.pullRequests.toLowerCase()} have not changed since the last detection. Try changing status or description, then run the detection again.`,
			);
		},
	});

	// Mutation for resolving bad practices
	const resolveBadPractice = useMutation({
		...resolveMutation(),
		onSuccess: () => {
			queryClient.invalidateQueries({
				queryKey: getBadPracticesForUserQueryKey({
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
		...provideFeedbackMutation(),
		onSuccess: () => {
			queryClient.invalidateQueries({
				queryKey: getBadPracticesForUserQueryKey({
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
		...detectForPullRequestMutation(),
		onSuccess: () => {
			queryClient.invalidateQueries({
				queryKey: getBadPracticesForUserQueryKey({
					path: { workspaceSlug: slug, login: username },
				}),
			});
		},
		onError: () => {
			toast.error(
				`This ${terms.pullRequest.toLowerCase()} has not changed since the last detection. Try changing status or description, then run the detection again.`,
			);
		},
	});

	// Feature guard — redirect to profile when practices are disabled
	useEffect(() => {
		if (!featuresLoading && !practicesEnabled && workspaceSlug) {
			toast.error("Best practices are not enabled for this workspace");
			navigate({
				to: "/w/$workspaceSlug/user/$username",
				params: { workspaceSlug, username },
				replace: true,
			});
		}
	}, [featuresLoading, practicesEnabled, workspaceSlug, username, navigate]);

	if (featuresLoading || !practicesEnabled) {
		return (
			<div className="flex items-center justify-center h-96">
				<Spinner className="size-8" />
			</div>
		);
	}

	if (showNoWorkspace) {
		return <NoWorkspace />;
	}

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
			path: { workspaceSlug: slug, id: badPracticeId },
			query: { state: "FIXED" },
		});
	};

	const onResolveBadPracticeAsWontFix = (badPracticeId: number) => {
		if (!hasWorkspace) {
			return;
		}
		resolveBadPractice.mutate({
			path: { workspaceSlug: slug, id: badPracticeId },
			query: { state: "WONT_FIX" },
		});
	};

	const onResolveBadPracticeAsWrong = (badPracticeId: number) => {
		if (!hasWorkspace) {
			return;
		}
		resolveBadPractice.mutate({
			path: { workspaceSlug: slug, id: badPracticeId },
			query: { state: "WRONG" },
		});
	};

	const onProvideBadPracticeFeedback = (badPracticeId: number, feedback: BadPracticeFeedback) => {
		if (!hasWorkspace) {
			return;
		}
		provideFeedbackForBadPractice.mutate({
			path: { workspaceSlug: slug, id: badPracticeId },
			body: feedback,
		});
	};

	return (
		<PracticesPage
			providerType={providerType}
			activityData={activityQuery.data}
			isLoading={activityQuery.isLoading}
			isDetectingBadPractices={detect.isPending || detectBadPracticesForPullRequest.isPending}
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

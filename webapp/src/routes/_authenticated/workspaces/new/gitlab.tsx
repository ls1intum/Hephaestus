import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, Link, Navigate, useNavigate } from "@tanstack/react-router";
import { ArrowLeftIcon, OctagonXIcon } from "lucide-react";
import { useEffect, useMemo, useReducer, useRef, useState } from "react";
import { toast } from "sonner";
import {
	claimIdentityMutation,
	createWorkspaceMutation,
	getIdentityProvidersOptions,
	getLinkedAccountsQueryKey,
	getProvidersOptions,
	listGitLabGroupsMutation,
	listWorkspacesQueryKey,
} from "@/api/@tanstack/react-query.gen";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { ConfigureWorkspaceStep } from "@/components/workspace/create-workspace/ConfigureWorkspaceStep";
import { ConnectGitLabStep } from "@/components/workspace/create-workspace/ConnectGitLabStep";
import { SelectGroupStep } from "@/components/workspace/create-workspace/SelectGroupStep";
import { workspaceDetailsSchema } from "@/components/workspace/create-workspace/schemas";
import { WizardStepIndicator } from "@/components/workspace/create-workspace/WizardStepIndicator";
import {
	createInitialWizardState,
	WizardContext,
	wizardReducer,
} from "@/components/workspace/create-workspace/wizard-context";
import { useAuth } from "@/integrations/auth/AuthContext";
import { useWorkspaceStore } from "@/stores/workspace-store";

export const Route = createFileRoute("/_authenticated/workspaces/new/gitlab")({
	component: GitLabWizardPage,
});

const STEP_META = [
	{
		title: "Connect to GitLab",
		description: "Enter your GitLab instance URL and access token.",
	},
	{ title: "Select a Group", description: "Choose the GitLab group to monitor." },
	{ title: "Configure Workspace", description: "Set a name and URL slug for your workspace." },
] as const;

/**
 * Prompts the user to link their GitLab account. Offers two paths:
 * 1. "Link GitLab" — standard Keycloak account linking (works when no conflict)
 * 2. "Claim GitLab Identity" — transfers the identity from an orphan account (handles the
 *    case where the user previously logged in via GitLab directly, creating a separate account)
 */
function GitLabLinkPrompt({
	gitlabIdpAlias,
	linkAccount,
}: {
	gitlabIdpAlias?: string;
	linkAccount: (alias: string) => Promise<void>;
}) {
	const queryClient = useQueryClient();
	const [showClaim, setShowClaim] = useState(false);

	const claimMutation = useMutation({
		...claimIdentityMutation(),
		onSuccess: () => {
			toast.success(
				"GitLab account linked successfully. Please log out and back in for the changes to take effect.",
			);
			queryClient.invalidateQueries({ queryKey: getLinkedAccountsQueryKey() });
		},
		onError: (error) => {
			toast.error(`Failed to claim identity: ${(error as Error).message}`);
		},
	});

	return (
		<div className="mx-auto max-w-2xl py-8">
			<Link
				to="/workspaces/new"
				className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground mb-6"
			>
				<ArrowLeftIcon className="size-3.5" />
				Back
			</Link>

			<div className="space-y-4">
				<div className="space-y-1.5">
					<h1 className="text-2xl font-semibold tracking-tight">Link Your GitLab Account</h1>
					<p className="text-muted-foreground">
						To create a GitLab workspace, you need to link your GitLab account first.
					</p>
				</div>

				<div className="flex flex-col gap-3">
					{gitlabIdpAlias && (
						<Button className="w-fit" onClick={() => linkAccount(gitlabIdpAlias)}>
							Link GitLab Account
						</Button>
					)}

					{!showClaim && (
						<button
							type="button"
							className="text-sm text-muted-foreground hover:text-foreground text-left"
							onClick={() => setShowClaim(true)}
						>
							Already logged in with GitLab before? Having trouble linking?
						</button>
					)}

					{showClaim && gitlabIdpAlias && (
						<Alert>
							<AlertTitle>Previously logged in with GitLab?</AlertTitle>
							<AlertDescription className="space-y-3">
								<p>
									If you previously signed in with GitLab directly, a separate account was created.
									Click below to merge it with your current account.
								</p>
								<Button
									variant="outline"
									size="sm"
									disabled={claimMutation.isPending}
									onClick={() => claimMutation.mutate({ path: { providerAlias: gitlabIdpAlias } })}
								>
									{claimMutation.isPending ? <Spinner className="mr-2" /> : null}
									Merge GitLab Identity
								</Button>
							</AlertDescription>
						</Alert>
					)}
				</div>
			</div>
		</div>
	);
}

function GitLabWizardPage() {
	const { hasGitLabIdentity, linkAccount } = useAuth();

	const {
		data: providers,
		isLoading: providersLoading,
		isError: providersError,
	} = useQuery({
		...getProvidersOptions(),
		staleTime: 5 * 60 * 1000,
	});

	// Find the GitLab IdP alias for account linking
	const { data: identityProviders } = useQuery({
		...getIdentityProvidersOptions(),
		staleTime: 5 * 60 * 1000,
	});
	const gitlabIdpAlias = identityProviders?.find((p) => p.alias.startsWith("gitlab"))?.alias;

	const gitlabEnabled = !!providers?.gitlab;
	const defaultServerUrl = providers?.gitlab?.defaultServerUrl;

	const [state, dispatch] = useReducer(wizardReducer, defaultServerUrl, (url) =>
		createInitialWizardState(url),
	);
	const queryClient = useQueryClient();
	const navigate = useNavigate();
	const { setSelectedSlug } = useWorkspaceStore();
	const headingRef = useRef<HTMLHeadingElement>(null);

	const stepAnnouncement = `Step ${state.step} of 3: ${STEP_META[state.step - 1].title}`;

	const listGroups = useMutation({
		...listGitLabGroupsMutation(),
		onError: (error) => {
			console.error("Failed to load GitLab groups:", {
				serverUrl: state.serverUrl,
				message: error instanceof Error ? error.message : "Unknown error",
			});
		},
	});

	const createWorkspace = useMutation({
		...createWorkspaceMutation(),
		onSuccess: (data) => {
			setSelectedSlug(data.workspaceSlug);
			toast.success(`Workspace "${data.displayName}" created`);
			navigate({
				to: "/w/$workspaceSlug",
				params: { workspaceSlug: data.workspaceSlug },
			});
		},
		onError: (error) => {
			console.error("Workspace creation failed:", {
				step: state.step,
				serverUrl: state.serverUrl,
				message: error instanceof Error ? error.message : "Unknown error",
			});
			const rawError =
				typeof error === "object" && error !== null && "error" in error
					? (error as Record<string, unknown>).error
					: undefined;
			toast.error(
				typeof rawError === "string" ? rawError : "Failed to create workspace. Please try again.",
			);
		},
		onSettled: () => {
			queryClient.invalidateQueries({ queryKey: listWorkspacesQueryKey() });
		},
	});

	const canAdvanceFromStep1 = state.preflightResult?.valid === true;
	const canAdvanceFromStep2 = state.selectedGroup !== null;
	const canSubmit = useMemo(
		() =>
			state.step === 3 &&
			state.selectedGroup !== null &&
			workspaceDetailsSchema.safeParse({
				displayName: state.displayName,
				workspaceSlug: state.workspaceSlug,
			}).success,
		[state.step, state.selectedGroup, state.displayName, state.workspaceSlug],
	);

	// biome-ignore lint/correctness/useExhaustiveDependencies: state.step is an intentional trigger to refocus heading on step change
	useEffect(() => {
		headingRef.current?.focus();
	}, [state.step]);

	const handleNext = () => {
		if (state.step === 1 && canAdvanceFromStep1) {
			if (listGroups.isPending) return;
			listGroups.mutate(
				{
					body: {
						personalAccessToken: state.personalAccessToken,
						serverUrl: state.serverUrl || undefined,
					},
				},
				{
					onSuccess: (data) => {
						dispatch({ type: "ADVANCE_TO_GROUPS", groups: data });
					},
				},
			);
		} else if (state.step === 2 && canAdvanceFromStep2) {
			dispatch({ type: "ADVANCE_TO_CONFIGURE" });
		}
	};

	const handleBack = () => {
		// Reset stale mutation state so old errors don't persist after back-navigation
		if (state.step === 2) listGroups.reset();
		dispatch({ type: "GO_BACK" });
	};

	const handleSubmit = () => {
		if (!canSubmit || !state.selectedGroup || createWorkspace.isPending) return;
		createWorkspace.mutate({
			body: {
				workspaceSlug: state.workspaceSlug,
				displayName: state.displayName,
				accountLogin: state.selectedGroup.fullPath,
				accountType: "ORG",
				gitProviderMode: "GITLAB_PAT",
				personalAccessToken: state.personalAccessToken,
				serverUrl: state.serverUrl || undefined,
			},
		});
	};

	const meta = STEP_META[state.step - 1];
	const wizardContextValue = useMemo(() => ({ state, dispatch }), [state]);
	const isTransitioning = listGroups.isPending;
	const isCreating = createWorkspace.isPending;
	if (providersLoading) {
		return (
			<div className="flex justify-center py-16">
				<Spinner />
			</div>
		);
	}
	if (providersError) {
		return (
			<div className="mx-auto max-w-2xl py-8">
				<Alert variant="destructive">
					<OctagonXIcon aria-hidden="true" />
					<AlertTitle>Unable to load</AlertTitle>
					<AlertDescription>
						Could not check feature availability. Please refresh the page.
					</AlertDescription>
				</Alert>
			</div>
		);
	}
	if (!gitlabEnabled) {
		return <Navigate to="/workspaces/new" />;
	}

	if (!hasGitLabIdentity) {
		return <GitLabLinkPrompt gitlabIdpAlias={gitlabIdpAlias} linkAccount={linkAccount} />;
	}

	return (
		<div className="mx-auto max-w-2xl py-8">
			{/* Visually hidden live region for screen reader step announcements */}
			<div aria-live="polite" aria-atomic="true" className="sr-only">
				{stepAnnouncement}
			</div>

			<Link
				to="/workspaces/new"
				className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground mb-6"
				aria-label="Back to provider selection"
			>
				<ArrowLeftIcon className="size-3.5" />
				Back
			</Link>

			<div className="space-y-1.5 mb-6">
				<h1
					id="wizard-heading"
					ref={headingRef}
					tabIndex={-1}
					className="text-2xl font-semibold tracking-tight outline-none"
				>
					{meta.title}
				</h1>
				<p className="text-muted-foreground">{meta.description}</p>
			</div>

			<WizardStepIndicator currentStep={state.step} />

			<div className="mt-6" role="region" aria-labelledby="wizard-heading">
				<WizardContext.Provider value={wizardContextValue}>
					{state.step === 1 && <ConnectGitLabStep />}
					{state.step === 2 && <SelectGroupStep />}
					{state.step === 3 && <ConfigureWorkspaceStep />}
				</WizardContext.Provider>
			</div>

			{listGroups.isError && state.step === 1 && (
				<Alert variant="destructive" className="mt-4">
					<OctagonXIcon aria-hidden="true" />
					<AlertTitle>Failed to load groups</AlertTitle>
					<AlertDescription>
						Could not load groups. Check your connection, server URL, and token permissions.
					</AlertDescription>
				</Alert>
			)}

			<div className="flex justify-end gap-2 mt-6">
				{state.step > 1 && (
					<Button
						variant="outline"
						onClick={handleBack}
						disabled={isCreating}
						aria-label="Back to previous step"
					>
						Back
					</Button>
				)}
				{state.step < 3 && (
					<Button
						onClick={handleNext}
						disabled={
							(state.step === 1 && !canAdvanceFromStep1) ||
							(state.step === 2 && !canAdvanceFromStep2) ||
							isTransitioning
						}
					>
						{isTransitioning && <Spinner className="mr-2" />}
						Next
					</Button>
				)}
				{state.step === 3 && (
					<Button onClick={handleSubmit} disabled={!canSubmit || isCreating}>
						{isCreating && <Spinner className="mr-2" />}
						Create Workspace
					</Button>
				)}
			</div>
		</div>
	);
}

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
import { ArrowLeftIcon, OctagonXIcon } from "lucide-react";
import { useReducer } from "react";
import { toast } from "sonner";
import {
	createWorkspaceMutation,
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
	initialWizardState,
	WizardContext,
	wizardReducer,
} from "@/components/workspace/create-workspace/wizard-context";
import { useWorkspaceStore } from "@/stores/workspace-store";

export const Route = createFileRoute("/_authenticated/workspaces/new/gitlab")({
	component: GitLabWizardPage,
});

const STEP_META = [
	{
		title: "Connect to GitLab",
		description: "Enter your GitLab instance URL and personal access token.",
	},
	{ title: "Select a Group", description: "Choose the GitLab group to monitor." },
	{ title: "Configure Workspace", description: "Set a name and URL slug for your workspace." },
] as const;

function GitLabWizardPage() {
	const [state, dispatch] = useReducer(wizardReducer, initialWizardState);
	const queryClient = useQueryClient();
	const navigate = useNavigate();
	const { setSelectedSlug } = useWorkspaceStore();

	const listGroups = useMutation({ ...listGitLabGroupsMutation() });

	const createWorkspace = useMutation({
		...createWorkspaceMutation(),
		onSuccess: (data) => {
			queryClient.invalidateQueries({ queryKey: listWorkspacesQueryKey() });
			setSelectedSlug(data.workspaceSlug);
			toast.success(`Workspace "${data.displayName}" created`);
			navigate({
				to: "/w/$workspaceSlug",
				params: { workspaceSlug: data.workspaceSlug },
			});
		},
		onError: (error) => {
			const message = error instanceof Error ? error.message : String(error);
			if (message.includes("409") || message.toLowerCase().includes("conflict")) {
				toast.error("A workspace with this slug already exists. Choose a different slug.");
			} else {
				toast.error(`Failed to create workspace: ${message}`);
			}
		},
	});

	const canAdvanceFromStep1 = state.preflightResult?.valid === true;
	const canAdvanceFromStep2 = state.selectedGroup !== null;
	const canSubmit =
		state.selectedGroup !== null &&
		workspaceDetailsSchema.safeParse({
			displayName: state.displayName,
			workspaceSlug: state.workspaceSlug,
		}).success;

	const handleNext = () => {
		if (state.step === 1 && canAdvanceFromStep1) {
			listGroups.reset();
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

	const handleSubmit = () => {
		if (!canSubmit || !state.selectedGroup) return;
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
	const isTransitioning = listGroups.isPending;
	const isCreating = createWorkspace.isPending;
	const stepKey = `step-${state.step}`;

	return (
		<div className="mx-auto max-w-lg py-8">
			<Link
				to="/workspaces/new"
				className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground mb-6"
			>
				<ArrowLeftIcon className="size-3.5" />
				Back
			</Link>

			<div className="space-y-1.5 mb-6">
				<h1 className="text-2xl font-semibold tracking-tight">{meta.title}</h1>
				<p className="text-muted-foreground">{meta.description}</p>
			</div>

			<WizardStepIndicator currentStep={state.step} />

			<div className="mt-6">
				<WizardContext.Provider value={{ state, dispatch }}>
					{state.step === 1 && <ConnectGitLabStep key={stepKey} />}
					{state.step === 2 && <SelectGroupStep key={stepKey} />}
					{state.step === 3 && <ConfigureWorkspaceStep key={stepKey} />}
				</WizardContext.Provider>
			</div>

			{listGroups.isError && state.step === 1 && (
				<Alert variant="destructive" className="mt-4">
					<OctagonXIcon />
					<AlertTitle>Failed to load groups</AlertTitle>
					<AlertDescription>
						Could not fetch accessible groups. Your token may lack the required scopes.
					</AlertDescription>
				</Alert>
			)}

			<div className="flex justify-end gap-2 mt-6">
				{state.step > 1 && (
					<Button
						variant="outline"
						onClick={() => dispatch({ type: "GO_BACK" })}
						disabled={isCreating}
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

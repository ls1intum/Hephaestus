import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "@tanstack/react-router";
import { OctagonXIcon } from "lucide-react";
import { useReducer } from "react";
import { toast } from "sonner";
import {
	createWorkspaceMutation,
	listGitLabGroupsMutation,
	listWorkspacesQueryKey,
} from "@/api/@tanstack/react-query.gen";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import {
	Dialog,
	DialogContent,
	DialogDescription,
	DialogFooter,
	DialogHeader,
	DialogTitle,
} from "@/components/ui/dialog";
import { Spinner } from "@/components/ui/spinner";
import { useWorkspaceStore } from "@/stores/workspace-store";
import { ConfigureWorkspaceStep } from "./ConfigureWorkspaceStep";
import { ConnectGitLabStep } from "./ConnectGitLabStep";
import { SelectGroupStep } from "./SelectGroupStep";
import { workspaceDetailsSchema } from "./schemas";
import { WizardStepIndicator } from "./WizardStepIndicator";
import { initialWizardState, WizardContext, wizardReducer } from "./wizard-context";

const STEP_META = [
	{
		title: "Connect to GitLab",
		description: "Enter your GitLab instance URL and personal access token.",
	},
	{ title: "Select a Group", description: "Choose the GitLab group to monitor." },
	{ title: "Configure Workspace", description: "Set a name and URL slug for your workspace." },
] as const;

export function CreateWorkspaceDialog({
	open,
	onOpenChange,
}: {
	open: boolean;
	onOpenChange: (open: boolean) => void;
}) {
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
			onOpenChange(false);
			dispatch({ type: "RESET" });
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

	const handleClose = (nextOpen: boolean) => {
		if (!nextOpen) {
			dispatch({ type: "RESET" });
			listGroups.reset();
		}
		onOpenChange(nextOpen);
	};

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

	// Key step components by step number so local state resets on step change
	const stepKey = `step-${state.step}`;

	return (
		<Dialog open={open} onOpenChange={handleClose}>
			<DialogContent className="sm:max-w-lg">
				<DialogHeader>
					<DialogTitle>{meta.title}</DialogTitle>
					<DialogDescription>{meta.description}</DialogDescription>
				</DialogHeader>

				<WizardStepIndicator currentStep={state.step} />

				<WizardContext.Provider value={{ state, dispatch }}>
					{state.step === 1 && <ConnectGitLabStep key={stepKey} />}
					{state.step === 2 && <SelectGroupStep key={stepKey} />}
					{state.step === 3 && <ConfigureWorkspaceStep key={stepKey} />}
				</WizardContext.Provider>

				{listGroups.isError && state.step === 1 && (
					<Alert variant="destructive">
						<OctagonXIcon />
						<AlertTitle>Failed to load groups</AlertTitle>
						<AlertDescription>
							Could not fetch accessible groups. Your token may lack the required scopes.
						</AlertDescription>
					</Alert>
				)}

				<DialogFooter>
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
				</DialogFooter>
			</DialogContent>
		</Dialog>
	);
}

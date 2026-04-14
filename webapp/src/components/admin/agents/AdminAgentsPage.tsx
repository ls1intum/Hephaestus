import { useEffect, useState } from "react";
import type {
	AgentConfig,
	AgentJob,
	CreateAgentConfigRequest,
	PageAgentJob,
	UpdateAgentConfigRequest,
} from "@/api/types.gen";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { AgentConfigForm } from "./AgentConfigForm";
import { AgentConfigList } from "./AgentConfigList";
import { AgentJobDetailsPanel } from "./AgentJobDetailsPanel";
import { AgentJobsTable } from "./AgentJobsTable";
import {
	type AgentConfigDraft,
	agentConfigFieldIds,
	createDraftFromConfig,
	createEmptyDraft,
	type DraftErrors,
	type JobStatusFilter,
	normalizeOptional,
	validateDraft,
} from "./utils";

export interface AdminAgentsPageProps {
	configs: AgentConfig[];
	jobsPage?: PageAgentJob;
	selectedJob?: AgentJob;
	selectedJobId: string | null;
	jobsFilter: {
		status: JobStatusFilter;
		configId: string;
		page: number;
		size: number;
	};
	isLoadingConfigs: boolean;
	isLoadingJobs: boolean;
	isLoadingJobDetails: boolean;
	configsError: Error | null;
	jobsError: Error | null;
	jobDetailsError: Error | null;
	isSavingConfig: boolean;
	deletingConfigId: number | null;
	cancellingJobId: string | null;
	retryingJobId: string | null;
	onRefresh: () => void;
	onCreateConfig: (payload: CreateAgentConfigRequest) => Promise<void>;
	onUpdateConfig: (configId: number, payload: UpdateAgentConfigRequest) => Promise<void>;
	onDeleteConfig: (configId: number) => Promise<void>;
	onChangeJobsFilter: (next: Partial<AdminAgentsPageProps["jobsFilter"]>) => void;
	onSelectJob: (jobId: string | null) => void;
	onCancelJob: (jobId: string) => Promise<void>;
	onRetryDelivery: (jobId: string) => Promise<void>;
}

export function AdminAgentsPage({
	configs,
	jobsPage,
	selectedJob,
	selectedJobId,
	jobsFilter,
	isLoadingConfigs,
	isLoadingJobs,
	isLoadingJobDetails,
	configsError,
	jobsError,
	jobDetailsError,
	isSavingConfig,
	deletingConfigId,
	cancellingJobId,
	retryingJobId,
	onRefresh,
	onCreateConfig,
	onUpdateConfig,
	onDeleteConfig,
	onChangeJobsFilter,
	onSelectJob,
	onCancelJob,
	onRetryDelivery,
}: AdminAgentsPageProps) {
	const [editingConfigId, setEditingConfigId] = useState<number | "new">("new");
	const [draft, setDraft] = useState<AgentConfigDraft>(() => createEmptyDraft());
	const [errors, setErrors] = useState<DraftErrors>({});
	const [deleteCandidate, setDeleteCandidate] = useState<AgentConfig | null>(null);
	const [cancelCandidate, setCancelCandidate] = useState<AgentJob | null>(null);
	const [pendingEditorTarget, setPendingEditorTarget] = useState<
		{ type: "create" } | { type: "edit"; config: AgentConfig } | null
	>(null);
	const [pendingDeleteCandidate, setPendingDeleteCandidate] = useState<AgentConfig | null>(null);
	const [pendingCancelCandidate, setPendingCancelCandidate] = useState<AgentJob | null>(null);

	const editingConfig = configs.find((config) => config.id === editingConfigId) ?? null;
	const existingHasCredential = editingConfig?.hasLlmApiKey ?? false;

	function hasUnsavedChanges() {
		const baseline = editingConfig ? createDraftFromConfig(editingConfig) : createEmptyDraft();
		return JSON.stringify(draft) !== JSON.stringify(baseline);
	}

	useEffect(() => {
		if (editingConfigId !== "new" && !editingConfig) {
			setEditingConfigId("new");
			setDraft(createEmptyDraft());
			setErrors({});
		}
	}, [editingConfig, editingConfigId]);

	function handleStartCreate() {
		if (hasUnsavedChanges()) {
			setPendingEditorTarget({ type: "create" });
			return;
		}
		setEditingConfigId("new");
		setDraft(createEmptyDraft());
		setErrors({});
	}

	function handleStartEdit(config: AgentConfig) {
		if (hasUnsavedChanges()) {
			setPendingEditorTarget({ type: "edit", config });
			return;
		}
		setEditingConfigId(config.id);
		setDraft(createDraftFromConfig(config));
		setErrors({});
	}

	function confirmEditorChange() {
		if (!pendingEditorTarget) {
			return;
		}

		if (pendingEditorTarget.type === "create") {
			setEditingConfigId("new");
			setDraft(createEmptyDraft());
		} else {
			setEditingConfigId(pendingEditorTarget.config.id);
			setDraft(createDraftFromConfig(pendingEditorTarget.config));
		}

		setErrors({});
		setPendingEditorTarget(null);
	}

	function dismissEditorChange() {
		setPendingEditorTarget(null);
	}

	function requestDelete(config: AgentConfig) {
		setPendingDeleteCandidate(config);
	}

	function requestCancelJob(job: AgentJob) {
		setPendingCancelCandidate(job);
	}

	function dismissDeleteCandidate() {
		setPendingDeleteCandidate(null);
	}

	function dismissCancelCandidate() {
		setPendingCancelCandidate(null);
	}

	function confirmDeleteCandidate() {
		if (!pendingDeleteCandidate) {
			return;
		}
		void onDeleteConfig(pendingDeleteCandidate.id).then(() => {
			if (editingConfigId === pendingDeleteCandidate.id) {
				handleStartCreate();
			}
			setDeleteCandidate(null);
		});
		setDeleteCandidate(pendingDeleteCandidate);
		setPendingDeleteCandidate(null);
	}

	function confirmCancelCandidate() {
		if (!pendingCancelCandidate) {
			return;
		}
		void onCancelJob(pendingCancelCandidate.id).then(() => {
			setCancelCandidate(null);
		});
		setCancelCandidate(pendingCancelCandidate);
		setPendingCancelCandidate(null);
	}

	function applyDraft(nextDraft: AgentConfigDraft) {
		setDraft(nextDraft);
		setErrors((previousErrors) => {
			if (Object.keys(previousErrors).length === 0) {
				return previousErrors;
			}

			const validation = validateDraft(nextDraft, { existingHasCredential });
			return validation.success ? {} : validation.errors;
		});
	}

	function focusFirstInvalidField(nextErrors: DraftErrors) {
		const firstInvalidField = Object.keys(nextErrors)[0] as keyof AgentConfigDraft | undefined;
		const fieldId = firstInvalidField ? agentConfigFieldIds[firstInvalidField] : undefined;
		if (!fieldId) {
			return;
		}

		queueMicrotask(() => {
			const element = document.getElementById(fieldId);
			if (element instanceof HTMLElement) {
				element.focus();
			}
		});
	}

	async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
		event.preventDefault();

		const validation = validateDraft(draft, { existingHasCredential });
		if (!validation.success) {
			setErrors(validation.errors);
			focusFirstInvalidField(validation.errors);
			return;
		}

		const payloadBase = {
			name: validation.data.name.trim(),
			enabled: validation.data.enabled,
			agentType: validation.data.agentType,
			modelName: normalizeOptional(validation.data.modelName),
			modelVersion: normalizeOptional(validation.data.modelVersion),
			llmProvider: validation.data.llmProvider,
			timeoutSeconds: Number(validation.data.timeoutSeconds),
			maxConcurrentJobs: Number(validation.data.maxConcurrentJobs),
			allowInternet: validation.data.allowInternet,
			credentialMode: validation.data.credentialMode,
		};

		if (editingConfigId === "new") {
			await onCreateConfig({
				...payloadBase,
				llmApiKey: normalizeOptional(validation.data.llmApiKey),
			});
			handleStartCreate();
			return;
		}

		await onUpdateConfig(editingConfigId, {
			...payloadBase,
			clearModelName:
				editingConfig?.modelName != null &&
				normalizeOptional(validation.data.modelName) === undefined
					? true
					: undefined,
			clearModelVersion:
				editingConfig?.modelVersion != null &&
				normalizeOptional(validation.data.modelVersion) === undefined
					? true
					: undefined,
			llmApiKey: normalizeOptional(validation.data.llmApiKey),
			clearLlmApiKey: validation.data.clearLlmApiKey || undefined,
		});

		if (editingConfig) {
			handleStartEdit({
				...editingConfig,
				...payloadBase,
				hasLlmApiKey:
					validation.data.clearLlmApiKey === true
						? false
						: normalizeOptional(validation.data.llmApiKey) !== undefined || existingHasCredential,
			});
		}
	}

	return (
		<div className="container mx-auto max-w-7xl py-6">
			<header className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
				<div className="max-w-3xl space-y-2">
					<h1 className="text-3xl font-semibold tracking-tight">Review Agents</h1>
					<p className="text-sm text-muted-foreground sm:text-base">
						Configure the Claude Code and Pi runtimes that run practice reviews for this workspace.
					</p>
				</div>
				<Button variant="outline" onClick={onRefresh}>
					Refresh data
				</Button>
			</header>

			<div className="grid gap-6 xl:grid-cols-[minmax(0,1.4fr)_24rem]">
				<div className="space-y-6">
					{configsError && (
						<Card>
							<CardHeader>
								<CardTitle>Could not load agent configs</CardTitle>
								<CardDescription>{configsError.message}</CardDescription>
							</CardHeader>
						</Card>
					)}

					<AgentConfigList
						configs={configs}
						isLoading={isLoadingConfigs}
						editingConfigId={editingConfigId}
						deletingConfigId={deletingConfigId}
						onCreateNew={handleStartCreate}
						onEdit={handleStartEdit}
						onDelete={requestDelete}
					/>

					{pendingDeleteCandidate && (
						<Alert variant="destructive">
							<AlertTitle>Delete {pendingDeleteCandidate.name}?</AlertTitle>
							<AlertDescription>
								This removes the config from the workspace. Existing job history stays intact, but
								active jobs must be cancelled before deletion succeeds.
							</AlertDescription>
							<div className="mt-4 flex flex-wrap gap-2">
								<Button variant="outline" onClick={dismissDeleteCandidate}>
									Keep config
								</Button>
								<Button variant="destructive" onClick={confirmDeleteCandidate}>
									Continue to delete
								</Button>
							</div>
						</Alert>
					)}

					<AgentJobsTable
						configs={configs}
						jobsPage={jobsPage}
						selectedJobId={selectedJobId}
						selectedJob={selectedJob}
						jobsFilter={jobsFilter}
						isLoading={isLoadingJobs}
						error={jobsError}
						cancellingJobId={cancellingJobId}
						retryingJobId={retryingJobId}
						onRefresh={onRefresh}
						onRequestCancelJob={requestCancelJob}
						onChangeJobsFilter={onChangeJobsFilter}
						onSelectJob={(jobId) => onSelectJob(jobId)}
						onRetryDelivery={onRetryDelivery}
					/>

					{pendingCancelCandidate && (
						<Alert>
							<AlertTitle>Cancel this job?</AlertTitle>
							<AlertDescription>
								This stops the selected practice-review run. Use this only when the current
								execution is no longer valid.
							</AlertDescription>
							<div className="mt-4 flex flex-wrap gap-2">
								<Button variant="outline" onClick={dismissCancelCandidate}>
									Keep running
								</Button>
								<Button variant="outline" onClick={confirmCancelCandidate}>
									Continue to cancel
								</Button>
							</div>
						</Alert>
					)}

					{selectedJobId !== null && (
						<AgentJobDetailsPanel
							job={selectedJob}
							isLoading={isLoadingJobDetails}
							error={jobDetailsError}
							cancellingJobId={cancellingJobId}
							retryingJobId={retryingJobId}
							onRequestCancelJob={requestCancelJob}
							onClose={() => onSelectJob(null)}
							onRetryDelivery={onRetryDelivery}
						/>
					)}

					{pendingEditorTarget && (
						<Alert>
							<AlertTitle>Discard unsaved changes?</AlertTitle>
							<AlertDescription>
								Your current edits have not been saved. Continue only if you want to lose those
								changes.
							</AlertDescription>
							<div className="mt-4 flex flex-wrap gap-2">
								<Button variant="outline" onClick={dismissEditorChange}>
									Keep editing
								</Button>
								<Button variant="outline" onClick={confirmEditorChange}>
									Discard changes
								</Button>
							</div>
						</Alert>
					)}
				</div>

				<Card className="xl:sticky xl:top-6 xl:self-start">
					<CardHeader>
						<CardTitle>
							{editingConfigId === "new" ? "New agent config" : "Edit agent config"}
						</CardTitle>
						<CardDescription>
							Configure naming, runtime, credentials, and execution limits for one review runtime.
						</CardDescription>
					</CardHeader>
					<CardContent>
						<AgentConfigForm
							mode={editingConfigId === "new" ? "create" : "edit"}
							draft={draft}
							errors={errors}
							existingHasCredential={existingHasCredential}
							isSaving={isSavingConfig}
							onDraftChange={applyDraft}
							onSubmit={handleSubmit}
							onReset={handleStartCreate}
						/>
					</CardContent>
				</Card>
			</div>

			{deleteCandidate && (
				<div className="sr-only" aria-live="polite">
					Deleting {deleteCandidate.name}
				</div>
			)}

			{cancelCandidate && (
				<div className="sr-only" aria-live="polite">
					Cancelling {cancelCandidate.id}
				</div>
			)}
		</div>
	);
}

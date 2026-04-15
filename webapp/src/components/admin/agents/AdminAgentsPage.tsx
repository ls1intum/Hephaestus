import { useEffect, useState } from "react";
import type {
	AgentConfig,
	AgentJob,
	AgentRunner,
	CreateAgentConfigRequest,
	CreateAgentRunnerRequest,
	PageAgentJob,
	UpdateAgentConfigRequest,
	UpdateAgentRunnerRequest,
} from "@/api/types.gen";
import { AgentSettingsPage } from "./AgentSettingsPage";
import {
	type ConfigDraft,
	createConfigDraftFromConfig,
	createEmptyConfigDraft,
	createEmptyRunnerDraft,
	createRunnerDraftFromRunner,
	type DraftErrors,
	focusFirstInvalidField,
	type JobStatusFilter,
	normalizeOptional,
	type RunnerDraft,
	validateConfigDraft,
	validateRunnerDraft,
} from "./utils";

export interface AdminAgentsPageProps {
	runners: AgentRunner[];
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
	isLoadingRunners: boolean;
	isLoadingConfigs: boolean;
	isLoadingJobs: boolean;
	isLoadingJobDetails: boolean;
	jobsError: Error | null;
	jobDetailsError: Error | null;
	isSavingRunner: boolean;
	isSavingConfig: boolean;
	deletingRunnerId: number | null;
	deletingConfigId: number | null;
	cancellingJobId: string | null;
	retryingJobId: string | null;
	onRefresh: () => void;
	onCreateRunner: (payload: CreateAgentRunnerRequest) => Promise<AgentRunner>;
	onUpdateRunner: (runnerId: number, payload: UpdateAgentRunnerRequest) => Promise<AgentRunner>;
	onDeleteRunner: (runnerId: number) => Promise<void>;
	onCreateConfig: (payload: CreateAgentConfigRequest) => Promise<AgentConfig>;
	onUpdateConfig: (configId: number, payload: UpdateAgentConfigRequest) => Promise<AgentConfig>;
	onDeleteConfig: (configId: number) => Promise<void>;
	onChangeJobsFilter: (next: Partial<AdminAgentsPageProps["jobsFilter"]>) => void;
	onSelectJob: (jobId: string | null) => void;
	onCancelJob: (jobId: string) => Promise<void>;
	onRetryDelivery: (jobId: string) => Promise<void>;
}

type RunnerSelection = number | "new" | null;
type ConfigSelection = number | "new" | null;

export function AdminAgentsPage({
	runners,
	configs,
	jobsPage,
	selectedJob,
	selectedJobId,
	jobsFilter,
	isLoadingRunners,
	isLoadingConfigs,
	isLoadingJobs,
	isLoadingJobDetails,
	jobsError,
	jobDetailsError,
	isSavingRunner,
	isSavingConfig,
	deletingRunnerId,
	deletingConfigId,
	cancellingJobId,
	retryingJobId,
	onRefresh,
	onCreateRunner,
	onUpdateRunner,
	onDeleteRunner,
	onCreateConfig,
	onUpdateConfig,
	onDeleteConfig,
	onChangeJobsFilter,
	onSelectJob,
	onCancelJob,
	onRetryDelivery,
}: AdminAgentsPageProps) {
	const [selectedRunnerId, setSelectedRunnerId] = useState<RunnerSelection>(null);
	const [selectedConfigId, setSelectedConfigId] = useState<ConfigSelection>(null);
	const [runnerDraft, setRunnerDraft] = useState<RunnerDraft>(createEmptyRunnerDraft);
	const [configDraft, setConfigDraft] = useState<ConfigDraft>(() => createEmptyConfigDraft());
	const [errors, setErrors] = useState<DraftErrors>({ runner: {}, config: {} });
	const [pendingDeleteRunner, setPendingDeleteRunner] = useState<AgentRunner | null>(null);
	const [pendingDeleteConfig, setPendingDeleteConfig] = useState<AgentConfig | null>(null);
	const [deleteRunnerCandidate, setDeleteRunnerCandidate] = useState<AgentRunner | null>(null);
	const [deleteConfigCandidate, setDeleteConfigCandidate] = useState<AgentConfig | null>(null);
	const [pendingCancelJob, setPendingCancelJob] = useState<AgentJob | null>(null);
	const [cancelCandidate, setCancelCandidate] = useState<AgentJob | null>(null);

	const activeRunner =
		selectedRunnerId == null || selectedRunnerId === "new"
			? null
			: (runners.find((runner) => runner.id === selectedRunnerId) ?? null);
	const activeConfig =
		selectedConfigId == null || selectedConfigId === "new"
			? null
			: (configs.find((config) => config.id === selectedConfigId) ?? null);
	const runnerHasCredential = activeRunner?.hasLlmApiKey ?? false;

	useEffect(() => {
		if (selectedRunnerId !== null || isLoadingRunners) {
			return;
		}

		if (runners.length === 0) {
			setSelectedRunnerId("new");
			setRunnerDraft(createEmptyRunnerDraft());
			return;
		}

		const [firstRunner] = runners;
		setSelectedRunnerId(firstRunner.id);
		setRunnerDraft(createRunnerDraftFromRunner(firstRunner));
	}, [isLoadingRunners, runners, selectedRunnerId]);

	useEffect(() => {
		if (selectedConfigId !== null || isLoadingConfigs) {
			return;
		}

		if (configs.length === 0) {
			setSelectedConfigId("new");
			setConfigDraft(createEmptyConfigDraft(runners[0]?.id));
			return;
		}

		const [firstConfig] = configs;
		setSelectedConfigId(firstConfig.id);
		setConfigDraft(createConfigDraftFromConfig(firstConfig));
	}, [configs, isLoadingConfigs, runners, selectedConfigId]);

	useEffect(() => {
		if (selectedRunnerId !== null && selectedRunnerId !== "new" && !activeRunner) {
			if (runners.length === 0) {
				setSelectedRunnerId("new");
				setRunnerDraft(createEmptyRunnerDraft());
				return;
			}

			const [firstRunner] = runners;
			setSelectedRunnerId(firstRunner.id);
			setRunnerDraft(createRunnerDraftFromRunner(firstRunner));
		}
	}, [activeRunner, runners, selectedRunnerId]);

	useEffect(() => {
		if (selectedConfigId !== null && selectedConfigId !== "new" && !activeConfig) {
			if (configs.length === 0) {
				setSelectedConfigId("new");
				setConfigDraft(createEmptyConfigDraft(runners[0]?.id));
				return;
			}

			const [firstConfig] = configs;
			setSelectedConfigId(firstConfig.id);
			setConfigDraft(createConfigDraftFromConfig(firstConfig));
		}
	}, [activeConfig, configs, runners, selectedConfigId]);

	useEffect(() => {
		if (!configDraft.runnerId && runners.length > 0) {
			setConfigDraft((current) => ({
				...current,
				runnerId: String(runners[0].id),
			}));
		}
	}, [configDraft.runnerId, runners]);

	function updateErrors(nextErrors: Partial<DraftErrors>) {
		setErrors((current) => ({
			runner: nextErrors.runner ?? current.runner,
			config: nextErrors.config ?? current.config,
		}));
	}

	function handleRunnerDraftChange(nextDraft: RunnerDraft) {
		setRunnerDraft(nextDraft);
		if (Object.keys(errors.runner).length > 0) {
			const validation = validateRunnerDraft(nextDraft, {
				existingHasCredential: runnerHasCredential,
			});
			updateErrors({ runner: validation.success ? {} : validation.errors });
		}
	}

	function handleConfigDraftChange(nextDraft: ConfigDraft) {
		setConfigDraft(nextDraft);
		if (Object.keys(errors.config).length > 0) {
			const validation = validateConfigDraft(nextDraft);
			updateErrors({ config: validation.success ? {} : validation.errors });
		}
	}

	function activateNewRunner() {
		setSelectedRunnerId("new");
		setRunnerDraft(createEmptyRunnerDraft());
		updateErrors({ runner: {} });
	}

	function activateRunner(runner: AgentRunner) {
		setSelectedRunnerId(runner.id);
		setRunnerDraft(createRunnerDraftFromRunner(runner));
		updateErrors({ runner: {} });
	}

	function activateNewConfig() {
		setSelectedConfigId("new");
		setConfigDraft(createEmptyConfigDraft(activeRunner?.id ?? runners[0]?.id));
		updateErrors({ config: {} });
	}

	function activateConfig(config: AgentConfig) {
		setSelectedConfigId(config.id);
		setConfigDraft(createConfigDraftFromConfig(config));
		updateErrors({ config: {} });
	}

	async function handleRunnerSubmit() {
		const validation = validateRunnerDraft(runnerDraft, {
			existingHasCredential: runnerHasCredential,
		});
		if (!validation.success) {
			updateErrors({ runner: validation.errors });
			focusFirstInvalidField({ runner: validation.errors, config: errors.config });
			return;
		}

		const payloadBase = {
			name: validation.data.name.trim(),
			agentType: validation.data.agentType,
			modelName: normalizeOptional(validation.data.modelName),
			modelVersion: normalizeOptional(validation.data.modelVersion),
			llmProvider: validation.data.llmProvider,
			timeoutSeconds: Number(validation.data.timeoutSeconds),
			maxConcurrentJobs: Number(validation.data.maxConcurrentJobs),
			allowInternet: validation.data.allowInternet,
			credentialMode: validation.data.credentialMode,
		};

		if (selectedRunnerId === "new" || selectedRunnerId === null) {
			const createdRunner = await onCreateRunner({
				...payloadBase,
				llmApiKey: normalizeOptional(validation.data.llmApiKey),
			});
			activateRunner(createdRunner);
			return;
		}

		const updatedRunner = await onUpdateRunner(selectedRunnerId, {
			...payloadBase,
			clearModelName:
				activeRunner?.modelName != null &&
				normalizeOptional(validation.data.modelName) === undefined
					? true
					: undefined,
			clearModelVersion:
				activeRunner?.modelVersion != null &&
				normalizeOptional(validation.data.modelVersion) === undefined
					? true
					: undefined,
			llmApiKey: normalizeOptional(validation.data.llmApiKey),
			clearLlmApiKey: validation.data.clearLlmApiKey || undefined,
		});

		activateRunner(updatedRunner);
	}

	async function handleConfigSubmit() {
		const validation = validateConfigDraft(configDraft);
		if (!validation.success) {
			updateErrors({ config: validation.errors });
			focusFirstInvalidField({ runner: errors.runner, config: validation.errors });
			return;
		}

		const payloadBase = {
			name: validation.data.name.trim(),
			enabled: validation.data.enabled,
			runnerId: Number(validation.data.runnerId),
		};

		if (selectedConfigId === "new" || selectedConfigId === null) {
			const createdConfig = await onCreateConfig(payloadBase);
			activateConfig(createdConfig);
			return;
		}

		const updatedConfig = await onUpdateConfig(selectedConfigId, payloadBase);
		activateConfig(updatedConfig);
	}

	function requestDeleteRunner(runner: AgentRunner) {
		setPendingDeleteRunner(runner);
	}

	function confirmDeleteRunner() {
		if (!pendingDeleteRunner) {
			return;
		}

		const runnerToDelete = pendingDeleteRunner;
		setDeleteRunnerCandidate(runnerToDelete);
		setPendingDeleteRunner(null);
		void (async () => {
			try {
				await onDeleteRunner(runnerToDelete.id);
				if (selectedRunnerId === runnerToDelete.id) {
					activateNewRunner();
				}
			} finally {
				setDeleteRunnerCandidate(null);
			}
		})();
	}

	function requestDeleteConfig(config: AgentConfig) {
		setPendingDeleteConfig(config);
	}

	function confirmDeleteConfig() {
		if (!pendingDeleteConfig) {
			return;
		}

		const configToDelete = pendingDeleteConfig;
		setDeleteConfigCandidate(configToDelete);
		setPendingDeleteConfig(null);
		void (async () => {
			try {
				await onDeleteConfig(configToDelete.id);
				if (selectedConfigId === configToDelete.id) {
					activateNewConfig();
				}
			} finally {
				setDeleteConfigCandidate(null);
			}
		})();
	}

	function requestCancelJob(job: AgentJob) {
		setPendingCancelJob(job);
	}

	function confirmCancelJob() {
		if (!pendingCancelJob) {
			return;
		}

		const jobToCancel = pendingCancelJob;
		setCancelCandidate(jobToCancel);
		setPendingCancelJob(null);
		void (async () => {
			try {
				await onCancelJob(jobToCancel.id);
			} finally {
				setCancelCandidate(null);
			}
		})();
	}

	return (
		<AgentSettingsPage
			runners={runners}
			configs={configs}
			selectedRunnerId={selectedRunnerId}
			selectedConfigId={selectedConfigId}
			runnerDraft={runnerDraft}
			configDraft={configDraft}
			errors={errors}
			existingHasCredential={runnerHasCredential}
			isLoadingRunners={isLoadingRunners}
			isLoadingConfigs={isLoadingConfigs}
			isSavingRunner={isSavingRunner}
			isSavingConfig={isSavingConfig}
			deletingRunnerId={deletingRunnerId ?? deleteRunnerCandidate?.id ?? null}
			deletingConfigId={deletingConfigId ?? deleteConfigCandidate?.id ?? null}
			pendingDeleteRunner={pendingDeleteRunner}
			pendingDeleteConfig={pendingDeleteConfig}
			jobsPage={jobsPage}
			selectedJob={selectedJob}
			selectedJobId={selectedJobId}
			jobsFilter={jobsFilter}
			isLoadingJobs={isLoadingJobs}
			isLoadingJobDetails={isLoadingJobDetails}
			jobsError={jobsError}
			jobDetailsError={jobDetailsError}
			pendingCancelJob={pendingCancelJob}
			cancellingJobId={cancellingJobId ?? cancelCandidate?.id ?? null}
			retryingJobId={retryingJobId}
			onSelectRunner={(runnerId: number | "new") => {
				if (runnerId === "new") {
					activateNewRunner();
					return;
				}

				const nextRunner = runners.find((runner) => runner.id === runnerId);
				if (nextRunner) {
					activateRunner(nextRunner);
				}
			}}
			onSelectConfig={(configId) => {
				if (configId === "new") {
					activateNewConfig();
					return;
				}

				const nextConfig = configs.find((config) => config.id === configId);
				if (nextConfig) {
					activateConfig(nextConfig);
				}
			}}
			onRunnerDraftChange={handleRunnerDraftChange}
			onConfigDraftChange={handleConfigDraftChange}
			onRunnerSubmit={handleRunnerSubmit}
			onConfigSubmit={handleConfigSubmit}
			onRunnerReset={activateNewRunner}
			onConfigReset={activateNewConfig}
			onRequestDeleteRunner={requestDeleteRunner}
			onCancelDeleteRunner={() => setPendingDeleteRunner(null)}
			onConfirmDeleteRunner={confirmDeleteRunner}
			onRequestDeleteConfig={requestDeleteConfig}
			onCancelDeleteConfig={() => setPendingDeleteConfig(null)}
			onConfirmDeleteConfig={confirmDeleteConfig}
			onRefresh={onRefresh}
			onChangeJobsFilter={onChangeJobsFilter}
			onSelectJob={onSelectJob}
			onRequestCancelJob={requestCancelJob}
			onCancelPendingJob={() => setPendingCancelJob(null)}
			onConfirmCancelJob={confirmCancelJob}
			onRetryDelivery={onRetryDelivery}
		/>
	);
}

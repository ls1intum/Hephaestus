import type { AgentConfig, AgentJob, AgentRunner, PageAgentJob } from "@/api/types.gen";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { AgentConfigForm } from "./AgentConfigForm";
import { AgentConfigOverview } from "./AgentConfigOverview";
import { AgentJobDetailsPanel } from "./AgentJobDetailsPanel";
import { AgentJobsTable } from "./AgentJobsTable";
import type { ConfigDraft, DraftErrors, JobStatusFilter, RunnerDraft } from "./utils";

export interface AgentSettingsPageProps {
	runners: AgentRunner[];
	configs: AgentConfig[];
	selectedRunnerId: number | "new" | null;
	selectedConfigId: number | "new" | null;
	runnerDraft: RunnerDraft;
	configDraft: ConfigDraft;
	errors: DraftErrors;
	existingHasCredential: boolean;
	isLoadingRunners: boolean;
	isLoadingConfigs: boolean;
	isSavingRunner: boolean;
	isSavingConfig: boolean;
	deletingRunnerId: number | null;
	deletingConfigId: number | null;
	pendingDeleteRunner: AgentRunner | null;
	pendingDeleteConfig: AgentConfig | null;
	jobsPage?: PageAgentJob;
	selectedJob?: AgentJob;
	selectedJobId: string | null;
	jobsFilter: {
		status: JobStatusFilter;
		configId: string;
		page: number;
		size: number;
	};
	isLoadingJobs: boolean;
	isLoadingJobDetails: boolean;
	jobsError: Error | null;
	jobDetailsError: Error | null;
	pendingCancelJob: AgentJob | null;
	cancellingJobId: string | null;
	retryingJobId: string | null;
	onSelectRunner: (runnerId: number | "new") => void;
	onSelectConfig: (configId: number | "new") => void;
	onRunnerDraftChange: (nextDraft: RunnerDraft) => void;
	onConfigDraftChange: (nextDraft: ConfigDraft) => void;
	onRunnerSubmit: () => Promise<void>;
	onConfigSubmit: () => Promise<void>;
	onRunnerReset: () => void;
	onConfigReset: () => void;
	onRequestDeleteRunner: (runner: AgentRunner) => void;
	onCancelDeleteRunner: () => void;
	onConfirmDeleteRunner: () => void;
	onRequestDeleteConfig: (config: AgentConfig) => void;
	onCancelDeleteConfig: () => void;
	onConfirmDeleteConfig: () => void;
	onRefresh: () => void;
	onChangeJobsFilter: (next: Partial<AgentSettingsPageProps["jobsFilter"]>) => void;
	onSelectJob: (jobId: string | null) => void;
	onRequestCancelJob: (job: AgentJob) => void;
	onCancelPendingJob: () => void;
	onConfirmCancelJob: () => void;
	onRetryDelivery: (jobId: string) => Promise<void>;
}

export function AgentSettingsPage(props: AgentSettingsPageProps) {
	const {
		runners,
		configs,
		selectedRunnerId,
		selectedConfigId,
		runnerDraft,
		configDraft,
		errors,
		existingHasCredential,
		isLoadingRunners,
		isLoadingConfigs,
		isSavingRunner,
		isSavingConfig,
		deletingRunnerId,
		deletingConfigId,
		pendingDeleteRunner,
		pendingDeleteConfig,
		jobsPage,
		selectedJob,
		selectedJobId,
		jobsFilter,
		isLoadingJobs,
		isLoadingJobDetails,
		jobsError,
		jobDetailsError,
		pendingCancelJob,
		cancellingJobId,
		retryingJobId,
		onSelectRunner,
		onSelectConfig,
		onRunnerDraftChange,
		onConfigDraftChange,
		onRunnerSubmit,
		onConfigSubmit,
		onRunnerReset,
		onConfigReset,
		onRequestDeleteRunner,
		onCancelDeleteRunner,
		onConfirmDeleteRunner,
		onRequestDeleteConfig,
		onCancelDeleteConfig,
		onConfirmDeleteConfig,
		onRefresh,
		onChangeJobsFilter,
		onSelectJob,
		onRequestCancelJob,
		onCancelPendingJob,
		onConfirmCancelJob,
		onRetryDelivery,
	} = props;

	return (
		<div className="container mx-auto max-w-7xl py-6">
			<header className="mb-6 space-y-2">
				<h1 className="text-3xl font-semibold tracking-tight">Review agent management</h1>
				<p className="max-w-3xl text-sm text-muted-foreground sm:text-base">
					Define reusable runners for execution, bind agent configs to those runners, and inspect
					the jobs created in this workspace.
				</p>
			</header>

			<div className="grid gap-6 xl:grid-cols-[minmax(0,1.1fr)_minmax(0,0.9fr)]">
				<div className="space-y-6">
					<AgentConfigOverview
						runners={runners}
						configs={configs}
						selectedRunnerId={selectedRunnerId}
						selectedConfigId={selectedConfigId}
						isLoadingRunners={isLoadingRunners}
						isLoadingConfigs={isLoadingConfigs}
						deletingRunnerId={deletingRunnerId}
						deletingConfigId={deletingConfigId}
						onCreateRunner={() => onSelectRunner("new")}
						onSelectRunner={onSelectRunner}
						onDeleteRunner={onRequestDeleteRunner}
						onCreateConfig={() => onSelectConfig("new")}
						onSelectConfig={onSelectConfig}
						onDeleteConfig={onRequestDeleteConfig}
					/>

					{pendingDeleteRunner && (
						<InlineDecisionCard
							title={`Delete runner ${pendingDeleteRunner.name}?`}
							description="This removes the execution runtime from the workspace. Delete or reassign linked agent configs first."
							confirmLabel="Delete runner"
							cancelLabel="Keep runner"
							onConfirm={onConfirmDeleteRunner}
							onCancel={onCancelDeleteRunner}
							variant="destructive"
						/>
					)}

					{pendingDeleteConfig && (
						<InlineDecisionCard
							title={`Delete config ${pendingDeleteConfig.name}?`}
							description="This removes the logical review-agent binding from the workspace. Existing job history stays intact."
							confirmLabel="Delete config"
							cancelLabel="Keep config"
							onConfirm={onConfirmDeleteConfig}
							onCancel={onCancelDeleteConfig}
							variant="destructive"
						/>
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
						onRequestCancelJob={onRequestCancelJob}
						onChangeJobsFilter={onChangeJobsFilter}
						onSelectJob={(jobId) => onSelectJob(jobId)}
						onRetryDelivery={onRetryDelivery}
					/>

					{pendingCancelJob && (
						<InlineDecisionCard
							title="Cancel this job?"
							description="This stops the selected review run. Use this only when the current execution is no longer valid."
							confirmLabel="Cancel job"
							cancelLabel="Keep running"
							onConfirm={onConfirmCancelJob}
							onCancel={onCancelPendingJob}
						/>
					)}

					{selectedJobId !== null && (
						<AgentJobDetailsPanel
							job={selectedJob}
							isLoading={isLoadingJobDetails}
							error={jobDetailsError}
							cancellingJobId={cancellingJobId}
							retryingJobId={retryingJobId}
							onRequestCancelJob={onRequestCancelJob}
							onClose={() => onSelectJob(null)}
							onRetryDelivery={onRetryDelivery}
						/>
					)}
				</div>

				<div className="space-y-6 xl:sticky xl:top-6 xl:self-start">
					<Card>
						<CardHeader>
							<CardTitle>
								{selectedRunnerId === "new" || selectedRunnerId === null
									? "New runner"
									: "Runner settings"}
							</CardTitle>
							<CardDescription>
								Runners define the executable environment: runtime, provider, credentials, network,
								timeout, and concurrency.
							</CardDescription>
						</CardHeader>
						<CardContent>
							<AgentConfigForm
								mode={selectedRunnerId === "new" || selectedRunnerId === null ? "create" : "edit"}
								draft={runnerDraft}
								configDraft={null}
								errors={errors.runner}
								existingHasCredential={existingHasCredential}
								isSaving={isSavingRunner}
								title="Runner"
								submitLabel={
									selectedRunnerId === "new" || selectedRunnerId === null
										? "Save runner"
										: "Update runner"
								}
								onRunnerDraftChange={onRunnerDraftChange}
								onConfigDraftChange={() => {}}
								onSubmit={onRunnerSubmit}
								onReset={onRunnerReset}
							/>
						</CardContent>
					</Card>

					<Card>
						<CardHeader>
							<CardTitle>
								{selectedConfigId === "new" || selectedConfigId === null
									? "New agent config"
									: "Agent config settings"}
							</CardTitle>
							<CardDescription>
								Agent configs are the logical review-agent entries. Each config selects one runner
								and controls whether it participates in new jobs.
							</CardDescription>
						</CardHeader>
						<CardContent>
							{runners.length === 0 ? (
								<Alert>
									<AlertTitle>Create a runner first</AlertTitle>
									<AlertDescription>
										Agent configs only bind logical review agents to an existing runner.
									</AlertDescription>
								</Alert>
							) : (
								<AgentConfigForm
									mode={selectedConfigId === "new" || selectedConfigId === null ? "create" : "edit"}
									draft={null}
									configDraft={configDraft}
									errors={errors.config}
									existingHasCredential={false}
									isSaving={isSavingConfig}
									title="Agent config"
									submitLabel={
										selectedConfigId === "new" || selectedConfigId === null
											? "Save config"
											: "Update config"
									}
									runners={runners}
									onRunnerDraftChange={() => {}}
									onConfigDraftChange={onConfigDraftChange}
									onSubmit={onConfigSubmit}
									onReset={onConfigReset}
								/>
							)}
						</CardContent>
					</Card>
				</div>
			</div>
		</div>
	);
}

interface InlineDecisionCardProps {
	title: string;
	description: string;
	confirmLabel: string;
	cancelLabel: string;
	onConfirm: () => void;
	onCancel: () => void;
	variant?: "default" | "destructive";
}

function InlineDecisionCard({
	title,
	description,
	confirmLabel,
	cancelLabel,
	onConfirm,
	onCancel,
	variant = "default",
}: InlineDecisionCardProps) {
	return (
		<Alert variant={variant === "destructive" ? "destructive" : "default"}>
			<AlertTitle>{title}</AlertTitle>
			<AlertDescription>{description}</AlertDescription>
			<div className="mt-4 flex flex-wrap gap-2">
				<Button variant="outline" onClick={onCancel} type="button">
					{cancelLabel}
				</Button>
				<Button
					variant={variant === "destructive" ? "destructive" : "default"}
					onClick={onConfirm}
					type="button"
				>
					{confirmLabel}
				</Button>
			</div>
		</Alert>
	);
}

import {
	AlertCircle,
	Bot,
	Clock3,
	Eye,
	Globe,
	KeyRound,
	Plus,
	RefreshCw,
	Trash2,
} from "lucide-react";
import { useEffect, useState } from "react";
import { z } from "zod";
import type {
	AgentConfig,
	AgentJob,
	CreateAgentConfigRequest,
	PageAgentJob,
	UpdateAgentConfigRequest,
} from "@/api/types.gen";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import {
	AlertDialog,
	AlertDialogAction,
	AlertDialogCancel,
	AlertDialogContent,
	AlertDialogDescription,
	AlertDialogFooter,
	AlertDialogHeader,
	AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
	Card,
	CardAction,
	CardContent,
	CardDescription,
	CardFooter,
	CardHeader,
	CardTitle,
} from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import {
	Field,
	FieldDescription,
	FieldError,
	FieldGroup,
	FieldLabel,
	FieldLegend,
	FieldSet,
	FieldTitle,
} from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from "@/components/ui/select";
import { Separator } from "@/components/ui/separator";
import {
	Sheet,
	SheetContent,
	SheetDescription,
	SheetFooter,
	SheetHeader,
	SheetTitle,
} from "@/components/ui/sheet";
import { Spinner } from "@/components/ui/spinner";
import {
	Table,
	TableBody,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
} from "@/components/ui/table";
import { Textarea } from "@/components/ui/textarea";

const agentTypeOptions = ["CLAUDE_CODE", "PI"] as const;
const providerOptions = ["ANTHROPIC", "OPENAI", "AZURE_OPENAI"] as const;
const credentialModeOptions = ["PROXY", "API_KEY", "OAUTH"] as const;
const statusFilterOptions = [
	"ALL",
	"QUEUED",
	"RUNNING",
	"COMPLETED",
	"FAILED",
	"TIMED_OUT",
	"CANCELLED",
] as const;

const draftSchema = z.object({
	name: z
		.string()
		.trim()
		.min(1, "Name is required")
		.max(100, "Name must not exceed 100 characters"),
	agentType: z.enum(agentTypeOptions),
	enabled: z.boolean(),
	modelName: z.string().max(128, "Model name must not exceed 128 characters"),
	modelVersion: z.string().max(50, "Model version must not exceed 50 characters"),
	llmProvider: z.enum(providerOptions),
	timeoutSeconds: z
		.string()
		.trim()
		.min(1, "Timeout is required")
		.refine((value) => Number.isInteger(Number(value)), "Timeout must be a whole number")
		.refine((value) => Number(value) >= 30, "Timeout must be at least 30 seconds")
		.refine((value) => Number(value) <= 3600, "Timeout must not exceed 3600 seconds"),
	maxConcurrentJobs: z
		.string()
		.trim()
		.min(1, "Concurrency limit is required")
		.refine((value) => Number.isInteger(Number(value)), "Concurrency limit must be a whole number")
		.refine((value) => Number(value) >= 1, "Concurrency limit must be at least 1")
		.refine((value) => Number(value) <= 10, "Concurrency limit must not exceed 10"),
	allowInternet: z.boolean(),
	credentialMode: z.enum(credentialModeOptions),
	llmApiKey: z.string(),
	clearLlmApiKey: z.boolean(),
});

type AgentTypeValue = (typeof agentTypeOptions)[number];
type ProviderValue = (typeof providerOptions)[number];
type CredentialModeValue = (typeof credentialModeOptions)[number];
type JobStatusFilter = (typeof statusFilterOptions)[number];

export interface AdminAgentsPageProps {
	workspaceSlug: string;
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

type AgentConfigDraft = {
	name: string;
	agentType: AgentTypeValue;
	enabled: boolean;
	modelName: string;
	modelVersion: string;
	llmProvider: ProviderValue;
	timeoutSeconds: string;
	maxConcurrentJobs: string;
	allowInternet: boolean;
	credentialMode: CredentialModeValue;
	llmApiKey: string;
	clearLlmApiKey: boolean;
};

type DraftErrors = Partial<Record<keyof AgentConfigDraft, string>>;

const fieldIds: Partial<Record<keyof AgentConfigDraft, string>> = {
	allowInternet: "agent-allow-internet",
	credentialMode: "agent-credential-mode",
	llmProvider: "agent-provider",
	maxConcurrentJobs: "agent-concurrency",
	modelName: "agent-model-name",
	modelVersion: "agent-model-version",
	name: "agent-config-name",
	timeoutSeconds: "agent-timeout",
	llmApiKey: "agent-secret",
};

function checkboxId(prefix: string, suffix: string | number): string {
	return `${prefix}-${suffix}`;
}

const numberFormatter = new Intl.NumberFormat();
const costFormatter = new Intl.NumberFormat(undefined, {
	style: "currency",
	currency: "USD",
	minimumFractionDigits: 2,
	maximumFractionDigits: 4,
});

function createEmptyDraft(): AgentConfigDraft {
	return {
		name: "",
		agentType: "CLAUDE_CODE",
		enabled: true,
		modelName: "",
		modelVersion: "",
		llmProvider: "ANTHROPIC",
		timeoutSeconds: "600",
		maxConcurrentJobs: "3",
		allowInternet: false,
		credentialMode: "PROXY",
		llmApiKey: "",
		clearLlmApiKey: false,
	};
}

function createDraftFromConfig(config: AgentConfig): AgentConfigDraft {
	return {
		name: config.name,
		agentType: config.agentType,
		enabled: config.enabled,
		modelName: config.modelName ?? "",
		modelVersion: config.modelVersion ?? "",
		llmProvider: config.llmProvider,
		timeoutSeconds: String(config.timeoutSeconds),
		maxConcurrentJobs: String(config.maxConcurrentJobs),
		allowInternet: config.allowInternet,
		credentialMode: config.credentialMode,
		llmApiKey: "",
		clearLlmApiKey: false,
	};
}

function normalizeOptional(value: string): string | undefined {
	const trimmed = value.trim();
	return trimmed.length > 0 ? trimmed : undefined;
}

function validateDraft(
	draft: AgentConfigDraft,
	options: { existingHasCredential: boolean },
): { success: true; data: z.infer<typeof draftSchema> } | { success: false; errors: DraftErrors } {
	const result = draftSchema.safeParse(draft);
	const extraErrors: DraftErrors = {};

	if (draft.agentType === "CLAUDE_CODE" && draft.llmProvider !== "ANTHROPIC") {
		extraErrors.llmProvider = "Claude Code requires Anthropic.";
	}

	if (draft.credentialMode !== "PROXY" && !draft.allowInternet) {
		extraErrors.allowInternet = "Direct credential modes require internet access.";
	}

	const hasCredential =
		normalizeOptional(draft.llmApiKey) !== undefined ||
		(options.existingHasCredential && !draft.clearLlmApiKey);
	if (draft.credentialMode !== "PROXY" && !hasCredential) {
		extraErrors.llmApiKey = "Direct credential modes require a credential or API key.";
	}

	if (result.success && Object.keys(extraErrors).length === 0) {
		return { success: true, data: result.data };
	}

	const zodErrors: DraftErrors = {};
	if (!result.success) {
		for (const issue of result.error.issues) {
			const field = issue.path[0] as keyof AgentConfigDraft;
			if (!zodErrors[field]) {
				zodErrors[field] = issue.message;
			}
		}
	}

	return {
		success: false,
		errors: {
			...zodErrors,
			...extraErrors,
		},
	};
}

function formatAgentType(agentType: AgentTypeValue | string | undefined): string {
	if (agentType === "CLAUDE_CODE") return "Claude Code";
	if (agentType === "PI") return "Pi";
	return agentType ?? "Unknown";
}

function formatProvider(provider: ProviderValue | string | undefined): string {
	if (provider === "AZURE_OPENAI") return "Azure OpenAI";
	if (provider === "OPENAI") return "OpenAI";
	if (provider === "ANTHROPIC") return "Anthropic";
	return provider ?? "Unknown";
}

function formatCredentialMode(mode: CredentialModeValue | string | undefined): string {
	if (mode === "API_KEY") return "API key";
	if (mode === "OAUTH") return "OAuth";
	if (mode === "PROXY") return "Proxy";
	return mode ?? "Unknown";
}

function formatJobStatus(status: AgentJob["status"] | string): string {
	return status
		.toLowerCase()
		.split("_")
		.map((part) => part.charAt(0).toUpperCase() + part.slice(1))
		.join(" ");
}

function formatDateTime(value: Date | string | undefined): string {
	if (!value) return "-";
	const date = value instanceof Date ? value : new Date(value);
	if (Number.isNaN(date.getTime())) return "-";
	return date.toLocaleString(undefined, {
		dateStyle: "medium",
		timeStyle: "short",
	});
}

function formatNumber(value: number | undefined): string {
	return value == null ? "-" : numberFormatter.format(value);
}

function formatCost(value: number | undefined): string {
	return value == null ? "-" : costFormatter.format(value);
}

function formatJson(value: unknown): string {
	if (value == null) return "-";
	try {
		return JSON.stringify(value, null, 2);
	} catch {
		return String(value);
	}
}

function statusBadgeVariant(
	status: AgentJob["status"] | string,
): "default" | "secondary" | "destructive" | "outline" {
	switch (status) {
		case "COMPLETED":
			return "default";
		case "RUNNING":
		case "QUEUED":
			return "secondary";
		case "FAILED":
		case "TIMED_OUT":
			return "destructive";
		default:
			return "outline";
	}
}

function deliveryBadgeVariant(
	status: AgentJob["deliveryStatus"],
): "default" | "secondary" | "destructive" | "outline" {
	switch (status) {
		case "DELIVERED":
			return "default";
		case "PENDING":
			return "secondary";
		case "FAILED":
			return "destructive";
		default:
			return "outline";
	}
}

function canCancelJob(job: AgentJob): boolean {
	return job.status === "QUEUED" || job.status === "RUNNING";
}

function canRetryDelivery(job: AgentJob): boolean {
	return job.status === "COMPLETED" && job.deliveryStatus === "FAILED";
}

function pageSummary(page: PageAgentJob | undefined): string {
	if (!page) return "No jobs yet";
	if ((page.totalElements ?? 0) === 0) return "No jobs yet";
	const currentPage = (page.number ?? 0) + 1;
	const totalPages = page.totalPages ?? 1;
	return `Page ${currentPage} of ${totalPages}`;
}

function focusFirstInvalidField(errors: DraftErrors) {
	const firstInvalidField = Object.keys(errors)[0] as keyof AgentConfigDraft | undefined;
	const fieldId = firstInvalidField ? fieldIds[firstInvalidField] : undefined;
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

export function AdminAgentsPage(props: AdminAgentsPageProps) {
	const {
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
	} = props;

	const [editingConfigId, setEditingConfigId] = useState<number | "new">("new");
	const [draft, setDraft] = useState<AgentConfigDraft>(() => createEmptyDraft());
	const [errors, setErrors] = useState<DraftErrors>({});
	const [deleteCandidate, setDeleteCandidate] = useState<AgentConfig | null>(null);

	const editingConfig = configs.find((config) => config.id === editingConfigId) ?? null;

	const currentJobs = jobsPage?.content ?? [];
	const totalConfigs = configs.length;

	useEffect(() => {
		if (editingConfigId !== "new" && !editingConfig) {
			setEditingConfigId("new");
			setDraft(createEmptyDraft());
			setErrors({});
		}
	}, [editingConfig, editingConfigId]);

	const existingHasCredential = editingConfig?.hasLlmApiKey ?? false;

	const applyDraft = (nextDraft: AgentConfigDraft) => {
		setDraft(nextDraft);
		setErrors((prev) => {
			if (Object.keys(prev).length === 0) {
				return prev;
			}
			const validation = validateDraft(nextDraft, { existingHasCredential });
			return validation.success ? {} : validation.errors;
		});
	};

	const handleStartCreate = () => {
		setEditingConfigId("new");
		setDraft(createEmptyDraft());
		setErrors({});
	};

	const handleStartEdit = (config: AgentConfig) => {
		setEditingConfigId(config.id);
		setDraft(createDraftFromConfig(config));
		setErrors({});
	};

	const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
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
		} as UpdateAgentConfigRequest & {
			clearModelName?: boolean;
			clearModelVersion?: boolean;
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
	};

	const handleDelete = async () => {
		if (!deleteCandidate) return;
		await onDeleteConfig(deleteCandidate.id);
		if (editingConfigId === deleteCandidate.id) {
			handleStartCreate();
		}
		setDeleteCandidate(null);
	};

	const selectedConfigName =
		jobsFilter.configId.length > 0
			? (configs.find((config) => config.id === Number(jobsFilter.configId))?.name ??
				"Selected config")
			: "All configs";

	return (
		<div className="container mx-auto max-w-7xl py-6">
			<header className="mb-6 max-w-3xl space-y-2">
				<h1 className="text-3xl font-semibold tracking-tight">Review Agents</h1>
				<p className="text-sm text-muted-foreground sm:text-base">
					Set up the Claude Code and Pi runtimes that run practice reviews for this workspace.
				</p>
			</header>
			<div className="grid gap-6 xl:grid-cols-[minmax(0,1.45fr)_24rem]">
				<div className="space-y-6">
					<section aria-labelledby="review-agents-overview">
						<h2 id="review-agents-overview" className="sr-only">
							Review agent overview
						</h2>
						<Card className="border-primary/20 bg-gradient-to-br from-primary/10 via-background to-background shadow-sm">
							<CardHeader>
								<CardTitle className="flex items-center gap-2 text-2xl">
									<Bot className="size-5" />
									Runtime Overview
								</CardTitle>
								<CardDescription className="max-w-2xl">
									Configure the Claude Code and Pi runtimes that fan out practice-review jobs for
									this workspace.
								</CardDescription>
								<CardAction>
									<Button variant="outline" onClick={onRefresh}>
										<RefreshCw className="mr-2 size-4" />
										Refresh
									</Button>
								</CardAction>
							</CardHeader>
							<CardContent className="grid gap-3 sm:grid-cols-3">
								<StatCard
									label="Configured runtimes"
									value={String(totalConfigs)}
									helper="Claude Code and Pi only"
								/>
								<StatCard
									label="Enabled configs"
									value={String(configs.filter((config) => config.enabled).length)}
									helper="Used for new submissions"
								/>
								<StatCard
									label="Recent jobs"
									value={String(jobsPage?.totalElements ?? 0)}
									helper={pageSummary(jobsPage)}
								/>
							</CardContent>
						</Card>
					</section>

					{configsError && (
						<Alert variant="destructive">
							<AlertCircle className="size-4" />
							<AlertTitle>Could not load agent configs</AlertTitle>
							<AlertDescription>{configsError.message}</AlertDescription>
						</Alert>
					)}

					<section aria-labelledby="workspace-runtimes-heading">
						<h2 id="workspace-runtimes-heading" className="sr-only">
							Workspace runtimes
						</h2>
						<Card>
							<CardHeader>
								<CardTitle>Workspace Configurations</CardTitle>
								<CardDescription>
									Each enabled config receives its own practice-review job when the workspace
									triggers an evaluation.
								</CardDescription>
								<CardAction>
									<Button onClick={handleStartCreate}>
										<Plus className="mr-2 size-4" />
										New Config
									</Button>
								</CardAction>
							</CardHeader>
							<CardContent>
								{isLoadingConfigs ? (
									<div className="flex min-h-48 items-center justify-center">
										<Spinner className="size-6" />
									</div>
								) : configs.length === 0 ? (
									<div className="rounded-xl border border-dashed px-6 py-12 text-center">
										<p className="text-base font-medium">No agent configs yet</p>
										<p className="mt-2 text-sm text-muted-foreground">
											Create a Claude Code or Pi runtime to start collecting practice-review jobs.
										</p>
									</div>
								) : (
									<div className="grid gap-4 lg:grid-cols-2">
										{configs.map((config) => (
											<AgentConfigCard
												key={config.id}
												config={config}
												isEditing={editingConfigId === config.id}
												isDeleting={deletingConfigId === config.id}
												onEdit={() => handleStartEdit(config)}
												onDelete={() => setDeleteCandidate(config)}
											/>
										))}
									</div>
								)}
							</CardContent>
						</Card>
					</section>

					<section aria-labelledby="job-history-heading">
						<h2 id="job-history-heading" className="sr-only">
							Job history
						</h2>
						<Card>
							<CardHeader>
								<CardTitle>Job History</CardTitle>
								<CardDescription>
									Filter recent workspace jobs, inspect frozen runtime snapshots, and retry failed
									delivery attempts.
								</CardDescription>
							</CardHeader>
							<CardContent className="space-y-4">
								{jobsError && (
									<Alert variant="destructive">
										<AlertCircle className="size-4" />
										<AlertTitle>Could not load jobs</AlertTitle>
										<AlertDescription>{jobsError.message}</AlertDescription>
									</Alert>
								)}

								<div className="grid gap-3 md:grid-cols-[14rem_14rem_1fr]">
									<Field>
										<FieldLabel htmlFor="agent-jobs-status">Status</FieldLabel>
										<Select
											value={jobsFilter.status}
											onValueChange={(value) =>
												value && onChangeJobsFilter({ status: value as JobStatusFilter, page: 0 })
											}
										>
											<SelectTrigger
												id="agent-jobs-status"
												className="w-full"
												aria-label="Filter jobs by status"
											>
												<SelectValue />
											</SelectTrigger>
											<SelectContent>
												{statusFilterOptions.map((status) => (
													<SelectItem key={status} value={status}>
														{status === "ALL" ? "All statuses" : formatJobStatus(status)}
													</SelectItem>
												))}
											</SelectContent>
										</Select>
									</Field>

									<Field>
										<FieldLabel htmlFor="agent-jobs-config">Runtime</FieldLabel>
										<Select
											value={jobsFilter.configId || "ALL"}
											onValueChange={(value) => {
												if (!value) return;
												onChangeJobsFilter({
													configId: value === "ALL" ? "" : value,
													page: 0,
												});
											}}
										>
											<SelectTrigger
												id="agent-jobs-config"
												className="w-full"
												aria-label="Filter jobs by runtime"
											>
												<SelectValue />
											</SelectTrigger>
											<SelectContent>
												<SelectItem value="ALL">All configs</SelectItem>
												{configs.map((config) => (
													<SelectItem key={config.id} value={String(config.id)}>
														{config.name}
													</SelectItem>
												))}
											</SelectContent>
										</Select>
									</Field>

									<div className="flex items-end justify-between gap-3 rounded-xl border bg-muted/30 px-4 py-3">
										<div>
											<p className="text-sm font-medium">{selectedConfigName}</p>
											<p className="text-sm text-muted-foreground">{pageSummary(jobsPage)}</p>
										</div>
										<Button variant="outline" onClick={onRefresh}>
											<RefreshCw className="mr-2 size-4" />
											Reload
										</Button>
									</div>
								</div>

								<div className="overflow-x-auto rounded-xl border">
									<Table>
										<TableHeader>
											<TableRow>
												<TableHead>Status</TableHead>
												<TableHead>Config</TableHead>
												<TableHead>Model</TableHead>
												<TableHead>Created</TableHead>
												<TableHead>Delivery</TableHead>
												<TableHead>Usage</TableHead>
												<TableHead className="text-right">Actions</TableHead>
											</TableRow>
										</TableHeader>
										<TableBody>
											{isLoadingJobs ? (
												<TableRow>
													<TableCell colSpan={7} className="h-32 text-center">
														<div className="flex items-center justify-center gap-2 text-muted-foreground">
															<Spinner className="size-4" />
															Loading agent jobs...
														</div>
													</TableCell>
												</TableRow>
											) : currentJobs.length === 0 ? (
												<TableRow>
													<TableCell colSpan={7} className="h-28 text-center text-muted-foreground">
														No jobs match the current filters.
													</TableCell>
												</TableRow>
											) : (
												currentJobs.map((job) => (
													<TableRow
														key={job.id}
														data-state={selectedJobId === job.id ? "selected" : undefined}
														className={selectedJobId === job.id ? "bg-muted/40" : undefined}
													>
														<TableCell>
															<Badge variant={statusBadgeVariant(job.status)}>
																{formatJobStatus(job.status)}
															</Badge>
														</TableCell>
														<TableCell>
															<div className="font-medium">
																{job.configName ?? "Deleted config"}
															</div>
															<div className="text-xs text-muted-foreground">
																{formatAgentType(job.configAgentType)}
															</div>
														</TableCell>
														<TableCell>
															<div>{job.llmModel ?? job.configModelName ?? "Default model"}</div>
															<div className="text-xs text-muted-foreground">
																{job.llmModelVersion ??
																	job.configModelVersion ??
																	formatProvider(job.configLlmProvider)}
															</div>
														</TableCell>
														<TableCell>{formatDateTime(job.createdAt)}</TableCell>
														<TableCell>
															<Badge variant={deliveryBadgeVariant(job.deliveryStatus)}>
																{job.deliveryStatus ? formatJobStatus(job.deliveryStatus) : "N/A"}
															</Badge>
														</TableCell>
														<TableCell>
															<div>{formatNumber(job.llmTotalCalls)} calls</div>
															<div className="text-xs text-muted-foreground">
																{formatNumber(job.llmTotalInputTokens)} in /{" "}
																{formatNumber(job.llmTotalOutputTokens)} out
															</div>
														</TableCell>
														<TableCell>
															<div className="flex justify-end gap-2">
																<Button
																	variant="outline"
																	size="sm"
																	onClick={() => onSelectJob(job.id)}
																	aria-label={`View job details for ${job.configName ?? "deleted runtime"}`}
																>
																	<Eye className="mr-2 size-4" />
																	View
																</Button>
																{canCancelJob(job) && (
																	<Button
																		variant="outline"
																		size="sm"
																		disabled={cancellingJobId === job.id}
																		onClick={() => onCancelJob(job.id)}
																		aria-label={`Cancel job for ${job.configName ?? "deleted runtime"}`}
																	>
																		{cancellingJobId === job.id ? (
																			<Spinner className="mr-2 size-4" />
																		) : null}
																		Cancel
																	</Button>
																)}
																{canRetryDelivery(job) && (
																	<Button
																		size="sm"
																		disabled={retryingJobId === job.id}
																		onClick={() => onRetryDelivery(job.id)}
																		aria-label={`Retry delivery for ${job.configName ?? "deleted runtime"}`}
																	>
																		{retryingJobId === job.id ? (
																			<Spinner className="mr-2 size-4" />
																		) : null}
																		Retry delivery
																	</Button>
																)}
															</div>
														</TableCell>
													</TableRow>
												))
											)}
										</TableBody>
									</Table>
								</div>

								<div className="flex flex-col gap-3 border-t pt-4 sm:flex-row sm:items-center sm:justify-between">
									<p className="text-sm text-muted-foreground">
										Showing {jobsPage?.numberOfElements ?? 0} of {jobsPage?.totalElements ?? 0} jobs
									</p>
									<div className="flex gap-2">
										<Button
											variant="outline"
											disabled={jobsFilter.page <= 0 || isLoadingJobs}
											onClick={() => onChangeJobsFilter({ page: Math.max(0, jobsFilter.page - 1) })}
										>
											Previous
										</Button>
										<Button
											variant="outline"
											disabled={Boolean(jobsPage?.last ?? true) || isLoadingJobs}
											onClick={() => onChangeJobsFilter({ page: jobsFilter.page + 1 })}
										>
											Next
										</Button>
									</div>
								</div>
							</CardContent>
						</Card>
					</section>
				</div>

				<Card className="xl:sticky xl:top-6 xl:self-start">
					<CardHeader>
						<CardTitle>
							{editingConfigId === "new" ? "New Agent Config" : "Edit Agent Config"}
						</CardTitle>
						<CardDescription>
							Configure naming, runtime, credentials, and execution limits for one review runtime.
						</CardDescription>
					</CardHeader>
					<CardContent>
						<form className="space-y-6" onSubmit={handleSubmit}>
							{Object.keys(errors).length > 0 && (
								<Alert variant="destructive" role="alert" aria-live="assertive">
									<AlertCircle className="size-4" />
									<AlertTitle>Review the highlighted fields</AlertTitle>
									<AlertDescription>
										Update the invalid settings before saving this runtime.
									</AlertDescription>
								</Alert>
							)}
							<FieldSet>
								<FieldLegend>Identity</FieldLegend>
								<FieldGroup>
									<Field data-invalid={errors.name ? "true" : undefined}>
										<FieldLabel htmlFor="agent-config-name">Config name</FieldLabel>
										<Input
											id="agent-config-name"
											value={draft.name}
											onChange={(event) => applyDraft({ ...draft, name: event.target.value })}
											placeholder="e.g. claude-default-reviewer"
											aria-describedby={errors.name ? "agent-config-name-error" : undefined}
											aria-invalid={Boolean(errors.name)}
										/>
										<FieldDescription>Unique within this workspace.</FieldDescription>
										<FieldError id="agent-config-name-error">{errors.name}</FieldError>
									</Field>

									<Field>
										<FieldLabel htmlFor="agent-runtime">Agent runtime</FieldLabel>
										<Select
											value={draft.agentType}
											onValueChange={(value) => {
												if (!value) return;
												const agentType = value as AgentTypeValue;
												applyDraft({
													...draft,
													agentType,
													credentialMode:
														agentType === "PI" && draft.credentialMode === "OAUTH"
															? "PROXY"
															: draft.credentialMode,
													llmProvider:
														agentType === "CLAUDE_CODE" ? "ANTHROPIC" : draft.llmProvider,
												});
											}}
										>
											<SelectTrigger
												id="agent-runtime"
												className="w-full"
												aria-label="Choose an agent runtime"
											>
												<SelectValue />
											</SelectTrigger>
											<SelectContent>
												<SelectItem value="CLAUDE_CODE">Claude Code</SelectItem>
												<SelectItem value="PI">Pi</SelectItem>
											</SelectContent>
										</Select>
										<FieldDescription>
											Claude Code is Anthropic-only. Pi supports Anthropic, OpenAI, and Azure
											OpenAI.
										</FieldDescription>
									</Field>
								</FieldGroup>
							</FieldSet>

							<Separator />

							<FieldSet>
								<FieldLegend>Runtime</FieldLegend>
								<FieldGroup>
									<Field data-invalid={errors.llmProvider ? "true" : undefined}>
										<FieldLabel htmlFor="agent-provider">Provider</FieldLabel>
										<Select
											value={draft.llmProvider}
											onValueChange={(value) =>
												value && applyDraft({ ...draft, llmProvider: value as ProviderValue })
											}
										>
											<SelectTrigger
												id="agent-provider"
												className="w-full"
												disabled={draft.agentType === "CLAUDE_CODE"}
												aria-describedby={errors.llmProvider ? "agent-provider-error" : undefined}
												aria-invalid={Boolean(errors.llmProvider)}
											>
												<SelectValue />
											</SelectTrigger>
											<SelectContent>
												<SelectItem value="ANTHROPIC">Anthropic</SelectItem>
												<SelectItem value="OPENAI">OpenAI</SelectItem>
												<SelectItem value="AZURE_OPENAI">Azure OpenAI</SelectItem>
											</SelectContent>
										</Select>
										<FieldError id="agent-provider-error">{errors.llmProvider}</FieldError>
									</Field>

									<FieldGroup className="grid gap-4 sm:grid-cols-2">
										<Field data-invalid={errors.modelName ? "true" : undefined}>
											<FieldLabel htmlFor="agent-model-name">Model name</FieldLabel>
											<Input
												id="agent-model-name"
												value={draft.modelName}
												onChange={(event) =>
													applyDraft({ ...draft, modelName: event.target.value })
												}
												placeholder={
													draft.agentType === "CLAUDE_CODE"
														? "claude-sonnet-4-20250514"
														: "gpt-5.4-mini"
												}
												aria-describedby={errors.modelName ? "agent-model-name-error" : undefined}
												aria-invalid={Boolean(errors.modelName)}
											/>
											<FieldError id="agent-model-name-error">{errors.modelName}</FieldError>
										</Field>

										<Field data-invalid={errors.modelVersion ? "true" : undefined}>
											<FieldLabel htmlFor="agent-model-version">Model version</FieldLabel>
											<Input
												id="agent-model-version"
												value={draft.modelVersion}
												onChange={(event) =>
													applyDraft({ ...draft, modelVersion: event.target.value })
												}
												placeholder="2026-03-17"
												aria-describedby={
													errors.modelVersion ? "agent-model-version-error" : undefined
												}
												aria-invalid={Boolean(errors.modelVersion)}
											/>
											<FieldError id="agent-model-version-error">{errors.modelVersion}</FieldError>
										</Field>
									</FieldGroup>
								</FieldGroup>
							</FieldSet>

							<Separator />

							<FieldSet>
								<FieldLegend>Credentials And Network</FieldLegend>
								<FieldGroup>
									<Field>
										<FieldLabel htmlFor="agent-credential-mode">Credential mode</FieldLabel>
										<Select
											value={draft.credentialMode}
											onValueChange={(value) => {
												if (!value) return;
												const mode = value as CredentialModeValue;
												applyDraft({
													...draft,
													credentialMode: mode,
													allowInternet: mode === "PROXY" ? draft.allowInternet : true,
													clearLlmApiKey: mode === "PROXY" ? draft.clearLlmApiKey : false,
												});
											}}
										>
											<SelectTrigger
												id="agent-credential-mode"
												className="w-full"
												aria-label="Choose credential mode"
											>
												<SelectValue />
											</SelectTrigger>
											<SelectContent>
												<SelectItem value="PROXY">Proxy</SelectItem>
												<SelectItem value="API_KEY">API key</SelectItem>
												{draft.agentType === "CLAUDE_CODE" && (
													<SelectItem value="OAUTH">OAuth</SelectItem>
												)}
											</SelectContent>
										</Select>
										<FieldDescription>
											Proxy keeps outbound access optional. API key mode works for Claude Code and
											Pi. OAuth is available for Claude Code only.
										</FieldDescription>
									</Field>

									<Field data-invalid={errors.llmApiKey ? "true" : undefined}>
										<FieldLabel htmlFor="agent-secret">Credential / API key</FieldLabel>
										<Input
											id="agent-secret"
											type="password"
											value={draft.llmApiKey}
											disabled={draft.clearLlmApiKey}
											onChange={(event) =>
												applyDraft({
													...draft,
													llmApiKey: event.target.value,
													clearLlmApiKey:
														event.target.value.length > 0 ? false : draft.clearLlmApiKey,
												})
											}
											placeholder={
												existingHasCredential
													? "Stored securely. Leave blank to keep the current secret."
													: "Paste the credential used for direct access"
											}
											autoCapitalize="none"
											autoCorrect="off"
											autoComplete="off"
											spellCheck={false}
											aria-describedby={errors.llmApiKey ? "agent-secret-error" : undefined}
											aria-invalid={Boolean(errors.llmApiKey)}
										/>
										{existingHasCredential && editingConfigId !== "new" && (
											<label
												htmlFor={checkboxId("clear-credential", String(editingConfigId))}
												className="mt-3 flex items-start gap-3 text-sm"
											>
												<Checkbox
													id={checkboxId("clear-credential", String(editingConfigId))}
													checked={draft.clearLlmApiKey}
													onCheckedChange={(checked) =>
														applyDraft({
															...draft,
															clearLlmApiKey: checked === true,
															llmApiKey: checked === true ? "" : draft.llmApiKey,
														})
													}
												/>
												<span>Clear the stored credential on the next save.</span>
											</label>
										)}
										<FieldError id="agent-secret-error">{errors.llmApiKey}</FieldError>
									</Field>

									<Field data-invalid={errors.allowInternet ? "true" : undefined}>
										<FieldTitle>Internet access</FieldTitle>
										<label
											htmlFor={checkboxId("allow-internet", String(editingConfigId))}
											className="mt-2 flex items-start gap-3 text-sm"
										>
											<Checkbox
												id={checkboxId("allow-internet", String(editingConfigId))}
												checked={draft.allowInternet}
												disabled={draft.credentialMode !== "PROXY"}
												onCheckedChange={(checked) =>
													applyDraft({ ...draft, allowInternet: checked === true })
												}
											/>
											<span>Allow the runtime to access the public internet during execution.</span>
										</label>
										<FieldDescription>
											{draft.credentialMode === "PROXY"
												? "Useful for external APIs, package registries, or direct provider access."
												: "Direct credential modes automatically keep internet access enabled."}
										</FieldDescription>
										<FieldError>{errors.allowInternet}</FieldError>
									</Field>
								</FieldGroup>
							</FieldSet>

							<Separator />

							<FieldSet>
								<FieldLegend>Execution</FieldLegend>
								<FieldGroup className="grid gap-4 sm:grid-cols-2">
									<Field data-invalid={errors.timeoutSeconds ? "true" : undefined}>
										<FieldLabel htmlFor="agent-timeout">Timeout (seconds)</FieldLabel>
										<Input
											id="agent-timeout"
											type="number"
											value={draft.timeoutSeconds}
											onChange={(event) =>
												applyDraft({ ...draft, timeoutSeconds: event.target.value })
											}
											min={30}
											max={3600}
											inputMode="numeric"
											aria-describedby={errors.timeoutSeconds ? "agent-timeout-error" : undefined}
											aria-invalid={Boolean(errors.timeoutSeconds)}
										/>
										<FieldError id="agent-timeout-error">{errors.timeoutSeconds}</FieldError>
									</Field>

									<Field data-invalid={errors.maxConcurrentJobs ? "true" : undefined}>
										<FieldLabel htmlFor="agent-concurrency">Max concurrent jobs</FieldLabel>
										<Input
											id="agent-concurrency"
											type="number"
											value={draft.maxConcurrentJobs}
											onChange={(event) =>
												applyDraft({ ...draft, maxConcurrentJobs: event.target.value })
											}
											min={1}
											max={10}
											inputMode="numeric"
											aria-describedby={
												errors.maxConcurrentJobs ? "agent-concurrency-error" : undefined
											}
											aria-invalid={Boolean(errors.maxConcurrentJobs)}
										/>
										<FieldError id="agent-concurrency-error">{errors.maxConcurrentJobs}</FieldError>
									</Field>
								</FieldGroup>

								<Field>
									<FieldTitle>Enabled</FieldTitle>
									<label
										htmlFor={checkboxId("enabled", String(editingConfigId))}
										className="mt-2 flex items-start gap-3 text-sm"
									>
										<Checkbox
											id={checkboxId("enabled", String(editingConfigId))}
											checked={draft.enabled}
											onCheckedChange={(checked) =>
												applyDraft({ ...draft, enabled: checked === true })
											}
										/>
										<span>
											Include this configuration when the workspace submits new practice-review
											jobs.
										</span>
									</label>
								</Field>
							</FieldSet>

							<CardFooter className="-mx-4 mt-2 flex-col items-stretch gap-2 sm:flex-row sm:justify-between sm:px-4">
								<Button type="button" variant="outline" onClick={handleStartCreate}>
									{editingConfigId === "new" ? "Reset form" : "Create another"}
								</Button>
								<Button type="submit" disabled={isSavingConfig}>
									{isSavingConfig ? <Spinner className="mr-2 size-4" /> : null}
									{editingConfigId === "new" ? "Save config" : "Save changes"}
								</Button>
							</CardFooter>
						</form>
					</CardContent>
				</Card>
			</div>

			<Sheet open={selectedJobId !== null} onOpenChange={(open) => !open && onSelectJob(null)}>
				<SheetContent className="w-full sm:max-w-2xl">
					<SheetHeader>
						<SheetTitle>
							{selectedJob ? (selectedJob.configName ?? "Agent job") : "Agent job details"}
						</SheetTitle>
						<SheetDescription>
							Inspect the frozen config snapshot, output payload, and delivery state for this run.
						</SheetDescription>
					</SheetHeader>

					<div className="flex-1 space-y-4 overflow-y-auto px-4 pb-4">
						{isLoadingJobDetails ? (
							<div className="flex min-h-48 items-center justify-center">
								<Spinner className="size-6" />
							</div>
						) : jobDetailsError ? (
							<Alert variant="destructive">
								<AlertCircle className="size-4" />
								<AlertTitle>Could not load job details</AlertTitle>
								<AlertDescription>{jobDetailsError.message}</AlertDescription>
							</Alert>
						) : selectedJob ? (
							<>
								<div className="grid gap-3 sm:grid-cols-2">
									<DetailCard
										label="Status"
										value={
											<Badge variant={statusBadgeVariant(selectedJob.status)}>
												{formatJobStatus(selectedJob.status)}
											</Badge>
										}
									/>
									<DetailCard
										label="Delivery"
										value={
											<Badge variant={deliveryBadgeVariant(selectedJob.deliveryStatus)}>
												{selectedJob.deliveryStatus
													? formatJobStatus(selectedJob.deliveryStatus)
													: "N/A"}
											</Badge>
										}
									/>
									<DetailCard
										label="Runtime"
										value={`${formatAgentType(selectedJob.configAgentType)} / ${formatProvider(selectedJob.configLlmProvider)}`}
									/>
									<DetailCard
										label="Model"
										value={selectedJob.llmModel ?? selectedJob.configModelName ?? "Default model"}
									/>
									<DetailCard label="Created" value={formatDateTime(selectedJob.createdAt)} />
									<DetailCard label="Completed" value={formatDateTime(selectedJob.completedAt)} />
								</div>

								<Card size="sm">
									<CardHeader>
										<CardTitle>Usage</CardTitle>
										<CardDescription>
											Runtime-reported token, call, and cost totals.
										</CardDescription>
									</CardHeader>
									<CardContent className="grid gap-3 sm:grid-cols-3">
										<Metric label="Calls" value={formatNumber(selectedJob.llmTotalCalls)} />
										<Metric
											label="Input tokens"
											value={formatNumber(selectedJob.llmTotalInputTokens)}
										/>
										<Metric
											label="Output tokens"
											value={formatNumber(selectedJob.llmTotalOutputTokens)}
										/>
										<Metric
											label="Reasoning tokens"
											value={formatNumber(selectedJob.llmTotalReasoningTokens)}
										/>
										<Metric
											label="Cache read"
											value={formatNumber(selectedJob.llmCacheReadTokens)}
										/>
										<Metric label="Estimated cost" value={formatCost(selectedJob.llmCostUsd)} />
									</CardContent>
								</Card>

								<JsonSection title="Config snapshot" value={selectedJob.configSnapshot} />
								<JsonSection title="Metadata" value={selectedJob.metadata} />
								<JsonSection title="Output" value={selectedJob.output} />
							</>
						) : null}
					</div>

					<SheetFooter>
						{selectedJob && canCancelJob(selectedJob) && (
							<Button
								variant="outline"
								disabled={cancellingJobId === selectedJob.id}
								onClick={() => onCancelJob(selectedJob.id)}
							>
								{cancellingJobId === selectedJob.id ? <Spinner className="mr-2 size-4" /> : null}
								Cancel job
							</Button>
						)}
						{selectedJob && canRetryDelivery(selectedJob) && (
							<Button
								disabled={retryingJobId === selectedJob.id}
								onClick={() => onRetryDelivery(selectedJob.id)}
							>
								{retryingJobId === selectedJob.id ? <Spinner className="mr-2 size-4" /> : null}
								Retry delivery
							</Button>
						)}
						<Button variant="outline" onClick={() => onSelectJob(null)}>
							Close
						</Button>
					</SheetFooter>
				</SheetContent>
			</Sheet>

			<AlertDialog
				open={deleteCandidate !== null}
				onOpenChange={(open) => {
					if (!open) setDeleteCandidate(null);
				}}
			>
				<AlertDialogContent>
					<AlertDialogHeader>
						<AlertDialogTitle>Delete {deleteCandidate?.name}?</AlertDialogTitle>
						<AlertDialogDescription>
							This removes the config from the workspace. Existing job history stays intact, but
							active jobs must be cancelled before deletion succeeds.
						</AlertDialogDescription>
					</AlertDialogHeader>
					<AlertDialogFooter>
						<AlertDialogCancel>Keep config</AlertDialogCancel>
						<AlertDialogAction
							className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
							onClick={handleDelete}
							disabled={deleteCandidate == null || deletingConfigId === deleteCandidate.id}
						>
							{deleteCandidate != null && deletingConfigId === deleteCandidate.id ? (
								<>
									<Spinner className="mr-2 size-4" />
									Deleting...
								</>
							) : (
								"Delete config"
							)}
						</AlertDialogAction>
					</AlertDialogFooter>
				</AlertDialogContent>
			</AlertDialog>
		</div>
	);
}

function StatCard(props: { label: string; value: string; helper: string }) {
	return (
		<div className="rounded-xl border bg-background/80 px-4 py-3 shadow-sm backdrop-blur-sm">
			<p className="text-sm text-muted-foreground">{props.label}</p>
			<p className="mt-1 text-2xl font-semibold tracking-tight">{props.value}</p>
			<p className="mt-1 text-xs text-muted-foreground">{props.helper}</p>
		</div>
	);
}

function AgentConfigCard(props: {
	config: AgentConfig;
	isEditing: boolean;
	isDeleting: boolean;
	onEdit: () => void;
	onDelete: () => void;
}) {
	const { config, isEditing, isDeleting, onEdit, onDelete } = props;

	return (
		<Card className={isEditing ? "ring-primary/30 border-primary/40 shadow-sm" : undefined}>
			<CardHeader>
				<CardTitle className="flex items-center justify-between gap-3">
					<span className="truncate">{config.name}</span>
					<Badge variant={config.enabled ? "default" : "outline"}>
						{config.enabled ? "Enabled" : "Disabled"}
					</Badge>
				</CardTitle>
				<CardDescription className="flex flex-wrap gap-2">
					<Badge variant="secondary">{formatAgentType(config.agentType)}</Badge>
					<Badge variant="outline">{formatProvider(config.llmProvider)}</Badge>
					<Badge variant="outline">{formatCredentialMode(config.credentialMode)}</Badge>
					{config.hasLlmApiKey && <Badge variant="outline">Stored credential</Badge>}
				</CardDescription>
			</CardHeader>
			<CardContent className="space-y-4">
				<div className="grid gap-3 sm:grid-cols-2">
					<CompactDetail
						icon={<Bot className="size-4" />}
						label="Model"
						value={config.modelName ?? "Workspace default"}
					/>
					<CompactDetail
						icon={<Clock3 className="size-4" />}
						label="Timeout"
						value={`${config.timeoutSeconds}s`}
					/>
					<CompactDetail
						icon={<KeyRound className="size-4" />}
						label="Credentials"
						value={formatCredentialMode(config.credentialMode)}
					/>
					<CompactDetail
						icon={<Globe className="size-4" />}
						label="Network"
						value={config.allowInternet ? "Internet enabled" : "Sandbox only"}
					/>
				</div>
				{config.modelVersion && (
					<div className="rounded-lg bg-muted/40 px-3 py-2 text-sm text-muted-foreground">
						Version snapshot:{" "}
						<span className="font-medium text-foreground">{config.modelVersion}</span>
					</div>
				)}
			</CardContent>
			<CardFooter className="justify-between gap-2">
				<Button variant="outline" onClick={onEdit}>
					{isEditing ? "Editing" : "Edit"}
				</Button>
				<Button variant="destructive" onClick={onDelete} disabled={isDeleting}>
					{isDeleting ? <Spinner className="mr-2 size-4" /> : <Trash2 className="mr-2 size-4" />}
					Delete
				</Button>
			</CardFooter>
		</Card>
	);
}

function CompactDetail(props: { icon: React.ReactNode; label: string; value: string }) {
	return (
		<div className="rounded-lg border bg-muted/20 px-3 py-2">
			<div className="flex items-center gap-2 text-xs uppercase tracking-wide text-muted-foreground">
				{props.icon}
				{props.label}
			</div>
			<div className="mt-1 text-sm font-medium text-foreground">{props.value}</div>
		</div>
	);
}

function DetailCard(props: { label: string; value: React.ReactNode }) {
	return (
		<div className="rounded-xl border bg-muted/20 px-4 py-3">
			<p className="text-xs uppercase tracking-wide text-muted-foreground">{props.label}</p>
			<div className="mt-2 text-sm font-medium">{props.value}</div>
		</div>
	);
}

function Metric(props: { label: string; value: string }) {
	return (
		<div className="rounded-lg border bg-muted/20 px-3 py-2">
			<p className="text-xs uppercase tracking-wide text-muted-foreground">{props.label}</p>
			<p className="mt-1 text-base font-semibold">{props.value}</p>
		</div>
	);
}

function JsonSection(props: { title: string; value: unknown }) {
	return (
		<Card size="sm">
			<CardHeader>
				<CardTitle>{props.title}</CardTitle>
			</CardHeader>
			<CardContent>
				<Textarea readOnly value={formatJson(props.value)} className="min-h-52 font-mono text-xs" />
			</CardContent>
		</Card>
	);
}

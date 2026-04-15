import { z } from "zod";
import type { AgentConfig, AgentJob, AgentRunner, PageAgentJob } from "@/api/types.gen";

export const agentTypeOptions = ["CLAUDE_CODE", "PI"] as const;
export const providerOptions = ["ANTHROPIC", "OPENAI", "AZURE_OPENAI"] as const;
export const credentialModeOptions = ["PROXY", "API_KEY", "OAUTH"] as const;
export const statusFilterOptions = [
	"ALL",
	"QUEUED",
	"RUNNING",
	"COMPLETED",
	"FAILED",
	"TIMED_OUT",
	"CANCELLED",
] as const;

export type AgentTypeValue = (typeof agentTypeOptions)[number];
export type ProviderValue = (typeof providerOptions)[number];
export type CredentialModeValue = (typeof credentialModeOptions)[number];
export type JobStatusFilter = (typeof statusFilterOptions)[number];

export type RunnerDraft = {
	name: string;
	agentType: AgentTypeValue;
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

export type ConfigDraft = {
	name: string;
	enabled: boolean;
	runnerId: string;
};

export type DraftErrors = {
	runner: Partial<Record<keyof RunnerDraft, string>>;
	config: Partial<Record<keyof ConfigDraft, string>>;
};

export const agentConfigFieldIds: Record<string, string> = {
	allowInternet: "agent-runner-allow-internet",
	configName: "agent-config-name",
	credentialMode: "agent-runner-credential-mode",
	enabled: "agent-config-enabled",
	llmApiKey: "agent-runner-secret",
	llmProvider: "agent-runner-provider",
	maxConcurrentJobs: "agent-runner-concurrency",
	modelName: "agent-runner-model-name",
	modelVersion: "agent-runner-model-version",
	runnerId: "agent-config-runner-id",
	runnerName: "agent-runner-name",
	timeoutSeconds: "agent-runner-timeout",
	agentType: "agent-runner-type",
};

const runnerDraftSchema = z.object({
	name: z
		.string()
		.trim()
		.min(1, "Runner name is required")
		.max(100, "Runner name must not exceed 100 characters"),
	agentType: z.enum(agentTypeOptions),
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

const configDraftSchema = z.object({
	name: z
		.string()
		.trim()
		.min(1, "Config name is required")
		.max(100, "Config name must not exceed 100 characters"),
	enabled: z.boolean(),
	runnerId: z.string().trim().min(1, "Select a runner"),
});

const numberFormatter = new Intl.NumberFormat();
const costFormatter = new Intl.NumberFormat(undefined, {
	style: "currency",
	currency: "USD",
	minimumFractionDigits: 2,
	maximumFractionDigits: 4,
});

export function createEmptyRunnerDraft(): RunnerDraft {
	return {
		name: "",
		agentType: "CLAUDE_CODE",
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

export function createEmptyConfigDraft(defaultRunnerId?: number): ConfigDraft {
	return {
		name: "",
		enabled: true,
		runnerId: defaultRunnerId != null ? String(defaultRunnerId) : "",
	};
}

export function createRunnerDraftFromRunner(runner: AgentRunner): RunnerDraft {
	return {
		name: runner.name,
		agentType: runner.agentType,
		modelName: runner.modelName ?? "",
		modelVersion: runner.modelVersion ?? "",
		llmProvider: runner.llmProvider,
		timeoutSeconds: String(runner.timeoutSeconds),
		maxConcurrentJobs: String(runner.maxConcurrentJobs),
		allowInternet: runner.allowInternet,
		credentialMode: runner.credentialMode,
		llmApiKey: "",
		clearLlmApiKey: false,
	};
}

export function createConfigDraftFromConfig(config: AgentConfig): ConfigDraft {
	return {
		name: config.name,
		enabled: config.enabled,
		runnerId: String(config.runnerId),
	};
}

export function normalizeOptional(value: string): string | undefined {
	const trimmed = value.trim();
	return trimmed.length > 0 ? trimmed : undefined;
}

export function validateRunnerDraft(
	draft: RunnerDraft,
	options: { existingHasCredential: boolean },
):
	| { success: true; data: z.infer<typeof runnerDraftSchema> }
	| { success: false; errors: DraftErrors["runner"] } {
	const result = runnerDraftSchema.safeParse(draft);
	const extraErrors: DraftErrors["runner"] = {};

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

	const zodErrors: DraftErrors["runner"] = {};
	if (!result.success) {
		for (const issue of result.error.issues) {
			const field = issue.path[0] as keyof RunnerDraft;
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

export function validateConfigDraft(
	draft: ConfigDraft,
):
	| { success: true; data: z.infer<typeof configDraftSchema> }
	| { success: false; errors: DraftErrors["config"] } {
	const result = configDraftSchema.safeParse(draft);
	if (result.success) {
		return { success: true, data: result.data };
	}

	const errors: DraftErrors["config"] = {};
	for (const issue of result.error.issues) {
		const field = issue.path[0] as keyof ConfigDraft;
		if (!errors[field]) {
			errors[field] = issue.message;
		}
	}

	return { success: false, errors };
}

export function formatAgentType(agentType: AgentTypeValue | string | undefined): string {
	if (agentType === "CLAUDE_CODE") return "Claude Code";
	if (agentType === "PI") return "Pi";
	return agentType ?? "Unknown";
}

export function formatProvider(provider: ProviderValue | string | undefined): string {
	if (provider === "AZURE_OPENAI") return "Azure OpenAI";
	if (provider === "OPENAI") return "OpenAI";
	if (provider === "ANTHROPIC") return "Anthropic";
	return provider ?? "Unknown";
}

export function formatCredentialMode(mode: CredentialModeValue | string | undefined): string {
	if (mode === "API_KEY") return "API key";
	if (mode === "OAUTH") return "OAuth";
	if (mode === "PROXY") return "Proxy";
	return mode ?? "Unknown";
}

export function formatJobStatus(status: AgentJob["status"] | string): string {
	return status
		.toLowerCase()
		.split("_")
		.map((part) => part.charAt(0).toUpperCase() + part.slice(1))
		.join(" ");
}

export function formatDateTime(value: Date | string | undefined): string {
	if (!value) return "-";
	const date = value instanceof Date ? value : new Date(value);
	if (Number.isNaN(date.getTime())) return "-";
	return date.toLocaleString(undefined, {
		dateStyle: "medium",
		timeStyle: "short",
	});
}

export function formatNumber(value: number | undefined): string {
	return value == null ? "-" : numberFormatter.format(value);
}

export function formatCost(value: number | undefined): string {
	return value == null ? "-" : costFormatter.format(value);
}

export function formatJson(value: unknown): string {
	if (value == null) return "-";
	try {
		return JSON.stringify(value, null, 2);
	} catch {
		return String(value);
	}
}

export function statusBadgeVariant(
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

export function deliveryBadgeVariant(
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

export function canCancelJob(job: AgentJob): boolean {
	return job.status === "QUEUED" || job.status === "RUNNING";
}

export function canRetryDelivery(job: AgentJob): boolean {
	return job.status === "COMPLETED" && job.deliveryStatus === "FAILED";
}

export function pageSummary(page: PageAgentJob | undefined): string {
	if (!page || (page.totalElements ?? 0) === 0) {
		return "No jobs yet";
	}

	const currentPage = (page.number ?? 0) + 1;
	const totalPages = page.totalPages ?? 1;
	return `Page ${currentPage} of ${totalPages}`;
}

export function focusFirstInvalidField(errors: DraftErrors) {
	const firstRunnerField = Object.keys(errors.runner)[0];
	const firstConfigField = Object.keys(errors.config)[0];
	const firstField = firstRunnerField ?? firstConfigField;
	const fieldId = firstField ? agentConfigFieldIds[firstField] : undefined;
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

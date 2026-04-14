import { z } from "zod";
import type { AgentConfig, AgentJob, PageAgentJob } from "@/api/types.gen";

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

export type AgentConfigDraft = {
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

export type DraftErrors = Partial<Record<keyof AgentConfigDraft, string>>;

export const agentConfigFieldIds: Partial<Record<keyof AgentConfigDraft, string>> = {
	allowInternet: "agent-allow-internet",
	credentialMode: "agent-credential-mode",
	enabled: "agent-enabled",
	llmApiKey: "agent-secret",
	llmProvider: "agent-provider",
	maxConcurrentJobs: "agent-concurrency",
	modelName: "agent-model-name",
	modelVersion: "agent-model-version",
	name: "agent-config-name",
	timeoutSeconds: "agent-timeout",
};

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

const numberFormatter = new Intl.NumberFormat();
const costFormatter = new Intl.NumberFormat(undefined, {
	style: "currency",
	currency: "USD",
	minimumFractionDigits: 2,
	maximumFractionDigits: 4,
});

export function createEmptyDraft(): AgentConfigDraft {
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

export function createDraftFromConfig(config: AgentConfig): AgentConfigDraft {
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

export function normalizeOptional(value: string): string | undefined {
	const trimmed = value.trim();
	return trimmed.length > 0 ? trimmed : undefined;
}

export function validateDraft(
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

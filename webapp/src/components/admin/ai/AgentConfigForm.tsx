import { useState } from "react";
import { z } from "zod";
import type {
	AgentConfig,
	CreateAgentConfigRequest,
	UpdateAgentConfigRequest,
} from "@/api/types.gen";
import { Button } from "@/components/ui/button";
import {
	Field,
	FieldContent,
	FieldDescription,
	FieldError,
	FieldGroup,
	FieldLabel,
} from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from "@/components/ui/select";
import { Spinner } from "@/components/ui/spinner";
import { Switch } from "@/components/ui/switch";
import { CredentialField } from "./CredentialField";
import {
	CREDENTIAL_MODE_LABELS,
	type CredentialMode,
	LLM_PROVIDER_LABELS,
	type LlmProvider,
	shouldSendKey,
} from "./utils";

const PROVIDERS: LlmProvider[] = ["ANTHROPIC", "OPENAI", "AZURE_OPENAI"];
const MODES: CredentialMode[] = ["PROXY", "API_KEY", "OAUTH"];

const PROVIDER_ITEMS = PROVIDERS.map((p) => ({ value: p, label: LLM_PROVIDER_LABELS[p] }));
const MODE_ITEMS = MODES.map((m) => ({ value: m, label: CREDENTIAL_MODE_LABELS[m] }));

const baseShape = {
	name: z.string().trim().min(1, "Name is required").max(120, "Name is too long"),
	modelName: z.string().trim().max(200).optional(),
	llmBaseUrl: z.string().trim().max(500).optional(),
	timeoutSeconds: z
		.number()
		.int("Must be a whole number")
		.min(30, "Minimum is 30 seconds")
		.max(3600, "Maximum is 3600 seconds"),
	maxConcurrentJobs: z
		.number()
		.int("Must be a whole number")
		.min(1, "Minimum is 1")
		.max(10, "Maximum is 10"),
	enabled: z.boolean(),
	llmProvider: z.enum(["ANTHROPIC", "OPENAI", "AZURE_OPENAI"]),
};

// Direct-auth modes require internet access (the container reaches the provider
// directly); PROXY routes through the internal proxy and hides the key field.
const agentConfigSchema = z.discriminatedUnion("credentialMode", [
	z.object({
		...baseShape,
		credentialMode: z.literal("PROXY"),
		allowInternet: z.boolean(),
	}),
	z.object({
		...baseShape,
		credentialMode: z.literal("API_KEY"),
		allowInternet: z.literal(true, {
			message: "Internet access is required for direct API-key auth",
		}),
	}),
	z.object({
		...baseShape,
		credentialMode: z.literal("OAUTH"),
		allowInternet: z.literal(true, {
			message: "Internet access is required for direct OAuth auth",
		}),
	}),
]);

interface FormState {
	name: string;
	llmProvider: LlmProvider;
	modelName: string;
	llmBaseUrl: string;
	credentialMode: CredentialMode;
	allowInternet: boolean;
	timeoutSeconds: number;
	maxConcurrentJobs: number;
	enabled: boolean;
	llmApiKey: string;
	clearLlmApiKey: boolean;
}

function initialState(config?: AgentConfig): FormState {
	return {
		name: config?.name ?? "",
		llmProvider: config?.llmProvider ?? "ANTHROPIC",
		modelName: config?.modelName ?? "",
		llmBaseUrl: config?.llmBaseUrl ?? "",
		credentialMode: config?.credentialMode ?? "PROXY",
		allowInternet: config?.allowInternet ?? false,
		timeoutSeconds: config?.timeoutSeconds ?? 600,
		maxConcurrentJobs: config?.maxConcurrentJobs ?? 1,
		enabled: config?.enabled ?? true,
		llmApiKey: "",
		clearLlmApiKey: false,
	};
}

export interface AgentConfigFormProps {
	config?: AgentConfig;
	isPending: boolean;
	onCreate: (body: CreateAgentConfigRequest) => void;
	onUpdate: (body: UpdateAgentConfigRequest) => void;
	onCancel?: () => void;
}

export function AgentConfigForm({
	config,
	isPending,
	onCreate,
	onUpdate,
	onCancel,
}: AgentConfigFormProps) {
	const isEdit = config !== undefined;
	const [form, setForm] = useState<FormState>(() => initialState(config));
	const [errors, setErrors] = useState<Record<string, string>>({});

	const set = <K extends keyof FormState>(key: K, value: FormState[K]) => {
		setForm((prev) => ({ ...prev, [key]: value }));
	};

	const handleSubmit = (e: React.FormEvent) => {
		e.preventDefault();
		const parsed = agentConfigSchema.safeParse({
			name: form.name,
			modelName: form.modelName,
			llmBaseUrl: form.llmBaseUrl,
			timeoutSeconds: form.timeoutSeconds,
			maxConcurrentJobs: form.maxConcurrentJobs,
			enabled: form.enabled,
			llmProvider: form.llmProvider,
			credentialMode: form.credentialMode,
			allowInternet: form.allowInternet,
		});

		if (!parsed.success) {
			const next: Record<string, string> = {};
			for (const issue of parsed.error.issues) {
				const key = String(issue.path[0] ?? "");
				if (key && !next[key]) next[key] = issue.message;
			}
			setErrors(next);
			return;
		}

		// Create mode requires an API key for direct-auth modes (edit mode keeps the
		// existing stored key when blank).
		if (!isEdit && form.credentialMode !== "PROXY" && form.llmApiKey.trim().length === 0) {
			setErrors((prev) => ({
				...prev,
				llmApiKey: "API key is required for direct authentication.",
			}));
			return;
		}
		setErrors({});

		const sendKey = shouldSendKey({ mode: form.credentialMode, input: form.llmApiKey });

		if (isEdit) {
			// Note: the update endpoint cannot rename a config (no `name` field).
			const body: UpdateAgentConfigRequest = {
				llmProvider: form.llmProvider,
				modelName: form.modelName.trim() || undefined,
				// Edit sends "" (not undefined) so blanking the field actively clears a stored base URL;
				// create omits it (undefined) to fall back to the provider default. The asymmetry is intentional.
				llmBaseUrl: form.llmBaseUrl.trim(),
				credentialMode: form.credentialMode,
				allowInternet: form.allowInternet,
				timeoutSeconds: form.timeoutSeconds,
				maxConcurrentJobs: form.maxConcurrentJobs,
				enabled: form.enabled,
			};
			if (form.clearLlmApiKey) {
				body.clearLlmApiKey = true;
			} else if (sendKey) {
				body.llmApiKey = form.llmApiKey.trim();
			}
			onUpdate(body);
		} else {
			const body: CreateAgentConfigRequest = {
				name: form.name.trim(),
				llmProvider: form.llmProvider,
				modelName: form.modelName.trim() || undefined,
				llmBaseUrl: form.llmBaseUrl.trim() || undefined,
				credentialMode: form.credentialMode,
				allowInternet: form.allowInternet,
				timeoutSeconds: form.timeoutSeconds,
				maxConcurrentJobs: form.maxConcurrentJobs,
				enabled: form.enabled,
			};
			if (sendKey) {
				body.llmApiKey = form.llmApiKey.trim();
			}
			onCreate(body);
		}
	};

	const handleModeChange = (mode: CredentialMode) => {
		setForm((prev) => ({
			...prev,
			credentialMode: mode,
			allowInternet: mode === "PROXY" ? prev.allowInternet : true,
			llmApiKey: mode === "PROXY" ? "" : prev.llmApiKey,
			clearLlmApiKey: mode === "PROXY" ? false : prev.clearLlmApiKey,
		}));
	};

	return (
		<form onSubmit={handleSubmit} className="space-y-6">
			<FieldGroup>
				<Field data-invalid={Boolean(errors.name)}>
					<FieldLabel htmlFor="agent-name">Name</FieldLabel>
					<Input
						id="agent-name"
						value={form.name}
						onChange={(e) => set("name", e.target.value)}
						disabled={isPending || isEdit}
						placeholder="e.g. Default reviewer"
						aria-invalid={Boolean(errors.name)}
						aria-describedby={errors.name ? "agent-name-error" : undefined}
					/>
					{isEdit && (
						<FieldDescription>This name can't be changed after creation.</FieldDescription>
					)}
					{errors.name && <FieldError id="agent-name-error">{errors.name}</FieldError>}
				</Field>

				<div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
					<Field>
						<FieldLabel htmlFor="agent-provider">Provider</FieldLabel>
						<Select
							items={PROVIDER_ITEMS}
							value={form.llmProvider}
							disabled={isPending}
							onValueChange={(value) => {
								if (value) set("llmProvider", value as LlmProvider);
							}}
						>
							<SelectTrigger id="agent-provider">
								<SelectValue />
							</SelectTrigger>
							<SelectContent>
								{PROVIDERS.map((p) => (
									<SelectItem key={p} value={p}>
										{LLM_PROVIDER_LABELS[p]}
									</SelectItem>
								))}
							</SelectContent>
						</Select>
					</Field>

					<Field>
						<FieldLabel htmlFor="agent-model">Model name</FieldLabel>
						<Input
							id="agent-model"
							value={form.modelName}
							onChange={(e) => set("modelName", e.target.value)}
							disabled={isPending}
							placeholder="e.g. claude-sonnet-4-5"
						/>
					</Field>
				</div>

				<Field>
					<FieldLabel htmlFor="agent-base-url">Base URL</FieldLabel>
					<Input
						id="agent-base-url"
						value={form.llmBaseUrl}
						onChange={(e) => set("llmBaseUrl", e.target.value)}
						disabled={isPending}
						placeholder="https://gpu.example.edu/api (optional)"
					/>
					<FieldDescription>
						Optional. Point to a compatible gateway instead of the provider's default.
					</FieldDescription>
				</Field>

				<Field>
					<FieldLabel htmlFor="agent-mode">How to authenticate</FieldLabel>
					<Select
						items={MODE_ITEMS}
						value={form.credentialMode}
						disabled={isPending}
						onValueChange={(value) => {
							if (value) handleModeChange(value as CredentialMode);
						}}
					>
						<SelectTrigger id="agent-mode">
							<SelectValue />
						</SelectTrigger>
						<SelectContent>
							{MODES.map((m) => (
								<SelectItem key={m} value={m}>
									{CREDENTIAL_MODE_LABELS[m]}
								</SelectItem>
							))}
						</SelectContent>
					</Select>
				</Field>

				<CredentialField
					mode={form.credentialMode}
					hasStoredKey={config?.hasLlmApiKey ?? false}
					value={form.llmApiKey}
					error={errors.llmApiKey}
					onChange={(value) => set("llmApiKey", value)}
					onClear={
						isEdit
							? () => setForm((prev) => ({ ...prev, clearLlmApiKey: true, llmApiKey: "" }))
							: undefined
					}
					disabled={isPending || form.clearLlmApiKey}
				/>

				{form.clearLlmApiKey && (
					<p className="text-sm text-muted-foreground">
						The stored key will be removed when you save.{" "}
						<Button
							type="button"
							variant="link"
							size="sm"
							className="h-auto p-0"
							onClick={() => set("clearLlmApiKey", false)}
						>
							Undo
						</Button>
					</p>
				)}

				<Field orientation="horizontal" data-invalid={Boolean(errors.allowInternet)}>
					<FieldContent>
						<FieldLabel htmlFor="agent-internet">Allow internet access</FieldLabel>
						<FieldDescription>
							The model connects directly to the provider. Required for API key or OAuth; not needed
							for the shared proxy.
						</FieldDescription>
						{errors.allowInternet && <FieldError>{errors.allowInternet}</FieldError>}
					</FieldContent>
					<Switch
						id="agent-internet"
						checked={form.allowInternet}
						disabled={isPending || form.credentialMode !== "PROXY"}
						onCheckedChange={(checked) => set("allowInternet", checked)}
					/>
				</Field>

				<div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
					<Field data-invalid={Boolean(errors.timeoutSeconds)}>
						<FieldLabel htmlFor="agent-timeout">Timeout (seconds)</FieldLabel>
						<Input
							id="agent-timeout"
							type="number"
							min={30}
							max={3600}
							value={String(form.timeoutSeconds)}
							onChange={(e) => set("timeoutSeconds", Number(e.target.value))}
							disabled={isPending}
							aria-invalid={Boolean(errors.timeoutSeconds)}
							aria-describedby={errors.timeoutSeconds ? "agent-timeout-error" : undefined}
						/>
						{errors.timeoutSeconds && (
							<FieldError id="agent-timeout-error">{errors.timeoutSeconds}</FieldError>
						)}
					</Field>

					<Field data-invalid={Boolean(errors.maxConcurrentJobs)}>
						<FieldLabel htmlFor="agent-concurrency">Max concurrent jobs</FieldLabel>
						<Input
							id="agent-concurrency"
							type="number"
							min={1}
							max={10}
							value={String(form.maxConcurrentJobs)}
							onChange={(e) => set("maxConcurrentJobs", Number(e.target.value))}
							disabled={isPending}
							aria-invalid={Boolean(errors.maxConcurrentJobs)}
							aria-describedby={errors.maxConcurrentJobs ? "agent-concurrency-error" : undefined}
						/>
						{errors.maxConcurrentJobs && (
							<FieldError id="agent-concurrency-error">{errors.maxConcurrentJobs}</FieldError>
						)}
					</Field>
				</div>

				<Field orientation="horizontal">
					<FieldContent>
						<FieldLabel htmlFor="agent-enabled">Enabled</FieldLabel>
						<FieldDescription>
							Disabled models are skipped when running all models.
						</FieldDescription>
					</FieldContent>
					<Switch
						id="agent-enabled"
						checked={form.enabled}
						disabled={isPending}
						onCheckedChange={(checked) => set("enabled", checked)}
					/>
				</Field>
			</FieldGroup>

			<div className="flex justify-end gap-2">
				{onCancel && (
					<Button type="button" variant="outline" onClick={onCancel} disabled={isPending}>
						Cancel
					</Button>
				)}
				<Button type="submit" disabled={isPending}>
					{isPending ? (
						<>
							<Spinner className="mr-2 h-4 w-4" />
							Saving…
						</>
					) : isEdit ? (
						"Save changes"
					) : (
						"Create model"
					)}
				</Button>
			</div>
		</form>
	);
}

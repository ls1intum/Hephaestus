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
import { LLM_PROVIDER_LABELS, type LlmProvider } from "./utils";

const PROVIDERS: LlmProvider[] = ["ANTHROPIC", "OPENAI", "AZURE_OPENAI"];
const PROVIDER_ITEMS = PROVIDERS.map((p) => ({ value: p, label: LLM_PROVIDER_LABELS[p] }));

const MODEL_PLACEHOLDER: Record<LlmProvider, string> = {
	ANTHROPIC: "e.g. claude-sonnet-4-5",
	OPENAI: "e.g. gpt-5.4-mini",
	AZURE_OPENAI: "e.g. gpt-5.4-mini (deployment name)",
};

// Workspace models always authenticate as "API key over the in-app proxy" — see CredentialField + ADR 0006.
const agentConfigSchema = z.object({
	name: z.string().trim().min(1, "Name is required").max(120, "Name is too long"),
	modelName: z.string().trim().max(200).optional(),
	llmProvider: z.enum(["ANTHROPIC", "OPENAI", "AZURE_OPENAI"]),
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
});

interface FormState {
	name: string;
	llmProvider: LlmProvider;
	modelName: string;
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
			llmProvider: form.llmProvider,
			timeoutSeconds: form.timeoutSeconds,
			maxConcurrentJobs: form.maxConcurrentJobs,
			enabled: form.enabled,
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

		// The proxy needs a key to inject, so one is required on create. On edit a blank field keeps the
		// stored key (the value is never sent back to the browser).
		if (!isEdit && form.llmApiKey.trim().length === 0) {
			setErrors({ llmApiKey: "An API key is required." });
			return;
		}
		setErrors({});

		const hasKeyInput = form.llmApiKey.trim().length > 0;

		if (isEdit) {
			// The update endpoint cannot rename a config (no `name` field).
			const body: UpdateAgentConfigRequest = {
				llmProvider: form.llmProvider,
				modelName: form.modelName.trim() || undefined,
				timeoutSeconds: form.timeoutSeconds,
				maxConcurrentJobs: form.maxConcurrentJobs,
				enabled: form.enabled,
			};
			if (form.clearLlmApiKey) {
				body.clearLlmApiKey = true;
			} else if (hasKeyInput) {
				body.llmApiKey = form.llmApiKey.trim();
			}
			onUpdate(body);
		} else {
			onCreate({
				name: form.name.trim(),
				llmProvider: form.llmProvider,
				modelName: form.modelName.trim() || undefined,
				timeoutSeconds: form.timeoutSeconds,
				maxConcurrentJobs: form.maxConcurrentJobs,
				enabled: form.enabled,
				llmApiKey: form.llmApiKey.trim(),
			});
		}
	};

	return (
		<form onSubmit={handleSubmit} className="space-y-6">
			<FieldGroup>
				<Field data-invalid={Boolean(errors.name)}>
					<FieldLabel htmlFor="agent-name">
						Name{" "}
						<span className="text-destructive" aria-hidden="true">
							*
						</span>
					</FieldLabel>
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
						<FieldLabel htmlFor="agent-model">
							Model name <span className="font-normal text-muted-foreground">(optional)</span>
						</FieldLabel>
						<Input
							id="agent-model"
							value={form.modelName}
							onChange={(e) => set("modelName", e.target.value)}
							disabled={isPending}
							placeholder={MODEL_PLACEHOLDER[form.llmProvider]}
						/>
					</Field>
				</div>

				<CredentialField
					hasStoredKey={config?.hasLlmApiKey ?? false}
					required={!isEdit}
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

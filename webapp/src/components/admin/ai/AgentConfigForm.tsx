import { useState } from "react";
import { z } from "zod";
import type {
	AgentConfig,
	AvailableLlmModel,
	CreateAgentConfigRequest,
	UpdateAgentConfigRequest,
} from "@/api/types.gen";
import { Alert, AlertDescription } from "@/components/ui/alert";
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
import { Spinner } from "@/components/ui/spinner";
import { Switch } from "@/components/ui/switch";
import { ModelPicker, type ModelSelection } from "./ModelPicker";

const agentConfigSchema = z.object({
	name: z.string().trim().min(1, "Name is required").max(120, "Name is too long"),
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
	timeoutSeconds: number;
	maxConcurrentJobs: number;
	enabled: boolean;
	allowInternet: boolean;
	selection: ModelSelection | null;
}

function selectionOf(config?: AgentConfig): ModelSelection | null {
	if (config?.instanceModelId != null) {
		return { scope: "SHARED", id: config.instanceModelId };
	}
	if (config?.workspaceModelId != null) {
		return { scope: "WORKSPACE", id: config.workspaceModelId };
	}
	return null;
}

function initialState(config?: AgentConfig): FormState {
	return {
		name: config?.name ?? "",
		timeoutSeconds: config?.timeoutSeconds ?? 600,
		maxConcurrentJobs: config?.maxConcurrentJobs ?? 1,
		enabled: config?.enabled ?? true,
		allowInternet: config?.allowInternet ?? false,
		selection: selectionOf(config),
	};
}

export interface AgentConfigFormProps {
	config?: AgentConfig;
	/** Models this workspace may bind to — shared (instance catalog) and its own provider's. */
	availableModels: AvailableLlmModel[];
	isPending: boolean;
	onCreate: (body: CreateAgentConfigRequest) => void;
	onUpdate: (body: UpdateAgentConfigRequest) => void;
	onCancel?: () => void;
}

export function AgentConfigForm({
	config,
	availableModels,
	isPending,
	onCreate,
	onUpdate,
	onCancel,
}: AgentConfigFormProps) {
	const isEdit = config !== undefined;
	// A config from before the model-catalog cutover: bound to neither a shared nor a workspace model,
	// still running on the legacy provider/model-name columns. Its old fields are read-only here — the
	// only way forward is picking a model from the catalog, which rebinds it and drops the legacy path.
	const isLegacy = isEdit && config.instanceModelId == null && config.workspaceModelId == null;

	const [form, setForm] = useState<FormState>(() => initialState(config));
	const [errors, setErrors] = useState<Record<string, string>>({});

	const set = <K extends keyof FormState>(key: K, value: FormState[K]) => {
		setForm((prev) => ({ ...prev, [key]: value }));
	};

	const handleSubmit = (e: React.FormEvent) => {
		e.preventDefault();
		const parsed = agentConfigSchema.safeParse({
			name: form.name,
			timeoutSeconds: form.timeoutSeconds,
			maxConcurrentJobs: form.maxConcurrentJobs,
			enabled: form.enabled,
		});

		const next: Record<string, string> = {};
		if (!parsed.success) {
			for (const issue of parsed.error.issues) {
				const key = String(issue.path[0] ?? "");
				if (key && !next[key]) next[key] = issue.message;
			}
		}
		// A legacy config may be saved without touching its binding (a no-op server-side); every other
		// path — create, or editing an already-bound config — must have a model selected.
		if (form.selection === null && !isLegacy) {
			next.selection = "Select a model.";
		}
		if (Object.keys(next).length > 0) {
			setErrors(next);
			return;
		}
		setErrors({});

		const modelBinding: Pick<UpdateAgentConfigRequest, "instanceModelId" | "workspaceModelId"> =
			form.selection === null
				? {}
				: form.selection.scope === "SHARED"
					? { instanceModelId: form.selection.id }
					: { workspaceModelId: form.selection.id };

		if (isEdit) {
			const body: UpdateAgentConfigRequest = {
				timeoutSeconds: form.timeoutSeconds,
				maxConcurrentJobs: form.maxConcurrentJobs,
				enabled: form.enabled,
				allowInternet: form.allowInternet,
				...modelBinding,
			};
			onUpdate(body);
		} else {
			onCreate({
				name: form.name.trim(),
				timeoutSeconds: form.timeoutSeconds,
				maxConcurrentJobs: form.maxConcurrentJobs,
				enabled: form.enabled,
				allowInternet: form.allowInternet,
				...modelBinding,
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
						aria-required="true"
						aria-invalid={Boolean(errors.name)}
						aria-describedby={errors.name ? "agent-name-error" : undefined}
					/>
					{isEdit && (
						<FieldDescription>This name can't be changed after creation.</FieldDescription>
					)}
					{errors.name && <FieldError id="agent-name-error">{errors.name}</FieldError>}
				</Field>

				{isLegacy && (
					<Alert>
						<AlertDescription>
							Using legacy provider settings
							{config?.modelName ? ` (${config.modelName})` : ""}. Pick a model below to switch to
							the catalog.
						</AlertDescription>
					</Alert>
				)}

				<Field data-invalid={Boolean(errors.selection)}>
					<FieldLabel htmlFor="agent-model">
						Model
						{!isLegacy && (
							<span className="text-destructive" aria-hidden="true">
								{" *"}
							</span>
						)}
					</FieldLabel>
					<ModelPicker
						id="agent-model"
						availableModels={availableModels}
						value={form.selection}
						onChange={(selection) => set("selection", selection)}
						disabled={isPending}
						invalid={Boolean(errors.selection)}
						aria-describedby={errors.selection ? "agent-model-error" : undefined}
					/>
					{errors.selection && <FieldError id="agent-model-error">{errors.selection}</FieldError>}
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
						<FieldLabel htmlFor="agent-internet">Internet access</FieldLabel>
						<FieldDescription>
							Allow the agent's sandbox to reach the public internet.
						</FieldDescription>
					</FieldContent>
					<Switch
						id="agent-internet"
						checked={form.allowInternet}
						disabled={isPending}
						onCheckedChange={(checked) => set("allowInternet", checked)}
					/>
				</Field>

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

import type { FormEvent } from "react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
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
import { Spinner } from "@/components/ui/spinner";
import type {
	AgentConfigDraft,
	AgentTypeValue,
	CredentialModeValue,
	DraftErrors,
	ProviderValue,
} from "./utils";
import { agentConfigFieldIds } from "./utils";

export interface AgentConfigFormProps {
	mode: "create" | "edit";
	draft: AgentConfigDraft;
	errors: DraftErrors;
	existingHasCredential: boolean;
	isSaving: boolean;
	onDraftChange: (nextDraft: AgentConfigDraft) => void;
	onSubmit: (event: FormEvent<HTMLFormElement>) => void;
	onReset: () => void;
}

export function AgentConfigForm({
	mode,
	draft,
	errors,
	existingHasCredential,
	isSaving,
	onDraftChange,
	onSubmit,
	onReset,
}: AgentConfigFormProps) {
	return (
		<form className="space-y-6" onSubmit={onSubmit}>
			{Object.keys(errors).length > 0 && (
				<Alert variant="destructive" role="alert" aria-live="assertive">
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
						<FieldLabel htmlFor={agentConfigFieldIds.name}>Config name</FieldLabel>
						<Input
							id={agentConfigFieldIds.name}
							name="name"
							autoComplete="off"
							value={draft.name}
							onChange={(event) => onDraftChange({ ...draft, name: event.target.value })}
							placeholder="e.g. claude-default-reviewer"
							aria-describedby={errors.name ? "agent-config-name-error" : undefined}
							aria-invalid={Boolean(errors.name)}
						/>
						<FieldDescription>Unique within this workspace.</FieldDescription>
						<FieldError id="agent-config-name-error">{errors.name}</FieldError>
					</Field>

					<Field>
						<FieldLabel htmlFor={agentConfigFieldIds.agentType}>Agent runtime</FieldLabel>
						<Select
							value={draft.agentType}
							onValueChange={(value) => {
								if (!value) return;
								const agentType = value as AgentTypeValue;
								onDraftChange({
									...draft,
									agentType,
									credentialMode:
										agentType === "PI" && draft.credentialMode === "OAUTH"
											? "PROXY"
											: draft.credentialMode,
									llmProvider: agentType === "CLAUDE_CODE" ? "ANTHROPIC" : draft.llmProvider,
								});
							}}
						>
							<SelectTrigger id={agentConfigFieldIds.agentType} className="w-full">
								<SelectValue />
							</SelectTrigger>
							<SelectContent>
								<SelectItem value="CLAUDE_CODE">Claude Code</SelectItem>
								<SelectItem value="PI">Pi</SelectItem>
							</SelectContent>
						</Select>
						<FieldDescription>
							Claude Code is Anthropic-only. Pi supports Anthropic, OpenAI, and Azure OpenAI.
						</FieldDescription>
					</Field>
				</FieldGroup>
			</FieldSet>

			<Separator />

			<FieldSet>
				<FieldLegend>Runtime</FieldLegend>
				<FieldGroup>
					<Field data-invalid={errors.llmProvider ? "true" : undefined}>
						<FieldLabel htmlFor={agentConfigFieldIds.llmProvider}>Provider</FieldLabel>
						<Select
							value={draft.llmProvider}
							onValueChange={(value) => {
								if (!value) return;
								onDraftChange({ ...draft, llmProvider: value as ProviderValue });
							}}
						>
							<SelectTrigger
								id={agentConfigFieldIds.llmProvider}
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
							<FieldLabel htmlFor={agentConfigFieldIds.modelName}>Model name</FieldLabel>
							<Input
								id={agentConfigFieldIds.modelName}
								name="modelName"
								autoComplete="off"
								value={draft.modelName}
								onChange={(event) => onDraftChange({ ...draft, modelName: event.target.value })}
								placeholder={
									draft.agentType === "CLAUDE_CODE" ? "claude-sonnet-4-20250514" : "gpt-5.4-mini"
								}
								aria-describedby={errors.modelName ? "agent-model-name-error" : undefined}
								aria-invalid={Boolean(errors.modelName)}
							/>
							<FieldError id="agent-model-name-error">{errors.modelName}</FieldError>
						</Field>

						<Field data-invalid={errors.modelVersion ? "true" : undefined}>
							<FieldLabel htmlFor={agentConfigFieldIds.modelVersion}>Model version</FieldLabel>
							<Input
								id={agentConfigFieldIds.modelVersion}
								name="modelVersion"
								autoComplete="off"
								value={draft.modelVersion}
								onChange={(event) => onDraftChange({ ...draft, modelVersion: event.target.value })}
								placeholder="2026-03-17"
								aria-describedby={errors.modelVersion ? "agent-model-version-error" : undefined}
								aria-invalid={Boolean(errors.modelVersion)}
							/>
							<FieldError id="agent-model-version-error">{errors.modelVersion}</FieldError>
						</Field>
					</FieldGroup>
				</FieldGroup>
			</FieldSet>

			<Separator />

			<FieldSet>
				<FieldLegend>Credentials and network</FieldLegend>
				<FieldGroup>
					<Field>
						<FieldLabel htmlFor={agentConfigFieldIds.credentialMode}>Credential mode</FieldLabel>
						<Select
							value={draft.credentialMode}
							onValueChange={(value) => {
								if (!value) return;
								const credentialMode = value as CredentialModeValue;
								onDraftChange({
									...draft,
									credentialMode,
									allowInternet: credentialMode === "PROXY" ? draft.allowInternet : true,
									clearLlmApiKey: credentialMode === "PROXY" ? draft.clearLlmApiKey : false,
								});
							}}
						>
							<SelectTrigger id={agentConfigFieldIds.credentialMode} className="w-full">
								<SelectValue />
							</SelectTrigger>
							<SelectContent>
								<SelectItem value="PROXY">Proxy</SelectItem>
								<SelectItem value="API_KEY">API key</SelectItem>
								{draft.agentType === "CLAUDE_CODE" && <SelectItem value="OAUTH">OAuth</SelectItem>}
							</SelectContent>
						</Select>
						<FieldDescription>
							Proxy keeps outbound access optional. OAuth is available for Claude Code only.
						</FieldDescription>
					</Field>

					<Field data-invalid={errors.llmApiKey ? "true" : undefined}>
						<FieldLabel htmlFor={agentConfigFieldIds.llmApiKey}>Credential / API key</FieldLabel>
						<Input
							id={agentConfigFieldIds.llmApiKey}
							name="llmApiKey"
							type="password"
							value={draft.llmApiKey}
							disabled={draft.clearLlmApiKey}
							onChange={(event) =>
								onDraftChange({
									...draft,
									llmApiKey: event.target.value,
									clearLlmApiKey: event.target.value.length > 0 ? false : draft.clearLlmApiKey,
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
						{existingHasCredential && mode === "edit" && (
							<label
								htmlFor="agent-clear-credential"
								className="mt-3 flex items-start gap-3 text-sm"
							>
								<Checkbox
									id="agent-clear-credential"
									checked={draft.clearLlmApiKey}
									onCheckedChange={(checked) =>
										onDraftChange({
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
							htmlFor={agentConfigFieldIds.allowInternet}
							className="mt-2 flex items-start gap-3 text-sm"
						>
							<Checkbox
								id={agentConfigFieldIds.allowInternet}
								checked={draft.allowInternet}
								disabled={draft.credentialMode !== "PROXY"}
								onCheckedChange={(checked) =>
									onDraftChange({ ...draft, allowInternet: checked === true })
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
						<FieldLabel htmlFor={agentConfigFieldIds.timeoutSeconds}>Timeout (seconds)</FieldLabel>
						<Input
							id={agentConfigFieldIds.timeoutSeconds}
							name="timeoutSeconds"
							type="number"
							value={draft.timeoutSeconds}
							onChange={(event) => onDraftChange({ ...draft, timeoutSeconds: event.target.value })}
							min={30}
							max={3600}
							inputMode="numeric"
							aria-describedby={errors.timeoutSeconds ? "agent-timeout-error" : undefined}
							aria-invalid={Boolean(errors.timeoutSeconds)}
						/>
						<FieldError id="agent-timeout-error">{errors.timeoutSeconds}</FieldError>
					</Field>

					<Field data-invalid={errors.maxConcurrentJobs ? "true" : undefined}>
						<FieldLabel htmlFor={agentConfigFieldIds.maxConcurrentJobs}>
							Max concurrent jobs
						</FieldLabel>
						<Input
							id={agentConfigFieldIds.maxConcurrentJobs}
							name="maxConcurrentJobs"
							type="number"
							value={draft.maxConcurrentJobs}
							onChange={(event) =>
								onDraftChange({ ...draft, maxConcurrentJobs: event.target.value })
							}
							min={1}
							max={10}
							inputMode="numeric"
							aria-describedby={errors.maxConcurrentJobs ? "agent-concurrency-error" : undefined}
							aria-invalid={Boolean(errors.maxConcurrentJobs)}
						/>
						<FieldError id="agent-concurrency-error">{errors.maxConcurrentJobs}</FieldError>
					</Field>
				</FieldGroup>

				<Field>
					<FieldTitle>Enabled</FieldTitle>
					<label
						htmlFor={agentConfigFieldIds.enabled}
						className="mt-2 flex items-start gap-3 text-sm"
					>
						<Checkbox
							id={agentConfigFieldIds.enabled}
							checked={draft.enabled}
							onCheckedChange={(checked) => onDraftChange({ ...draft, enabled: checked === true })}
						/>
						<span>
							Include this configuration when the workspace submits new practice-review jobs.
						</span>
					</label>
				</Field>
			</FieldSet>

			<div className="flex flex-col gap-2 border-t pt-4 sm:flex-row sm:justify-between">
				<Button type="button" variant="outline" onClick={onReset}>
					{mode === "create" ? "Reset form" : "Create another"}
				</Button>
				<Button type="submit" disabled={isSaving}>
					{isSaving ? <Spinner className="mr-2 size-4" /> : null}
					{mode === "create" ? "Save config" : "Save changes"}
				</Button>
			</div>
		</form>
	);
}

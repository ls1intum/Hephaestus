import { useId } from "react";
import { Checkbox } from "@/components/ui/checkbox";
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
import {
	type FieldErrors,
	type LlmConnectionFormField,
	validateLlmConnectionForm,
} from "@/lib/llm-form-validation";
import {
	authModeDefaultFor,
	baseUrlDefaultFor,
	type LlmAuthMode,
	type OpenAiConnectionIdentity,
	PROVIDER_PRESET_LABELS,
	PROVIDER_PRESET_ORDER,
	PROVIDER_PRESET_SELECT_ITEMS,
	type ProviderPreset,
	presetForConnection,
	usesResponsesApi,
} from "@/lib/llm-provider-type";

/**
 * How an OpenAI-compatible endpoint is described, asked identically by the instance console and by a
 * workspace connecting its own provider.
 *
 * Only the description lives here. The two dialogs around it differ in ways that do not reduce to a
 * flag — the instance one can probe an unsaved draft to seed a model datalist, the workspace one owns
 * an active/inactive switch — so they stay two components and compose this in.
 */
export interface LlmConnectionFieldsValue {
	displayName: string;
	baseUrl: string;
	preset: ProviderPreset;
	useResponsesApi: boolean;
	authMode: LlmAuthMode;
	/** Always starts blank, even when editing: a stored key is never read back to the browser. */
	apiKey: string;
	clearApiKey: boolean;
}

/** The routing columns both `LlmConnection` and `WorkspaceLlmConnection` carry under the same names. */
type EditedConnection = OpenAiConnectionIdentity & {
	displayName: string;
	authMode?: LlmAuthMode;
};

export function connectionFieldsValueOf(
	connection: EditedConnection | null,
): LlmConnectionFieldsValue {
	return {
		displayName: connection?.displayName ?? "",
		baseUrl: connection?.baseUrl ?? baseUrlDefaultFor("OPENAI"),
		preset: connection ? presetForConnection(connection) : "OPENAI",
		useResponsesApi: connection ? usesResponsesApi(connection.apiProtocol) : false,
		authMode: connection?.authMode ?? "BEARER",
		apiKey: "",
		clearApiKey: false,
	};
}

/**
 * The shared rules, not a presence check of either form's own: `noValidate` makes `type="url"` inert,
 * and an endpoint carrying a credential or a query string is rejected by the server whichever console
 * pasted it.
 */
export function validateConnectionFields(
	value: LlmConnectionFieldsValue,
	isEdit: boolean,
): FieldErrors<LlmConnectionFormField> {
	return validateLlmConnectionForm({
		displayName: value.displayName,
		// The endpoint is immutable, so an edit neither sends one nor validates one.
		baseUrl: isEdit ? undefined : value.baseUrl,
	});
}

export interface LlmConnectionFieldsProps {
	value: LlmConnectionFieldsValue;
	/**
	 * Called with the whole next value, not a per-field setter, so a caller can react to *any* change
	 * — the instance dialog invalidates a probe result whose inputs have since moved — without each
	 * control needing to know about it.
	 */
	onChange: (value: LlmConnectionFieldsValue) => void;
	errors: FieldErrors<LlmConnectionFormField>;
	isEdit: boolean;
	/** The saved connection has a credential, so the field offers to keep or remove it. */
	hasApiKey: boolean;
	apiKeyLast4?: string;
}

export function LlmConnectionFields({
	value,
	onChange,
	errors,
	isEdit,
	hasApiKey,
	apiKeyLast4,
}: LlmConnectionFieldsProps) {
	const update = (patch: Partial<LlmConnectionFieldsValue>) => onChange({ ...value, ...patch });

	// `useId()` rather than hand-spelled ids, the house rule `BudgetAmountDialog` set: the two dialogs
	// that render this spell out the same field names, and two forms cannot own one id.
	const displayNameId = useId();
	const presetId = useId();
	const responsesApiId = useId();
	const baseUrlId = useId();
	const authModeId = useId();
	const apiKeyId = useId();
	const clearApiKeyId = useId();
	const displayNameErrorId = useId();
	const baseUrlErrorId = useId();

	/** Picking a preset fills in what it implies, but never overwrites a URL that was typed by hand. */
	const applyPreset = (next: ProviderPreset) =>
		update({
			preset: next,
			authMode: authModeDefaultFor(next),
			...(!value.baseUrl || value.baseUrl === baseUrlDefaultFor(value.preset)
				? { baseUrl: baseUrlDefaultFor(next) }
				: {}),
		});

	return (
		<>
			<Field data-invalid={Boolean(errors.displayName)}>
				<FieldLabel htmlFor={displayNameId}>Display name</FieldLabel>
				<Input
					id={displayNameId}
					value={value.displayName}
					onChange={(event) => update({ displayName: event.target.value })}
					placeholder="e.g. Production OpenAI"
					// `required` is semantics only — the form is `noValidate`, so the browser never acts on
					// it — but it is what tells a screen reader the field is required before submit (SC 3.3.2).
					required
					aria-invalid={Boolean(errors.displayName)}
					aria-describedby={errors.displayName ? displayNameErrorId : undefined}
				/>
				{errors.displayName && (
					<FieldError id={displayNameErrorId}>{errors.displayName}</FieldError>
				)}
			</Field>

			{!isEdit && (
				<FieldGroup className="gap-3">
					<Field>
						<FieldLabel htmlFor={presetId}>Endpoint preset</FieldLabel>
						<Select
							items={PROVIDER_PRESET_SELECT_ITEMS}
							value={value.preset}
							onValueChange={(next) => next && applyPreset(next as ProviderPreset)}
						>
							<SelectTrigger id={presetId} className="w-full">
								<SelectValue />
							</SelectTrigger>
							<SelectContent>
								{PROVIDER_PRESET_ORDER.map((item) => (
									<SelectItem key={item} value={item}>
										{PROVIDER_PRESET_LABELS[item]}
									</SelectItem>
								))}
							</SelectContent>
						</Select>
						{value.preset === "AZURE_OPENAI_V1" && (
							<FieldDescription>
								Replace RESOURCE below with your Azure resource name. The v1 API does not need an
								api-version parameter.
							</FieldDescription>
						)}
					</Field>

					<Field orientation="horizontal">
						<Checkbox
							id={responsesApiId}
							checked={value.useResponsesApi}
							onCheckedChange={(checked) => update({ useResponsesApi: checked === true })}
						/>
						<FieldContent>
							<FieldLabel htmlFor={responsesApiId} className="font-normal">
								Use the Responses API instead of Chat Completions
							</FieldLabel>
						</FieldContent>
					</Field>
				</FieldGroup>
			)}

			<Field data-invalid={Boolean(errors.baseUrl)}>
				<FieldLabel htmlFor={baseUrlId}>Base URL</FieldLabel>
				<Input
					id={baseUrlId}
					type="url"
					value={value.baseUrl}
					onChange={(event) => update({ baseUrl: event.target.value })}
					disabled={isEdit}
					placeholder="https://api.openai.com/v1"
					required={!isEdit}
					autoComplete="off"
					aria-invalid={Boolean(errors.baseUrl)}
					aria-describedby={errors.baseUrl ? baseUrlErrorId : undefined}
				/>
				{isEdit && (
					<FieldDescription>
						Endpoint, API shape and authentication can't change. Add a connection instead.
					</FieldDescription>
				)}
				{errors.baseUrl && <FieldError id={baseUrlErrorId}>{errors.baseUrl}</FieldError>}
			</Field>

			{!isEdit && value.preset === "OTHER" && (
				<Field>
					<FieldLabel htmlFor={authModeId}>Authentication</FieldLabel>
					<Select
						items={[
							{ value: "BEARER", label: "Bearer token" },
							{ value: "API_KEY", label: "api-key header" },
						]}
						value={value.authMode}
						onValueChange={(next) => next && update({ authMode: next as LlmAuthMode })}
					>
						<SelectTrigger id={authModeId} className="w-full">
							<SelectValue />
						</SelectTrigger>
						<SelectContent>
							<SelectItem value="BEARER">Bearer token</SelectItem>
							<SelectItem value="API_KEY">api-key header</SelectItem>
						</SelectContent>
					</Select>
				</Field>
			)}

			<FieldGroup className="gap-3">
				<Field>
					<FieldLabel htmlFor={apiKeyId}>API key</FieldLabel>
					<Input
						id={apiKeyId}
						type="password"
						value={value.apiKey}
						onChange={(event) => update({ apiKey: event.target.value })}
						disabled={value.clearApiKey}
						placeholder={
							hasApiKey ? `Configured · ends in ····${apiKeyLast4 ?? "····"}` : "Enter API key"
						}
						autoComplete="off"
					/>
					<FieldDescription>
						{hasApiKey ? "Leave blank to keep the current key." : "Stored encrypted."}
					</FieldDescription>
				</Field>

				{hasApiKey && (
					<Field orientation="horizontal">
						<Checkbox
							id={clearApiKeyId}
							checked={value.clearApiKey}
							onCheckedChange={(checked) =>
								// Removing the key and typing a replacement are two different intents; holding both
								// would send a key the field no longer shows.
								update({ clearApiKey: checked === true, ...(checked === true && { apiKey: "" }) })
							}
						/>
						<FieldContent>
							<FieldLabel htmlFor={clearApiKeyId} className="font-normal">
								Remove stored API key
							</FieldLabel>
						</FieldContent>
					</Field>
				)}
			</FieldGroup>
		</>
	);
}

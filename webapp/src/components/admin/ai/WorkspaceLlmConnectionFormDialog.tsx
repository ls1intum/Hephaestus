import { AlertTriangle } from "lucide-react";
import { useId, useState } from "react";
import type {
	CreateWorkspaceLlmConnectionRequest,
	UpdateWorkspaceLlmConnectionRequest,
	WorkspaceLlmConnection,
} from "@/api/types.gen";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
	Dialog,
	DialogBody,
	DialogClose,
	DialogContent,
	DialogDescription,
	DialogFooter,
	DialogHeader,
	DialogTitle,
} from "@/components/ui/dialog";
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
import { Switch } from "@/components/ui/switch";
import {
	type FieldErrors,
	type LlmConnectionFormField,
	validateLlmConnectionForm,
} from "@/lib/llm-form-validation";
import {
	authModeDefaultFor,
	baseUrlDefaultFor,
	defaultProtocolFor,
	type LlmAuthMode,
	PROVIDER_PRESET_LABELS,
	PROVIDER_PRESET_ORDER,
	PROVIDER_PRESET_SELECT_ITEMS,
	type ProviderPreset,
	presetForConnection,
	usesResponsesApi,
} from "@/lib/llm-provider-type";

export interface WorkspaceLlmConnectionFormDialogProps {
	open: boolean;
	onOpenChange: (open: boolean) => void;
	editing: WorkspaceLlmConnection | null;
	isSubmitting: boolean;
	onCreate: (body: CreateWorkspaceLlmConnectionRequest) => void;
	onUpdate: (id: number, body: UpdateWorkspaceLlmConnectionRequest) => void;
}

/**
 * Create or update a workspace-owned OpenAI-compatible connection.
 *
 * The body is a separate component keyed on the edited connection, so switching which connection is
 * edited remounts it with fresh initial state instead of copying props into state from an effect.
 */
export function WorkspaceLlmConnectionFormDialog({
	open,
	onOpenChange,
	editing,
	...contentProps
}: WorkspaceLlmConnectionFormDialogProps) {
	return (
		<Dialog open={open} onOpenChange={onOpenChange}>
			{open && (
				<WorkspaceLlmConnectionFormDialogContent
					key={editing?.id ?? "new"}
					editing={editing}
					{...contentProps}
				/>
			)}
		</Dialog>
	);
}

type WorkspaceLlmConnectionFormDialogContentProps = Omit<
	WorkspaceLlmConnectionFormDialogProps,
	"open" | "onOpenChange"
>;

function WorkspaceLlmConnectionFormDialogContent({
	editing,
	isSubmitting,
	onCreate,
	onUpdate,
}: WorkspaceLlmConnectionFormDialogContentProps) {
	const isEdit = editing !== null;
	const [displayName, setDisplayName] = useState(editing?.displayName ?? "");
	const [baseUrl, setBaseUrl] = useState(editing?.baseUrl ?? baseUrlDefaultFor("OPENAI"));
	const [preset, setPreset] = useState<ProviderPreset>(
		editing ? presetForConnection(editing) : "OPENAI",
	);
	const [useResponsesApi, setUseResponsesApi] = useState(
		editing ? usesResponsesApi(editing.apiProtocol) : false,
	);
	const [authMode, setAuthMode] = useState<LlmAuthMode>(editing?.authMode ?? "BEARER");
	const [apiKey, setApiKey] = useState("");
	const [clearApiKey, setClearApiKey] = useState(false);
	const [enabled, setEnabled] = useState(editing?.enabled ?? false);
	const [errors, setErrors] = useState<FieldErrors<LlmConnectionFormField>>({});

	const apiProtocol = defaultProtocolFor(useResponsesApi);

	const validate = (): boolean => {
		// Immutable once connected, so an edit neither sends a base URL nor validates one.
		const next = validateLlmConnectionForm({ displayName, baseUrl: isEdit ? undefined : baseUrl });
		setErrors(next);
		return Object.keys(next).length === 0;
	};

	// `useId()` rather than hand-spelled ids, the house rule `BudgetAmountDialog` set: the instance
	// twin of this dialog spells out the same field names, and two forms cannot own one id.
	const displayNameId = useId();
	const presetId = useId();
	const responsesApiId = useId();
	const baseUrlId = useId();
	const authModeId = useId();
	const apiKeyId = useId();
	const clearApiKeyId = useId();
	const enabledId = useId();
	const displayNameErrorId = useId();
	const baseUrlErrorId = useId();

	const handleSubmit = (event: React.FormEvent) => {
		event.preventDefault();
		if (!validate()) return;

		if (editing) {
			const body: UpdateWorkspaceLlmConnectionRequest = {
				displayName: displayName.trim(),
				enabled,
			};
			if (apiKey.trim()) body.apiKey = apiKey.trim();
			if (clearApiKey) body.clearApiKey = true;
			onUpdate(editing.id, body);
			return;
		}

		onCreate({
			displayName: displayName.trim(),
			baseUrl: baseUrl.trim(),
			apiProtocol,
			authMode,
			apiKey: apiKey.trim() || undefined,
			enabled: false,
		});
	};

	return (
		<DialogContent className="sm:max-w-lg">
			{/* `contents`: the form has to wrap header, body and footer so submit works, but it must not
			    become a layout box between them — the popup's own column is what pins the header and the
			    footer while `DialogBody` scrolls. */}
			<form onSubmit={handleSubmit} className="contents" noValidate>
				<DialogHeader>
					<DialogTitle>{isEdit ? "Edit connection" : "Add connection"}</DialogTitle>
					<DialogDescription>
						Connect an endpoint that implements an OpenAI API. Add and price its models next.
					</DialogDescription>
				</DialogHeader>

				{/* This form is ~700 px tall — taller than a phone in portrait and far taller than one in
				    landscape. It scrolls here so the submit button is always on screen. */}
				<DialogBody className="space-y-4 py-1">
					<Field data-invalid={Boolean(errors.displayName)}>
						<FieldLabel htmlFor={displayNameId}>Display name</FieldLabel>
						<Input
							id={displayNameId}
							value={displayName}
							onChange={(event) => setDisplayName(event.target.value)}
							placeholder="e.g. Production OpenAI"
							required
							aria-invalid={Boolean(errors.displayName)}
							// Announces *why* the field is invalid on the way back to it, which `aria-invalid`
							// alone never does (WCAG SC 3.3.1).
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
									value={preset}
									onValueChange={(value) => {
										if (!value) return;
										const next = value as ProviderPreset;
										if (!baseUrl || baseUrl === baseUrlDefaultFor(preset)) {
											setBaseUrl(baseUrlDefaultFor(next));
										}
										setAuthMode(authModeDefaultFor(next));
										setPreset(next);
									}}
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
								{preset === "AZURE_OPENAI_V1" && (
									<FieldDescription>
										Replace RESOURCE below with your Azure resource name. The v1 API does not need
										an api-version parameter.
									</FieldDescription>
								)}
							</Field>

							<Field orientation="horizontal">
								<Checkbox
									id={responsesApiId}
									checked={useResponsesApi}
									onCheckedChange={(checked) => setUseResponsesApi(checked === true)}
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
							value={baseUrl}
							onChange={(event) => setBaseUrl(event.target.value)}
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

					{!isEdit && preset === "OTHER" && (
						<Field>
							<FieldLabel htmlFor={authModeId}>Authentication</FieldLabel>
							<Select
								items={[
									{ value: "BEARER", label: "Bearer token" },
									{ value: "API_KEY", label: "api-key header" },
								]}
								value={authMode}
								onValueChange={(value) => value && setAuthMode(value as LlmAuthMode)}
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
								value={apiKey}
								onChange={(event) => setApiKey(event.target.value)}
								disabled={clearApiKey}
								placeholder={
									editing?.hasApiKey
										? `Configured · ends in ····${editing.apiKeyLast4 ?? "····"}`
										: "Enter API key"
								}
								autoComplete="off"
							/>
							<FieldDescription>
								{editing?.hasApiKey ? "Leave blank to keep the current key." : "Stored encrypted."}
							</FieldDescription>
						</Field>

						{editing?.hasApiKey && (
							<Field orientation="horizontal">
								<Checkbox
									id={clearApiKeyId}
									checked={clearApiKey}
									onCheckedChange={(checked) => {
										setClearApiKey(checked === true);
										if (checked === true) setApiKey("");
									}}
								/>
								<FieldContent>
									<FieldLabel htmlFor={clearApiKeyId} className="font-normal">
										Remove stored API key
									</FieldLabel>
								</FieldContent>
							</Field>
						)}
					</FieldGroup>

					<Field orientation="horizontal">
						<FieldContent>
							<FieldLabel htmlFor={enabledId}>Active</FieldLabel>
							<FieldDescription>
								{isEdit
									? "Turn off to stop new requests using this connection."
									: "Starts inactive. Test it, add a priced model, then activate both."}
							</FieldDescription>
						</FieldContent>
						<Switch
							id={enabledId}
							checked={enabled}
							disabled={!isEdit}
							onCheckedChange={setEnabled}
						/>
					</Field>
					{editing?.enabled && !enabled && (
						<Alert variant="warning">
							<AlertTriangle aria-hidden />
							<AlertTitle>All workspace models will stop immediately</AlertTitle>
							<AlertDescription>
								Practice detection and the mentor can't run until you reactivate this provider or
								pick another model.
							</AlertDescription>
						</Alert>
					)}
				</DialogBody>
				<DialogFooter>
					<DialogClose render={<Button type="button" variant="outline" />}>Cancel</DialogClose>
					<Button type="submit" disabled={isSubmitting}>
						{isEdit ? "Save changes" : "Connect provider"}
					</Button>
				</DialogFooter>
			</form>
		</DialogContent>
	);
}

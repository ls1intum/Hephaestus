import { AlertTriangle } from "lucide-react";
import { useState } from "react";
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
} from "@/lib/llmProviderType";

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
	const [errors, setErrors] = useState<{ displayName?: string; baseUrl?: string }>({});

	const apiProtocol = defaultProtocolFor(useResponsesApi);

	const validate = (): boolean => {
		const next: typeof errors = {};
		if (!displayName.trim()) next.displayName = "A display name is required.";
		if (!isEdit && !baseUrl.trim()) next.baseUrl = "A base URL is required.";
		setErrors(next);
		return Object.keys(next).length === 0;
	};

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
			<form onSubmit={handleSubmit} className="space-y-4" noValidate>
				<DialogHeader>
					<DialogTitle>{isEdit ? "Edit connection" : "Add connection"}</DialogTitle>
					<DialogDescription>
						Connect an endpoint that implements an OpenAI API. Models are added and priced after the
						connection is saved.
					</DialogDescription>
				</DialogHeader>

				<Field data-invalid={Boolean(errors.displayName)}>
					<FieldLabel htmlFor="llm-conn-display-name">Display name</FieldLabel>
					<Input
						id="llm-conn-display-name"
						value={displayName}
						onChange={(event) => setDisplayName(event.target.value)}
						placeholder="e.g. Production OpenAI"
						aria-invalid={Boolean(errors.displayName)}
					/>
					{errors.displayName && <FieldError>{errors.displayName}</FieldError>}
				</Field>

				{!isEdit && (
					<FieldGroup className="gap-3">
						<Field>
							<FieldLabel htmlFor="llm-conn-provider-preset">Endpoint preset</FieldLabel>
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
								<SelectTrigger id="llm-conn-provider-preset" className="w-full">
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
									Replace RESOURCE below with your Azure resource name. The v1 API does not need an
									api-version parameter.
								</FieldDescription>
							)}
						</Field>

						<Field orientation="horizontal">
							<Checkbox
								id="llm-conn-responses-api"
								checked={useResponsesApi}
								onCheckedChange={(checked) => setUseResponsesApi(checked === true)}
							/>
							<FieldContent>
								<FieldLabel htmlFor="llm-conn-responses-api" className="font-normal">
									Use the Responses API instead of Chat Completions
								</FieldLabel>
							</FieldContent>
						</Field>
					</FieldGroup>
				)}

				<Field data-invalid={Boolean(errors.baseUrl)}>
					<FieldLabel htmlFor="llm-conn-base-url">Base URL</FieldLabel>
					<Input
						id="llm-conn-base-url"
						type="url"
						value={baseUrl}
						onChange={(event) => setBaseUrl(event.target.value)}
						disabled={isEdit}
						placeholder="https://api.openai.com/v1"
						aria-invalid={Boolean(errors.baseUrl)}
					/>
					{isEdit && (
						<FieldDescription>
							Endpoint, API shape, and authentication are immutable. Add a connection to change
							them.
						</FieldDescription>
					)}
					{errors.baseUrl && <FieldError>{errors.baseUrl}</FieldError>}
				</Field>

				{!isEdit && preset === "OTHER" && (
					<Field>
						<FieldLabel htmlFor="llm-conn-auth-mode">Authentication</FieldLabel>
						<Select
							items={[
								{ value: "BEARER", label: "Bearer token" },
								{ value: "API_KEY", label: "api-key header" },
							]}
							value={authMode}
							onValueChange={(value) => value && setAuthMode(value as LlmAuthMode)}
						>
							<SelectTrigger id="llm-conn-auth-mode" className="w-full">
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
						<FieldLabel htmlFor="llm-conn-api-key">API key</FieldLabel>
						<Input
							id="llm-conn-api-key"
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
								id="llm-conn-clear-api-key"
								checked={clearApiKey}
								onCheckedChange={(checked) => {
									setClearApiKey(checked === true);
									if (checked === true) setApiKey("");
								}}
							/>
							<FieldContent>
								<FieldLabel htmlFor="llm-conn-clear-api-key" className="font-normal">
									Remove stored API key
								</FieldLabel>
							</FieldContent>
						</Field>
					)}
				</FieldGroup>

				<Field orientation="horizontal">
					<FieldContent>
						<FieldLabel htmlFor="llm-conn-enabled">Active</FieldLabel>
						<FieldDescription>
							{isEdit
								? "Turn off to stop new requests using this connection."
								: "New connections start inactive. Save and test this connection, add a priced model, then activate both."}
						</FieldDescription>
					</FieldContent>
					<Switch
						id="llm-conn-enabled"
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
							Existing Practice and Mentor configurations using this provider cannot run until the
							connection is reactivated or another model is selected.
						</AlertDescription>
					</Alert>
				)}
				<DialogFooter>
					<DialogClose render={<Button type="button" variant="outline" />}>Cancel</DialogClose>
					<Button type="submit" disabled={isSubmitting}>
						{isEdit ? "Save changes" : "Connect inactive provider"}
					</Button>
				</DialogFooter>
			</form>
		</DialogContent>
	);
}

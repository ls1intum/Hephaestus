import { useEffect, useRef, useState } from "react";
import type {
	CreateLlmConnectionRequest,
	LlmConnection,
	LlmProbeResult,
	UpdateLlmConnectionRequest,
} from "@/api/types.gen";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
	Dialog,
	DialogBody,
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
	defaultProtocolFor,
	type LlmAuthMode,
	PROVIDER_PRESET_LABELS,
	PROVIDER_PRESET_ORDER,
	PROVIDER_PRESET_SELECT_ITEMS,
	type ProviderPreset,
	presetForConnection,
	usesResponsesApi,
} from "@/lib/llm-provider-type";

export interface AdminLlmConnectionFormDialogProps {
	open: boolean;
	onOpenChange: (open: boolean) => void;
	editing: LlmConnection | null;
	isSubmitting: boolean;
	onCreate: (body: CreateLlmConnectionRequest) => void;
	onUpdate: (id: number, body: UpdateLlmConnectionRequest) => void;
	onProbe: (
		request: {
			apiProtocol: string;
			baseUrl: string;
			apiKey?: string;
			authMode?: LlmAuthMode;
		},
		callbacks: { onSuccess: (result: LlmProbeResult) => void; onError: (message: string) => void },
	) => void;
	onProbeSaved?: (
		id: number,
		callbacks: { onSuccess: (result: LlmProbeResult) => void; onError: (message: string) => void },
	) => void;
	isProbing: boolean;
	onProbed?: (models: string[]) => void;
}

/**
 * Create or update an instance OpenAI-compatible connection.
 *
 * The body is a separate component keyed on the edited connection, so switching which connection is
 * edited remounts it with fresh initial state instead of copying props into state from an effect —
 * the same seeding rule as the workspace-scoped form. An effect would leave a window, between the
 * prop change and the effect running, in which the dialog shows the *previous* connection's secrets
 * and endpoint under the new connection's title.
 */
export function AdminLlmConnectionFormDialog({
	open,
	onOpenChange,
	editing,
	...contentProps
}: AdminLlmConnectionFormDialogProps) {
	return (
		<Dialog open={open} onOpenChange={onOpenChange}>
			{open && (
				<AdminLlmConnectionFormDialogContent
					key={editing?.id ?? "new"}
					editing={editing}
					onOpenChange={onOpenChange}
					{...contentProps}
				/>
			)}
		</Dialog>
	);
}

type AdminLlmConnectionFormDialogContentProps = Omit<AdminLlmConnectionFormDialogProps, "open">;

function AdminLlmConnectionFormDialogContent({
	onOpenChange,
	editing,
	isSubmitting,
	onCreate,
	onUpdate,
	onProbe,
	onProbeSaved,
	isProbing,
	onProbed,
}: AdminLlmConnectionFormDialogContentProps) {
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
	const [probeResult, setProbeResult] = useState<LlmProbeResult | null>(null);
	const [probeError, setProbeError] = useState<string | null>(null);
	const [errors, setErrors] = useState<FieldErrors<LlmConnectionFormField>>({});
	// Invalidates an in-flight probe whose inputs have since changed, so a slow answer about the old
	// endpoint cannot land as if it were about the new one.
	const probeGeneration = useRef(0);
	// A probe answer that arrives after the dialog closed must not reach `onProbed`, which would seed
	// the model form's datalist from a discovery the admin walked away from. Closing unmounts this
	// component, so "still open" is exactly "still mounted".
	const isMounted = useRef(true);
	useEffect(() => {
		isMounted.current = true;
		return () => {
			isMounted.current = false;
		};
	}, []);

	const apiProtocol = defaultProtocolFor(useResponsesApi);
	const clearProbe = () => {
		probeGeneration.current += 1;
		setProbeResult(null);
		setProbeError(null);
		onProbed?.([]);
	};

	// The shared rules, not a presence check of this form's own: `noValidate` makes `type="url"`
	// inert, and an endpoint carrying a credential or a query string is rejected by the server
	// whichever console pasted it.
	const validate = (): boolean => {
		const next = validateLlmConnectionForm({
			displayName,
			// The endpoint is immutable, so an edit neither sends nor validates one.
			...(isEdit ? {} : { baseUrl }),
		});
		setErrors(next);
		return Object.keys(next).length === 0;
	};

	const handleTest = () => {
		const generation = probeGeneration.current + 1;
		probeGeneration.current = generation;
		setProbeResult(null);
		setProbeError(null);
		onProbed?.([]);
		const callbacks = {
			onSuccess: (result: LlmProbeResult) => {
				if (probeGeneration.current !== generation || !isMounted.current) return;
				setProbeResult(result);
				if (result.reachable) onProbed?.(result.models);
			},
			onError: (message: string) => {
				if (probeGeneration.current === generation && isMounted.current) setProbeError(message);
			},
		};
		if (editing && !apiKey.trim() && !clearApiKey) {
			onProbeSaved?.(editing.id, callbacks);
			return;
		}
		onProbe(
			{
				apiProtocol,
				baseUrl: baseUrl.trim(),
				apiKey: apiKey.trim() || undefined,
				authMode,
			},
			callbacks,
		);
	};

	const handleSubmit = (event: React.FormEvent) => {
		event.preventDefault();
		if (!validate()) return;

		if (editing) {
			const body: UpdateLlmConnectionRequest = { displayName: displayName.trim() };
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
			{/* `contents`, so the header/body/footer are the popup's own flex children: a `<form>` with a
			    layout box of its own would defeat the pinned-header/scrolling-body column. */}
			<form onSubmit={handleSubmit} className="contents" noValidate>
				<DialogHeader>
					<DialogTitle>{isEdit ? "Edit connection" : "Add connection"}</DialogTitle>
					<DialogDescription>
						Connect an endpoint that implements an OpenAI API. Models are added and priced after the
						connection is saved.
					</DialogDescription>
				</DialogHeader>

				{/* Preset, endpoint, auth mode, credential and the probe result run to ~900 px, which
				    overflows every phone in both directions; only the body scrolls. */}
				<DialogBody className="space-y-4 py-1">
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
									clearProbe();
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
							<Field orientation="horizontal" className="mt-2">
								<Checkbox
									id="llm-conn-responses-api"
									checked={useResponsesApi}
									onCheckedChange={(checked) => {
										setUseResponsesApi(checked === true);
										clearProbe();
									}}
								/>
								<FieldContent>
									<FieldLabel htmlFor="llm-conn-responses-api" className="font-normal">
										Use the Responses API instead of Chat Completions
									</FieldLabel>
								</FieldContent>
							</Field>
							{preset === "AZURE_OPENAI_V1" && (
								<FieldDescription>
									Replace RESOURCE below with your Azure resource name. The v1 API does not need an
									api-version parameter.
								</FieldDescription>
							)}
						</Field>
					)}

					<Field data-invalid={Boolean(errors.baseUrl)}>
						<FieldLabel htmlFor="llm-conn-base-url">Base URL</FieldLabel>
						<Input
							id="llm-conn-base-url"
							type="url"
							value={baseUrl}
							onChange={(event) => {
								setBaseUrl(event.target.value);
								clearProbe();
							}}
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
								onValueChange={(value) => {
									if (!value) return;
									setAuthMode(value as LlmAuthMode);
									clearProbe();
								}}
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

					<Field>
						<FieldLabel htmlFor="llm-conn-api-key">API key</FieldLabel>
						<Input
							id="llm-conn-api-key"
							type="password"
							value={apiKey}
							onChange={(event) => {
								setApiKey(event.target.value);
								clearProbe();
							}}
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
						{editing?.hasApiKey && (
							<Field orientation="horizontal">
								<Checkbox
									id="llm-conn-clear-api-key"
									checked={clearApiKey}
									onCheckedChange={(checked) => {
										setClearApiKey(checked === true);
										if (checked === true) setApiKey("");
										clearProbe();
									}}
								/>
								<FieldContent>
									<FieldLabel htmlFor="llm-conn-clear-api-key" className="font-normal">
										Remove stored API key
									</FieldLabel>
								</FieldContent>
							</Field>
						)}
					</Field>

					{!isEdit && (
						<p className="text-sm text-muted-foreground">
							New connections start inactive. Save and test the connection, add a priced model, then
							activate it from the connections table.
						</p>
					)}

					<div className="space-y-2">
						<Button
							type="button"
							variant="outline"
							size="sm"
							disabled={isProbing || !baseUrl.trim()}
							onClick={handleTest}
						>
							{isProbing
								? "Testing…"
								: isEdit && !apiKey.trim() && !clearApiKey
									? "Test saved connection"
									: isEdit
										? "Test changes"
										: "Test & fetch models"}
						</Button>
						{probeResult?.reachable && (
							<Alert variant="success">
								<AlertDescription>
									Reachable. Found {probeResult.models.length} model
									{probeResult.models.length === 1 ? "" : "s"}.
									{probeResult.models.length > 0 && (
										<div className="mt-1.5 flex flex-wrap gap-1">
											{probeResult.models.slice(0, 12).map((modelId) => (
												<Badge key={modelId} variant="outline" className="font-mono text-[10px]">
													{modelId}
												</Badge>
											))}
										</div>
									)}
								</AlertDescription>
							</Alert>
						)}
						{probeResult && !probeResult.reachable && (
							<Alert variant="warning">
								<AlertDescription>
									Discovery unsupported. {probeResult.message ?? "The provider didn't answer."} You
									can still save the connection and enter a model id.
								</AlertDescription>
							</Alert>
						)}
						{probeError && (
							<Alert variant="warning">
								<AlertDescription>
									Discovery unsupported. {probeError} You can still save the connection and enter a
									model id.
								</AlertDescription>
							</Alert>
						)}
					</div>
				</DialogBody>

				<DialogFooter>
					<Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
						Cancel
					</Button>
					<Button type="submit" disabled={isSubmitting}>
						{isEdit ? "Save changes" : "Save inactive connection"}
					</Button>
				</DialogFooter>
			</form>
		</DialogContent>
	);
}

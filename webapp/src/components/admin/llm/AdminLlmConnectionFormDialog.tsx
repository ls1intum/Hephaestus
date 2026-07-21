import { ChevronDown } from "lucide-react";
import { useEffect, useState } from "react";
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
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import {
	Dialog,
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
import { Switch } from "@/components/ui/switch";
import {
	authDefaultsForProtocol,
	defaultProtocolFor,
	PROVIDER_TYPE_LABELS,
	PROVIDER_TYPE_ORDER,
	PROVIDER_TYPE_SELECT_ITEMS,
	type ProviderTypeOption,
	providerTypeForProtocol,
	usesResponsesApi,
} from "@/lib/llmProviderType";

const SLUG_PATTERN = /^[a-z][a-z0-9-]{1,62}$/;

export interface AdminLlmConnectionFormDialogProps {
	open: boolean;
	onOpenChange: (open: boolean) => void;
	editing: LlmConnection | null;
	isSubmitting: boolean;
	onCreate: (body: CreateLlmConnectionRequest) => void;
	onUpdate: (id: number, body: UpdateLlmConnectionRequest) => void;
	/** Probes the in-progress draft (works before the connection is saved). */
	onProbe: (
		request: {
			apiProtocol: string;
			baseUrl: string;
			apiKey?: string;
			authHeaderName?: string;
			authValuePrefix?: string;
		},
		callbacks: { onSuccess: (result: LlmProbeResult) => void; onError: (message: string) => void },
	) => void;
	isProbing: boolean;
	/** Lifts a successful probe's model ids so the model-creation flow can offer them. */
	onProbed?: (models: string[]) => void;
}

/**
 * Create/edit an instance LLM provider connection (#1368). "Test & fetch models" never gates saving —
 * a provider that can't be introspected can still be connected and its models entered by hand.
 */
export function AdminLlmConnectionFormDialog({
	open,
	onOpenChange,
	editing,
	isSubmitting,
	onCreate,
	onUpdate,
	onProbe,
	isProbing,
	onProbed,
}: AdminLlmConnectionFormDialogProps) {
	const isEdit = editing !== null;
	const [displayName, setDisplayName] = useState("");
	const [slug, setSlug] = useState("");
	const [baseUrl, setBaseUrl] = useState("");
	const [providerType, setProviderType] = useState<ProviderTypeOption>("OPENAI");
	const [useResponsesApi, setUseResponsesApi] = useState(false);
	const [apiKey, setApiKey] = useState("");
	const [advancedOpen, setAdvancedOpen] = useState(false);
	const [authHeaderName, setAuthHeaderName] = useState("");
	const [authValuePrefix, setAuthValuePrefix] = useState("");
	const [enabled, setEnabled] = useState(true);
	const [probeResult, setProbeResult] = useState<LlmProbeResult | null>(null);
	const [probeError, setProbeError] = useState<string | null>(null);
	const [errors, setErrors] = useState<{ displayName?: string; slug?: string; baseUrl?: string }>(
		{},
	);

	useEffect(() => {
		if (!open) return;
		setDisplayName(editing?.displayName ?? "");
		setSlug(editing?.slug ?? "");
		setBaseUrl(editing?.baseUrl ?? "");
		const type = editing ? providerTypeForProtocol(editing.apiProtocol) : "OPENAI";
		setProviderType(type);
		setUseResponsesApi(editing ? usesResponsesApi(editing.apiProtocol) : false);
		setApiKey("");
		setAuthHeaderName(editing?.authHeaderName ?? "");
		setAuthValuePrefix(editing?.authValuePrefix ?? "");
		setAdvancedOpen(false);
		setEnabled(editing?.enabled ?? true);
		setProbeResult(null);
		setProbeError(null);
		setErrors({});
	}, [open, editing]);

	const apiProtocol = defaultProtocolFor(providerType, useResponsesApi);
	const authDefaults = authDefaultsForProtocol(apiProtocol);

	const validate = (): boolean => {
		const next: typeof errors = {};
		if (!displayName.trim()) next.displayName = "A display name is required.";
		if (!isEdit) {
			if (!SLUG_PATTERN.test(slug.trim())) {
				next.slug = "Lowercase letters, digits and hyphens; must start with a letter.";
			}
			if (!baseUrl.trim()) next.baseUrl = "A base URL is required.";
		}
		setErrors(next);
		return Object.keys(next).length === 0;
	};

	const handleTest = () => {
		setProbeResult(null);
		setProbeError(null);
		onProbe(
			{
				apiProtocol,
				baseUrl: baseUrl.trim(),
				apiKey: apiKey.trim() || undefined,
				authHeaderName: authHeaderName.trim() || undefined,
				authValuePrefix: authValuePrefix.trim() || undefined,
			},
			{
				onSuccess: (result) => {
					setProbeResult(result);
					if (result.reachable) onProbed?.(result.models);
				},
				onError: (message) => setProbeError(message),
			},
		);
	};

	const handleSubmit = (event: React.FormEvent) => {
		event.preventDefault();
		if (!validate()) return;

		if (isEdit && editing) {
			const body: UpdateLlmConnectionRequest = {
				displayName: displayName.trim(),
				baseUrl: baseUrl.trim() || undefined,
				apiProtocol,
				authHeaderName: authHeaderName.trim() || undefined,
				authValuePrefix: authValuePrefix.trim() || undefined,
				enabled,
			};
			if (apiKey.trim()) body.apiKey = apiKey.trim();
			onUpdate(editing.id, body);
			return;
		}

		onCreate({
			displayName: displayName.trim(),
			slug: slug.trim(),
			baseUrl: baseUrl.trim(),
			apiProtocol,
			apiKey: apiKey.trim() || undefined,
			authHeaderName: authHeaderName.trim() || undefined,
			authValuePrefix: authValuePrefix.trim() || undefined,
			enabled,
		});
	};

	return (
		<Dialog open={open} onOpenChange={onOpenChange}>
			<DialogContent className="sm:max-w-lg">
				<form onSubmit={handleSubmit} className="space-y-4" noValidate>
					<DialogHeader>
						<DialogTitle>{isEdit ? "Edit connection" : "Add connection"}</DialogTitle>
						<DialogDescription>
							An LLM provider connection. Models added under it can be shared with workspaces.
						</DialogDescription>
					</DialogHeader>

					<Field data-invalid={Boolean(errors.displayName)}>
						<FieldLabel htmlFor="llm-conn-display-name">Display name</FieldLabel>
						<Input
							id="llm-conn-display-name"
							value={displayName}
							onChange={(e) => setDisplayName(e.target.value)}
							placeholder="e.g. Azure EU"
							aria-invalid={Boolean(errors.displayName)}
						/>
						{errors.displayName && <FieldError>{errors.displayName}</FieldError>}
					</Field>

					{!isEdit && (
						<Field data-invalid={Boolean(errors.slug)}>
							<FieldLabel htmlFor="llm-conn-slug">Slug</FieldLabel>
							<Input
								id="llm-conn-slug"
								value={slug}
								onChange={(e) => setSlug(e.target.value)}
								placeholder="azure-eu"
								autoComplete="off"
								aria-invalid={Boolean(errors.slug)}
							/>
							<FieldDescription>Immutable once created.</FieldDescription>
							{errors.slug && <FieldError>{errors.slug}</FieldError>}
						</Field>
					)}

					<Field>
						<FieldLabel htmlFor="llm-conn-provider-type">Provider type</FieldLabel>
						<Select
							items={PROVIDER_TYPE_SELECT_ITEMS}
							value={providerType}
							onValueChange={(v) => {
								if (v) setProviderType(v as ProviderTypeOption);
							}}
						>
							<SelectTrigger id="llm-conn-provider-type" className="w-full">
								<SelectValue />
							</SelectTrigger>
							<SelectContent>
								{PROVIDER_TYPE_ORDER.map((type) => (
									<SelectItem key={type} value={type}>
										{PROVIDER_TYPE_LABELS[type]}
									</SelectItem>
								))}
							</SelectContent>
						</Select>
						{providerType === "OPENAI" && (
							<div className="mt-2 flex items-center gap-2 text-sm font-normal text-muted-foreground">
								<Checkbox
									id="llm-conn-responses-api"
									checked={useResponsesApi}
									onCheckedChange={(checked) => setUseResponsesApi(checked === true)}
								/>
								<label htmlFor="llm-conn-responses-api">
									Use the Responses API instead of Completions
								</label>
							</div>
						)}
					</Field>

					<Field data-invalid={Boolean(errors.baseUrl)}>
						<FieldLabel htmlFor="llm-conn-base-url">Base URL</FieldLabel>
						<Input
							id="llm-conn-base-url"
							type="url"
							value={baseUrl}
							onChange={(e) => setBaseUrl(e.target.value)}
							placeholder="https://api.openai.com/v1"
							aria-invalid={Boolean(errors.baseUrl)}
						/>
						{errors.baseUrl && <FieldError>{errors.baseUrl}</FieldError>}
					</Field>

					<Field>
						<FieldLabel htmlFor="llm-conn-api-key">API key</FieldLabel>
						<Input
							id="llm-conn-api-key"
							type="password"
							value={apiKey}
							onChange={(e) => setApiKey(e.target.value)}
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

					<Collapsible open={advancedOpen} onOpenChange={setAdvancedOpen}>
						<CollapsibleTrigger
							render={
								<Button type="button" variant="ghost" size="sm" className="gap-1 px-0">
									<ChevronDown
										className={`size-4 transition-transform ${advancedOpen ? "rotate-180" : ""}`}
										aria-hidden
									/>
									Advanced
								</Button>
							}
						/>
						<CollapsibleContent className="space-y-4 pt-2">
							<Field>
								<FieldLabel htmlFor="llm-conn-auth-header">Auth header name</FieldLabel>
								<Input
									id="llm-conn-auth-header"
									value={authHeaderName}
									onChange={(e) => setAuthHeaderName(e.target.value)}
									placeholder={authDefaults.headerName}
								/>
								<FieldDescription>
									Defaults to “{authDefaults.headerName}” for this provider type.
								</FieldDescription>
							</Field>
							<Field>
								<FieldLabel htmlFor="llm-conn-auth-prefix">Auth value prefix</FieldLabel>
								<Input
									id="llm-conn-auth-prefix"
									value={authValuePrefix}
									onChange={(e) => setAuthValuePrefix(e.target.value)}
									placeholder={authDefaults.valuePrefix || "(none)"}
								/>
							</Field>
						</CollapsibleContent>
					</Collapsible>

					<Field orientation="horizontal">
						<FieldContent>
							<FieldLabel htmlFor="llm-conn-enabled">Active</FieldLabel>
							<FieldDescription>Off — existing settings stop working</FieldDescription>
						</FieldContent>
						<Switch id="llm-conn-enabled" checked={enabled} onCheckedChange={setEnabled} />
					</Field>

					<div className="space-y-2">
						<Button
							type="button"
							variant="outline"
							size="sm"
							disabled={isProbing || !baseUrl.trim()}
							onClick={handleTest}
						>
							{isProbing ? "Testing…" : "Test & fetch models"}
						</Button>
						{probeResult?.reachable && (
							<Alert variant="success">
								<AlertDescription>
									Reachable — found {probeResult.models.length} model
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
									Discovery unsupported — {probeResult.message ?? "the provider didn't answer."} You
									can still save this connection and add models by hand.
								</AlertDescription>
							</Alert>
						)}
						{probeError && (
							<Alert variant="warning">
								<AlertDescription>
									Discovery unsupported — {probeError} You can still save this connection and add
									models by hand.
								</AlertDescription>
							</Alert>
						)}
					</div>

					<DialogFooter>
						<Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
							Cancel
						</Button>
						<Button type="submit" disabled={isSubmitting}>
							{isEdit ? "Save changes" : "Add connection"}
						</Button>
					</DialogFooter>
				</form>
			</DialogContent>
		</Dialog>
	);
}

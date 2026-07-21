import { useEffect, useState } from "react";
import type {
	CreateWorkspaceLlmConnectionRequest,
	UpdateWorkspaceLlmConnectionRequest,
	WorkspaceLlmConnection,
} from "@/api/types.gen";
import { Button } from "@/components/ui/button";
import {
	Dialog,
	DialogContent,
	DialogDescription,
	DialogFooter,
	DialogHeader,
	DialogTitle,
} from "@/components/ui/dialog";
import { Field, FieldDescription, FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from "@/components/ui/select";
import {
	defaultProtocolFor,
	PROVIDER_TYPE_LABELS,
	PROVIDER_TYPE_ORDER,
	PROVIDER_TYPE_SELECT_ITEMS,
	type ProviderTypeOption,
	providerTypeForProtocol,
} from "@/lib/llmProviderType";

const SLUG_PATTERN = /^[a-z][a-z0-9-]{1,62}$/;

export interface WorkspaceLlmConnectionFormDialogProps {
	open: boolean;
	onOpenChange: (open: boolean) => void;
	editing: WorkspaceLlmConnection | null;
	isSubmitting: boolean;
	onCreate: (body: CreateWorkspaceLlmConnectionRequest) => void;
	onUpdate: (id: number, body: UpdateWorkspaceLlmConnectionRequest) => void;
}

/**
 * Connect/edit your own AI provider (#1368 glossary copy moment #1): the key is stored encrypted and
 * used only in this workspace, billed directly to whoever owns it — never a shared provider.
 */
export function WorkspaceLlmConnectionFormDialog({
	open,
	onOpenChange,
	editing,
	isSubmitting,
	onCreate,
	onUpdate,
}: WorkspaceLlmConnectionFormDialogProps) {
	const isEdit = editing !== null;
	const [displayName, setDisplayName] = useState("");
	const [slug, setSlug] = useState("");
	const [baseUrl, setBaseUrl] = useState("");
	const [providerType, setProviderType] = useState<ProviderTypeOption>("OPENAI");
	const [apiKey, setApiKey] = useState("");
	const [errors, setErrors] = useState<{
		displayName?: string;
		slug?: string;
		baseUrl?: string;
		apiKey?: string;
	}>({});

	useEffect(() => {
		if (!open) return;
		setDisplayName(editing?.displayName ?? "");
		setSlug(editing?.slug ?? "");
		setBaseUrl(editing?.baseUrl ?? "");
		setProviderType(editing ? providerTypeForProtocol(editing.apiProtocol) : "OPENAI");
		setApiKey("");
		setErrors({});
	}, [open, editing]);

	const validate = (): boolean => {
		const next: typeof errors = {};
		if (!displayName.trim()) {
			next.displayName = "A display name is required.";
		}
		if (!isEdit) {
			if (!SLUG_PATTERN.test(slug.trim())) {
				next.slug = "Lowercase letters, digits and hyphens; must start with a letter.";
			}
			if (!baseUrl.trim()) {
				next.baseUrl = "A base URL is required.";
			}
		}
		setErrors(next);
		return Object.keys(next).length === 0;
	};

	const handleSubmit = (event: React.FormEvent) => {
		event.preventDefault();
		if (!validate()) return;

		if (isEdit && editing) {
			const body: UpdateWorkspaceLlmConnectionRequest = {
				displayName: displayName.trim(),
				baseUrl: baseUrl.trim() || undefined,
				apiProtocol: defaultProtocolFor(providerType),
			};
			if (apiKey.trim()) {
				body.apiKey = apiKey.trim();
			}
			onUpdate(editing.id, body);
			return;
		}

		onCreate({
			displayName: displayName.trim(),
			slug: slug.trim(),
			baseUrl: baseUrl.trim(),
			apiProtocol: defaultProtocolFor(providerType),
			apiKey: apiKey.trim() || undefined,
			enabled: true,
		});
	};

	return (
		<Dialog open={open} onOpenChange={onOpenChange}>
			<DialogContent className="sm:max-w-lg">
				{/* noValidate: this form validates itself so every rejection surfaces through `FieldError`,
				    rather than blocking submit silently via the browser's native constraint validation. */}
				<form onSubmit={handleSubmit} className="space-y-4" noValidate>
					<DialogHeader>
						<DialogTitle>
							{isEdit ? "Edit your AI provider" : "Connect your own AI provider"}
						</DialogTitle>
						<DialogDescription>
							Shared models are set up and paid for by your organization. You can also connect your
							own AI provider with your own API key — it's used only in this workspace and billed
							directly to you.
						</DialogDescription>
					</DialogHeader>

					<Field data-invalid={Boolean(errors.displayName)}>
						<FieldLabel htmlFor="wllm-display-name">Display name</FieldLabel>
						<Input
							id="wllm-display-name"
							value={displayName}
							onChange={(e) => setDisplayName(e.target.value)}
							placeholder="e.g. My OpenAI account"
							required
							aria-invalid={Boolean(errors.displayName)}
						/>
						{errors.displayName && <FieldError>{errors.displayName}</FieldError>}
					</Field>

					{!isEdit && (
						<Field data-invalid={Boolean(errors.slug)}>
							<FieldLabel htmlFor="wllm-slug">Slug</FieldLabel>
							<Input
								id="wllm-slug"
								value={slug}
								onChange={(e) => setSlug(e.target.value)}
								placeholder="my-openai"
								required
								autoComplete="off"
								aria-invalid={Boolean(errors.slug)}
							/>
							<FieldDescription>Immutable once created.</FieldDescription>
							{errors.slug && <FieldError>{errors.slug}</FieldError>}
						</Field>
					)}

					<Field>
						<FieldLabel htmlFor="wllm-provider-type">Provider type</FieldLabel>
						<Select
							items={PROVIDER_TYPE_SELECT_ITEMS}
							value={providerType}
							onValueChange={(v) => {
								if (v) setProviderType(v as ProviderTypeOption);
							}}
						>
							<SelectTrigger id="wllm-provider-type" className="w-full">
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
					</Field>

					<Field data-invalid={Boolean(errors.baseUrl)}>
						<FieldLabel htmlFor="wllm-base-url">Base URL</FieldLabel>
						<Input
							id="wllm-base-url"
							type="url"
							value={baseUrl}
							onChange={(e) => setBaseUrl(e.target.value)}
							placeholder="https://api.openai.com/v1"
							required={!isEdit}
							aria-invalid={Boolean(errors.baseUrl)}
						/>
						{errors.baseUrl && <FieldError>{errors.baseUrl}</FieldError>}
					</Field>

					<Field data-invalid={Boolean(errors.apiKey)}>
						<FieldLabel htmlFor="wllm-api-key">API key</FieldLabel>
						<Input
							id="wllm-api-key"
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
							{editing?.hasApiKey
								? "Leave blank to keep the current key."
								: "Stored encrypted; never shown again after saving."}
						</FieldDescription>
						{errors.apiKey && <FieldError>{errors.apiKey}</FieldError>}
					</Field>

					<DialogFooter>
						<Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
							Cancel
						</Button>
						<Button type="submit" disabled={isSubmitting}>
							{isEdit ? "Save changes" : "Connect"}
						</Button>
					</DialogFooter>
				</form>
			</DialogContent>
		</Dialog>
	);
}

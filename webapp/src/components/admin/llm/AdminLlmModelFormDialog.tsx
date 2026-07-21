import { useEffect, useState } from "react";
import type {
	CreateLlmModelRequest,
	LlmModel,
	UpdateLlmModelPriceRequest,
	UpdateLlmModelRequest,
	UpdateLlmModelSharingRequest,
} from "@/api/types.gen";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
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
import { PriceModeEditor, type PriceModeValue } from "./PriceModeEditor";
import { WorkspaceMultiSelect, type WorkspaceMultiSelectOption } from "./WorkspaceMultiSelect";

const SLUG_PATTERN = /^[a-z][a-z0-9-]{1,62}$/;

// Passed as `items` so the trigger can render the selected label before the popup has ever opened
// (Base UI Select otherwise has no label to show until the matching item has mounted once).
const SHARE_WITH_ITEMS = [
	{ value: "ALL", label: "All workspaces" },
	{ value: "SELECTED", label: "Selected workspaces" },
];

export interface AdminLlmModelSaveBody {
	metadata: CreateLlmModelRequest | UpdateLlmModelRequest;
	price: UpdateLlmModelPriceRequest;
	sharing: UpdateLlmModelSharingRequest;
}

export interface AdminLlmModelFormDialogProps {
	open: boolean;
	onOpenChange: (open: boolean) => void;
	editing: LlmModel | null;
	workspaceOptions: WorkspaceMultiSelectOption[];
	/** Model ids discovered by the connection's last successful probe, offered as a datalist. */
	probedModelIds: string[];
	isSubmitting: boolean;
	onSave: (body: AdminLlmModelSaveBody) => void;
}

/**
 * Create/edit an instance catalog model (#1368): metadata, price, and sharing are three separate
 * server calls (each with its own dedicated endpoint) but one guided form — the container sequences
 * them on save.
 */
export function AdminLlmModelFormDialog({
	open,
	onOpenChange,
	editing,
	workspaceOptions,
	probedModelIds,
	isSubmitting,
	onSave,
}: AdminLlmModelFormDialogProps) {
	const isEdit = editing !== null;
	const [displayName, setDisplayName] = useState("");
	const [slug, setSlug] = useState("");
	const [upstreamModelId, setUpstreamModelId] = useState("");
	const [contextWindow, setContextWindow] = useState("");
	const [maxOutputTokens, setMaxOutputTokens] = useState("");
	const [supportsReasoning, setSupportsReasoning] = useState(false);
	const [price, setPrice] = useState<PriceModeValue>({ pricingMode: "UNPRICED" });
	const [shareAll, setShareAll] = useState(true);
	const [sharedWorkspaceIds, setSharedWorkspaceIds] = useState<number[]>([]);
	const [errors, setErrors] = useState<{
		displayName?: string;
		slug?: string;
		upstreamModelId?: string;
		per1mInputUsd?: string;
		per1mOutputUsd?: string;
		note?: string;
	}>({});

	useEffect(() => {
		if (!open) return;
		setDisplayName(editing?.displayName ?? "");
		setSlug(editing?.slug ?? "");
		setUpstreamModelId(editing?.upstreamModelId ?? "");
		setContextWindow(editing?.contextWindow != null ? String(editing.contextWindow) : "");
		setMaxOutputTokens(editing?.maxOutputTokens != null ? String(editing.maxOutputTokens) : "");
		setSupportsReasoning(editing?.supportsReasoning ?? false);
		setPrice({
			pricingMode: editing?.currentPrice?.pricingMode ?? "UNPRICED",
			per1mInputUsd: editing?.currentPrice?.per1mInputUsd,
			per1mOutputUsd: editing?.currentPrice?.per1mOutputUsd,
			per1mCacheReadUsd: editing?.currentPrice?.per1mCacheReadUsd,
			per1mCacheWriteUsd: editing?.currentPrice?.per1mCacheWriteUsd,
			per1mReasoningUsd: editing?.currentPrice?.per1mReasoningUsd,
			note: editing?.currentPrice?.note,
		});
		setShareAll(editing ? editing.visibility === "PUBLIC" : true);
		setSharedWorkspaceIds(editing?.grantedWorkspaceIds ?? []);
		setErrors({});
	}, [open, editing]);

	const validate = (): boolean => {
		const next: typeof errors = {};
		if (!displayName.trim()) next.displayName = "A display name is required.";
		if (!isEdit && !SLUG_PATTERN.test(slug.trim())) {
			next.slug = "Lowercase letters, digits and hyphens; must start with a letter.";
		}
		if (!upstreamModelId.trim()) next.upstreamModelId = "The upstream model id is required.";
		if (price.pricingMode === "PRICED") {
			if (price.per1mInputUsd == null) next.per1mInputUsd = "Required when the model has a price.";
			if (price.per1mOutputUsd == null)
				next.per1mOutputUsd = "Required when the model has a price.";
		}
		if (price.pricingMode === "FREE" && !price.note?.trim()) {
			next.note = "A note is required when the model is free.";
		}
		setErrors(next);
		return Object.keys(next).length === 0;
	};

	const handleSubmit = (event: React.FormEvent) => {
		event.preventDefault();
		if (!validate()) return;

		const metadataShared = {
			displayName: displayName.trim(),
			upstreamModelId: upstreamModelId.trim(),
			contextWindow: contextWindow.trim() ? Number(contextWindow) : undefined,
			maxOutputTokens: maxOutputTokens.trim() ? Number(maxOutputTokens) : undefined,
			supportsReasoning,
		};
		const metadata: CreateLlmModelRequest | UpdateLlmModelRequest = isEdit
			? metadataShared
			: { ...metadataShared, slug: slug.trim() };

		const priceBody: UpdateLlmModelPriceRequest = {
			pricingMode: price.pricingMode,
			per1mInputUsd: price.pricingMode === "PRICED" ? price.per1mInputUsd : undefined,
			per1mOutputUsd: price.pricingMode === "PRICED" ? price.per1mOutputUsd : undefined,
			per1mCacheReadUsd: price.pricingMode === "PRICED" ? price.per1mCacheReadUsd : undefined,
			per1mCacheWriteUsd: price.pricingMode === "PRICED" ? price.per1mCacheWriteUsd : undefined,
			per1mReasoningUsd: price.pricingMode === "PRICED" ? price.per1mReasoningUsd : undefined,
			note: price.pricingMode === "FREE" ? price.note?.trim() : undefined,
		};

		const sharingBody: UpdateLlmModelSharingRequest = shareAll
			? { visibility: "PUBLIC" }
			: { visibility: "GRANTED", workspaceIds: sharedWorkspaceIds };

		onSave({ metadata, price: priceBody, sharing: sharingBody });
	};

	return (
		<Dialog open={open} onOpenChange={onOpenChange}>
			<DialogContent className="sm:max-w-lg">
				<form onSubmit={handleSubmit} className="space-y-4" noValidate>
					<DialogHeader>
						<DialogTitle>{isEdit ? "Edit model" : "Add model"}</DialogTitle>
						<DialogDescription>
							Give the model a name workspaces will recognize — the upstream id is never shown to
							them.
						</DialogDescription>
					</DialogHeader>

					<Field data-invalid={Boolean(errors.displayName)}>
						<FieldLabel htmlFor="llm-model-display-name">Display name</FieldLabel>
						<Input
							id="llm-model-display-name"
							value={displayName}
							onChange={(e) => setDisplayName(e.target.value)}
							placeholder="e.g. GPT-5 (Azure, EU)"
							aria-invalid={Boolean(errors.displayName)}
						/>
						{errors.displayName && <FieldError>{errors.displayName}</FieldError>}
					</Field>

					{!isEdit && (
						<Field data-invalid={Boolean(errors.slug)}>
							<FieldLabel htmlFor="llm-model-slug">Slug</FieldLabel>
							<Input
								id="llm-model-slug"
								value={slug}
								onChange={(e) => setSlug(e.target.value)}
								placeholder="gpt-5-eu"
								autoComplete="off"
								aria-invalid={Boolean(errors.slug)}
							/>
							{errors.slug && <FieldError>{errors.slug}</FieldError>}
						</Field>
					)}

					<Field data-invalid={Boolean(errors.upstreamModelId)}>
						<FieldLabel htmlFor="llm-model-upstream-id">Upstream model id</FieldLabel>
						<Input
							id="llm-model-upstream-id"
							value={upstreamModelId}
							onChange={(e) => setUpstreamModelId(e.target.value)}
							placeholder="e.g. openai/gpt-5"
							autoComplete="off"
							list="llm-model-upstream-id-options"
							aria-invalid={Boolean(errors.upstreamModelId)}
						/>
						{probedModelIds.length > 0 && (
							<datalist id="llm-model-upstream-id-options">
								{probedModelIds.map((id) => (
									<option key={id} value={id} />
								))}
							</datalist>
						)}
						<FieldDescription>
							The exact id the provider expects. Slashes are part of the id.
						</FieldDescription>
						{errors.upstreamModelId && <FieldError>{errors.upstreamModelId}</FieldError>}
					</Field>

					<div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
						<Field>
							<FieldLabel htmlFor="llm-model-context-window">
								Context window <span className="font-normal text-muted-foreground">(optional)</span>
							</FieldLabel>
							<Input
								id="llm-model-context-window"
								type="number"
								min={0}
								value={contextWindow}
								onChange={(e) => setContextWindow(e.target.value)}
							/>
						</Field>
						<Field>
							<FieldLabel htmlFor="llm-model-max-output">
								Max output tokens{" "}
								<span className="font-normal text-muted-foreground">(optional)</span>
							</FieldLabel>
							<Input
								id="llm-model-max-output"
								type="number"
								min={0}
								value={maxOutputTokens}
								onChange={(e) => setMaxOutputTokens(e.target.value)}
							/>
						</Field>
					</div>

					<div className="flex items-center gap-2 text-sm">
						<Checkbox
							id="llm-model-supports-reasoning"
							checked={supportsReasoning}
							onCheckedChange={(checked) => setSupportsReasoning(checked === true)}
						/>
						<label htmlFor="llm-model-supports-reasoning">Supports a reasoning mode</label>
					</div>

					<PriceModeEditor
						audience="instance"
						idPrefix="llm-model-price"
						value={price}
						onChange={setPrice}
						errors={errors}
					/>

					<Field>
						<FieldLabel htmlFor="llm-model-share-with">Share with</FieldLabel>
						<Select
							items={SHARE_WITH_ITEMS}
							value={shareAll ? "ALL" : "SELECTED"}
							onValueChange={(v) => {
								if (v) setShareAll(v === "ALL");
							}}
						>
							<SelectTrigger id="llm-model-share-with" className="w-full">
								<SelectValue />
							</SelectTrigger>
							<SelectContent>
								<SelectItem value="ALL">All workspaces</SelectItem>
								<SelectItem value="SELECTED">Selected workspaces</SelectItem>
							</SelectContent>
						</Select>
						{!shareAll && (
							<WorkspaceMultiSelect
								id="llm-model-share-workspaces"
								className="mt-2"
								options={workspaceOptions}
								selectedIds={sharedWorkspaceIds}
								onChange={setSharedWorkspaceIds}
							/>
						)}
					</Field>

					<DialogFooter>
						<Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
							Cancel
						</Button>
						<Button type="submit" disabled={isSubmitting}>
							{isEdit ? "Save changes" : "Add model"}
						</Button>
					</DialogFooter>
				</form>
			</DialogContent>
		</Dialog>
	);
}

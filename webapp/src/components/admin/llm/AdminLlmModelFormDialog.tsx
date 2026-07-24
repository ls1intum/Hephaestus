import { AlertTriangle } from "lucide-react";
import { useEffect, useState } from "react";
import type {
	CreateLlmModelRequest,
	LlmModel,
	UpdateLlmModelPriceRequest,
	UpdateLlmModelRequest,
	UpdateLlmModelSharingRequest,
} from "@/api/types.gen";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
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
import type { AdminLlmModelSaveBody } from "@/lib/adminLlmModelSave";
import { PriceModeEditor, type PriceModeValue } from "./PriceModeEditor";
import { WorkspaceMultiSelect, type WorkspaceMultiSelectOption } from "./WorkspaceMultiSelect";

// Passed as `items` so the trigger can render the selected label before the popup has ever opened
// (Base UI Select otherwise has no label to show until the matching item has mounted once).
const SHARE_WITH_ITEMS = [
	{ value: "ALL", label: "All workspaces" },
	{ value: "SELECTED", label: "Selected workspaces" },
];

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
 * Create/edit an instance catalog model (#1368). Creation includes initial access; later access
 * changes use the dedicated access dialog so their immediate impact cannot be bypassed.
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
	const [upstreamModelId, setUpstreamModelId] = useState("");
	const [contextWindow, setContextWindow] = useState("");
	const [maxOutputTokens, setMaxOutputTokens] = useState("");
	const [supportsReasoning, setSupportsReasoning] = useState(false);
	const [enabled, setEnabled] = useState(false);
	const [price, setPrice] = useState<PriceModeValue>({ pricingMode: "UNPRICED" });
	const [shareAll, setShareAll] = useState(false);
	const [sharedWorkspaceIds, setSharedWorkspaceIds] = useState<number[]>([]);
	const [errors, setErrors] = useState<{
		displayName?: string;
		upstreamModelId?: string;
		per1mInputUsd?: string;
		per1mOutputUsd?: string;
		note?: string;
	}>({});

	useEffect(() => {
		if (!open) return;
		setDisplayName(editing?.displayName ?? "");
		setUpstreamModelId(editing?.upstreamModelId ?? "");
		setContextWindow(editing?.contextWindow != null ? String(editing.contextWindow) : "");
		setMaxOutputTokens(editing?.maxOutputTokens != null ? String(editing.maxOutputTokens) : "");
		setSupportsReasoning(editing?.supportsReasoning ?? false);
		setEnabled(editing?.enabled ?? false);
		setPrice({
			pricingMode: editing?.currentPrice?.pricingMode ?? "UNPRICED",
			per1mInputUsd: editing?.currentPrice?.per1mInputUsd,
			per1mOutputUsd: editing?.currentPrice?.per1mOutputUsd,
			per1mCacheReadUsd: editing?.currentPrice?.per1mCacheReadUsd,
			per1mCacheWriteUsd: editing?.currentPrice?.per1mCacheWriteUsd,
			note: editing?.currentPrice?.note,
		});
		setShareAll(editing ? editing.visibility === "PUBLIC" : false);
		setSharedWorkspaceIds(editing?.grantedWorkspaceIds ?? []);
		setErrors({});
	}, [open, editing]);

	const validate = (): boolean => {
		const next: typeof errors = {};
		if (!displayName.trim()) next.displayName = "A display name is required.";
		if (!upstreamModelId.trim()) next.upstreamModelId = "The upstream model id is required.";
		if (price.pricingMode === "PRICED") {
			if (price.per1mInputUsd == null) next.per1mInputUsd = "Required when the model has a price.";
			if (price.per1mOutputUsd == null)
				next.per1mOutputUsd = "Required when the model has a price.";
		}
		if (price.pricingMode === "NO_CHARGE" && !price.note?.trim()) {
			next.note = "Explain why no metered API rate applies.";
		}
		setErrors(next);
		return Object.keys(next).length === 0;
	};

	const handleSubmit = (event: React.FormEvent) => {
		event.preventDefault();
		if (!validate()) return;

		const metadataShared = {
			displayName: displayName.trim(),
			contextWindow: contextWindow.trim() ? Number(contextWindow) : undefined,
			maxOutputTokens: maxOutputTokens.trim() ? Number(maxOutputTokens) : undefined,
			supportsReasoning,
			enabled,
		};
		const metadata: CreateLlmModelRequest | UpdateLlmModelRequest = isEdit
			? metadataShared
			: { ...metadataShared, upstreamModelId: upstreamModelId.trim() };

		const priceBody: UpdateLlmModelPriceRequest = {
			pricingMode: price.pricingMode,
			per1mInputUsd: price.pricingMode === "PRICED" ? price.per1mInputUsd : undefined,
			per1mOutputUsd: price.pricingMode === "PRICED" ? price.per1mOutputUsd : undefined,
			per1mCacheReadUsd: price.pricingMode === "PRICED" ? price.per1mCacheReadUsd : undefined,
			per1mCacheWriteUsd: price.pricingMode === "PRICED" ? price.per1mCacheWriteUsd : undefined,
			note: price.pricingMode === "NO_CHARGE" ? price.note?.trim() : undefined,
		};

		const sharingBody: UpdateLlmModelSharingRequest | undefined = isEdit
			? undefined
			: shareAll
				? { visibility: "PUBLIC" }
				: { visibility: "GRANTED", workspaceIds: sharedWorkspaceIds };

		onSave({ metadata, price: priceBody, ...(sharingBody ? { sharing: sharingBody } : {}) });
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
							placeholder="e.g. GPT-5"
							aria-invalid={Boolean(errors.displayName)}
						/>
						{errors.displayName && <FieldError>{errors.displayName}</FieldError>}
					</Field>

					<Field data-invalid={Boolean(errors.upstreamModelId)}>
						<FieldLabel htmlFor="llm-model-upstream-id">Upstream model id</FieldLabel>
						<Input
							id="llm-model-upstream-id"
							value={upstreamModelId}
							onChange={(e) => setUpstreamModelId(e.target.value)}
							disabled={isEdit}
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
							{isEdit
								? "Create a new model to use a different upstream id."
								: "The exact id the provider expects. Slashes are part of the id."}
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

					<Field orientation="horizontal">
						<FieldContent>
							<FieldLabel htmlFor="llm-model-enabled">Active</FieldLabel>
							<FieldDescription>
								{isEdit
									? "Only active models can be selected for new workspace requests."
									: "New models are saved inactive. Review the saved price and sharing before activating."}
							</FieldDescription>
						</FieldContent>
						<Switch
							id="llm-model-enabled"
							checked={enabled}
							disabled={!isEdit || price.pricingMode === "UNPRICED"}
							onCheckedChange={setEnabled}
						/>
					</Field>

					{editing?.enabled && !enabled && (
						<Alert variant="warning">
							<AlertTriangle aria-hidden />
							<AlertTitle>Existing configurations will stop immediately</AlertTitle>
							<AlertDescription>
								Practice detection and Mentor configurations using this model cannot run until the
								model is reactivated or replaced.
							</AlertDescription>
						</Alert>
					)}

					<PriceModeEditor
						audience="instance"
						idPrefix="llm-model-price"
						value={price}
						onChange={(next) => {
							setPrice(next);
							if (next.pricingMode === "UNPRICED") setEnabled(false);
						}}
						errors={errors}
					/>

					{!isEdit && (
						<Field>
							<FieldLabel htmlFor="llm-model-share-with">Initial workspace access</FieldLabel>
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
								<>
									<WorkspaceMultiSelect
										id="llm-model-share-workspaces"
										className="mt-2"
										options={workspaceOptions}
										selectedIds={sharedWorkspaceIds}
										onChange={setSharedWorkspaceIds}
									/>
									{sharedWorkspaceIds.length === 0 && (
										<FieldDescription>
											No workspace can use this model yet. This is safe for staging; manage access
											from the model table when it is ready.
										</FieldDescription>
									)}
								</>
							)}
						</Field>
					)}

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

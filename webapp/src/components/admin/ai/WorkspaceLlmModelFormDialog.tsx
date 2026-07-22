import { useEffect, useState } from "react";
import type {
	CreateWorkspaceLlmModelRequest,
	UpdateWorkspaceLlmModelRequest,
	WorkspaceLlmModel,
} from "@/api/types.gen";
import { PriceModeEditor, type PriceModeValue } from "@/components/admin/llm/PriceModeEditor";
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
import { Switch } from "@/components/ui/switch";

export interface WorkspaceLlmModelFormDialogProps {
	open: boolean;
	onOpenChange: (open: boolean) => void;
	editing: WorkspaceLlmModel | null;
	isSubmitting: boolean;
	onCreate: (body: CreateWorkspaceLlmModelRequest) => void;
	onUpdate: (id: number, body: UpdateWorkspaceLlmModelRequest) => void;
}

function priceValueOf(model: WorkspaceLlmModel | null): PriceModeValue {
	return {
		pricingMode: model?.pricingMode ?? "UNPRICED",
		per1mInputUsd: model?.per1mInputUsd,
		per1mOutputUsd: model?.per1mOutputUsd,
		per1mCacheReadUsd: model?.per1mCacheReadUsd,
		per1mCacheWriteUsd: model?.per1mCacheWriteUsd,
		note: model?.priceNote,
	};
}

/** Create/edit a model on your own provider (#1368). Price is set inline — the workspace scope has no
 * separate price endpoint, unlike the instance catalog. */
export function WorkspaceLlmModelFormDialog({
	open,
	onOpenChange,
	editing,
	isSubmitting,
	onCreate,
	onUpdate,
}: WorkspaceLlmModelFormDialogProps) {
	const isEdit = editing !== null;
	const [displayName, setDisplayName] = useState("");
	const [upstreamModelId, setUpstreamModelId] = useState("");
	const [contextWindow, setContextWindow] = useState("");
	const [maxOutputTokens, setMaxOutputTokens] = useState("");
	const [supportsReasoning, setSupportsReasoning] = useState(false);
	const [enabled, setEnabled] = useState(false);
	const [price, setPrice] = useState<PriceModeValue>(() => priceValueOf(editing));
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
		setPrice(priceValueOf(editing));
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

		const shared = {
			displayName: displayName.trim(),
			contextWindow: contextWindow.trim() ? Number(contextWindow) : undefined,
			maxOutputTokens: maxOutputTokens.trim() ? Number(maxOutputTokens) : undefined,
			supportsReasoning,
			enabled: isEdit ? enabled : false,
			pricingMode: price.pricingMode,
			per1mInputUsd: price.pricingMode === "PRICED" ? price.per1mInputUsd : undefined,
			per1mOutputUsd: price.pricingMode === "PRICED" ? price.per1mOutputUsd : undefined,
			per1mCacheReadUsd: price.pricingMode === "PRICED" ? price.per1mCacheReadUsd : undefined,
			per1mCacheWriteUsd: price.pricingMode === "PRICED" ? price.per1mCacheWriteUsd : undefined,
			priceNote: price.pricingMode === "NO_CHARGE" ? price.note?.trim() : undefined,
		};

		if (isEdit && editing) {
			onUpdate(editing.id, shared satisfies UpdateWorkspaceLlmModelRequest);
			return;
		}
		onCreate({
			...shared,
			upstreamModelId: upstreamModelId.trim(),
		} satisfies CreateWorkspaceLlmModelRequest);
	};

	return (
		<Dialog open={open} onOpenChange={onOpenChange}>
			<DialogContent className="sm:max-w-lg">
				<form onSubmit={handleSubmit} className="space-y-4" noValidate>
					<DialogHeader>
						<DialogTitle>{isEdit ? "Edit model" : "Add model"}</DialogTitle>
						<DialogDescription>A model on your own connected provider.</DialogDescription>
					</DialogHeader>

					<Field data-invalid={Boolean(errors.displayName)}>
						<FieldLabel htmlFor="wm-display-name">Display name</FieldLabel>
						<Input
							id="wm-display-name"
							value={displayName}
							onChange={(e) => setDisplayName(e.target.value)}
							placeholder="e.g. GPT-5 mini"
							required
							aria-invalid={Boolean(errors.displayName)}
						/>
						{errors.displayName && <FieldError>{errors.displayName}</FieldError>}
					</Field>

					<Field data-invalid={Boolean(errors.upstreamModelId)}>
						<FieldLabel htmlFor="wm-upstream-id">Upstream model id</FieldLabel>
						<Input
							id="wm-upstream-id"
							value={upstreamModelId}
							onChange={(e) => setUpstreamModelId(e.target.value)}
							disabled={isEdit}
							placeholder="e.g. openai/gpt-5-mini"
							required
							autoComplete="off"
							aria-invalid={Boolean(errors.upstreamModelId)}
						/>
						<FieldDescription>
							{isEdit
								? "Create a new model to use a different upstream id."
								: "The exact id your provider expects. Slashes are part of the id."}
						</FieldDescription>
						{errors.upstreamModelId && <FieldError>{errors.upstreamModelId}</FieldError>}
					</Field>

					<div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
						<Field>
							<FieldLabel htmlFor="wm-context-window">
								Context window <span className="font-normal text-muted-foreground">(optional)</span>
							</FieldLabel>
							<Input
								id="wm-context-window"
								type="number"
								min={0}
								value={contextWindow}
								onChange={(e) => setContextWindow(e.target.value)}
							/>
						</Field>
						<Field>
							<FieldLabel htmlFor="wm-max-output">
								Max output tokens{" "}
								<span className="font-normal text-muted-foreground">(optional)</span>
							</FieldLabel>
							<Input
								id="wm-max-output"
								type="number"
								min={0}
								value={maxOutputTokens}
								onChange={(e) => setMaxOutputTokens(e.target.value)}
							/>
						</Field>
					</div>

					<div className="flex items-center gap-2 text-sm">
						<Checkbox
							id="wm-supports-reasoning"
							checked={supportsReasoning}
							onCheckedChange={(checked) => setSupportsReasoning(checked === true)}
						/>
						<label htmlFor="wm-supports-reasoning">Supports a reasoning mode</label>
					</div>

					<Field orientation="horizontal">
						<FieldContent>
							<FieldLabel htmlFor="wm-enabled">Active</FieldLabel>
							<FieldDescription>
								{isEdit
									? "Only active models with a declared price can be selected."
									: "New models start inactive. Save a price, then review and activate the model."}
							</FieldDescription>
						</FieldContent>
						<Switch
							id="wm-enabled"
							checked={enabled}
							disabled={!isEdit || price.pricingMode === "UNPRICED"}
							onCheckedChange={setEnabled}
						/>
					</Field>

					<PriceModeEditor
						audience="workspace"
						idPrefix="wm-price"
						value={price}
						onChange={(next) => {
							setPrice(next);
							if (next.pricingMode === "UNPRICED") setEnabled(false);
						}}
						errors={errors}
					/>

					<DialogFooter>
						<Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
							Cancel
						</Button>
						<Button type="submit" disabled={isSubmitting}>
							{isEdit ? "Save changes" : "Add inactive model"}
						</Button>
					</DialogFooter>
				</form>
			</DialogContent>
		</Dialog>
	);
}

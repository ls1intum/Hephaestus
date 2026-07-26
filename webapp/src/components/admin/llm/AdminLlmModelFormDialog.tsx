import { AlertTriangle } from "lucide-react";
import { useState } from "react";
import type {
	CreateLlmModelRequest,
	LlmModel,
	UpdateLlmModelPriceRequest,
	UpdateLlmModelRequest,
	UpdateLlmModelSharingRequest,
} from "@/api/types.gen";
import { FacetMultiSelect } from "@/components/common/FacetMultiSelect";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
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
import { Switch } from "@/components/ui/switch";
import type { AdminLlmModelSaveBody } from "@/lib/admin-llm-model-save";
import {
	type FieldErrors,
	type LlmModelFormField,
	validateLlmModelForm,
} from "@/lib/llm-form-validation";
import { PriceModeEditor, type PriceModeValue } from "./PriceModeEditor";
import { type WorkspaceOption, workspaceFacetOptions } from "./workspace-options";

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
	workspaceOptions: WorkspaceOption[];
	/** Model ids discovered by the connection's last successful probe, offered as a datalist. */
	probedModelIds: string[];
	isSubmitting: boolean;
	onSave: (body: AdminLlmModelSaveBody) => void;
}

function priceValueOf(model: LlmModel | null): PriceModeValue {
	return {
		pricingMode: model?.currentPrice?.pricingMode ?? "UNPRICED",
		per1mInputUsd: model?.currentPrice?.per1mInputUsd,
		per1mOutputUsd: model?.currentPrice?.per1mOutputUsd,
		per1mCacheReadUsd: model?.currentPrice?.per1mCacheReadUsd,
		per1mCacheWriteUsd: model?.currentPrice?.per1mCacheWriteUsd,
		note: model?.currentPrice?.note,
	};
}

/**
 * Create/edit an instance catalog model (#1368). Creation includes initial access; later access
 * changes use the dedicated access dialog so their immediate impact cannot be bypassed.
 *
 * The body is a separate component keyed on the edited model, so switching which model is edited
 * remounts it with fresh initial state instead of copying props into state from an effect — the same
 * seeding rule as the workspace-scoped form. An effect would leave a window, between the prop change
 * and the effect running, in which the dialog shows the *previous* model's price and sharing under
 * the new model's title.
 */
export function AdminLlmModelFormDialog({
	open,
	onOpenChange,
	editing,
	...contentProps
}: AdminLlmModelFormDialogProps) {
	return (
		<Dialog open={open} onOpenChange={onOpenChange}>
			{open && (
				<AdminLlmModelFormDialogContent
					key={editing?.id ?? "new"}
					editing={editing}
					onOpenChange={onOpenChange}
					{...contentProps}
				/>
			)}
		</Dialog>
	);
}

type AdminLlmModelFormDialogContentProps = Omit<AdminLlmModelFormDialogProps, "open">;

function AdminLlmModelFormDialogContent({
	onOpenChange,
	editing,
	workspaceOptions,
	probedModelIds,
	isSubmitting,
	onSave,
}: AdminLlmModelFormDialogContentProps) {
	const isEdit = editing !== null;
	const [displayName, setDisplayName] = useState(editing?.displayName ?? "");
	const [upstreamModelId, setUpstreamModelId] = useState(editing?.upstreamModelId ?? "");
	const [contextWindow, setContextWindow] = useState(
		editing?.contextWindow != null ? String(editing.contextWindow) : "",
	);
	const [maxOutputTokens, setMaxOutputTokens] = useState(
		editing?.maxOutputTokens != null ? String(editing.maxOutputTokens) : "",
	);
	const [supportsReasoning, setSupportsReasoning] = useState(editing?.supportsReasoning ?? false);
	const [enabled, setEnabled] = useState(editing?.enabled ?? false);
	const [price, setPrice] = useState<PriceModeValue>(() => priceValueOf(editing));
	const [shareAll, setShareAll] = useState(editing ? editing.visibility === "PUBLIC" : false);
	const [sharedWorkspaceIds, setSharedWorkspaceIds] = useState<number[]>(
		editing?.grantedWorkspaceIds ?? [],
	);
	const [errors, setErrors] = useState<FieldErrors<LlmModelFormField>>({});

	// The shared rules, not this form's own: a rule the workspace console enforces and the instance
	// console does not is a rule an admin discovers from a 400.
	const validate = (): boolean => {
		const next = validateLlmModelForm({
			displayName,
			// The upstream id is immutable, so an edit neither sends nor validates one.
			...(isEdit ? {} : { upstreamModelId }),
			contextWindow,
			maxOutputTokens,
			...price,
		});
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
		<DialogContent className="sm:max-w-lg">
			{/* `contents`, so the header/body/footer are the popup's own flex children: a `<form>` with a
			    layout box of its own would defeat the pinned-header/scrolling-body column. */}
			<form onSubmit={handleSubmit} className="contents" noValidate>
				<DialogHeader>
					<DialogTitle>{isEdit ? "Edit model" : "Add model"}</DialogTitle>
					<DialogDescription>
						Give the model a name workspaces will recognize. The upstream id is never shown to them.
					</DialogDescription>
				</DialogHeader>

				{/* The tallest form on the instance console — seven fields, the price editor and the
				    workspace picker, past 1000 px. Only the body scrolls, so the title and "Add model"
				    stay reachable. */}
				<DialogBody className="space-y-4 py-1">
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

					<Field orientation="horizontal">
						<Checkbox
							id="llm-model-supports-reasoning"
							checked={supportsReasoning}
							onCheckedChange={(checked) => setSupportsReasoning(checked === true)}
						/>
						<FieldContent>
							<FieldLabel htmlFor="llm-model-supports-reasoning" className="font-normal">
								Supports a reasoning mode
							</FieldLabel>
						</FieldContent>
					</Field>

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
							<AlertTitle>Work on this model stops immediately, in every workspace</AlertTitle>
							<AlertDescription>
								Practice detection and Mentor can't run on it until you reactivate it, or until each
								workspace picks another model.
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
									<FacetMultiSelect
										variant="field"
										id="llm-model-share-workspaces"
										title="Workspaces"
										className="mt-2"
										options={workspaceFacetOptions(workspaceOptions)}
										selected={sharedWorkspaceIds}
										onChange={setSharedWorkspaceIds}
										emptyLabel="No workspaces yet"
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
				</DialogBody>

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
	);
}

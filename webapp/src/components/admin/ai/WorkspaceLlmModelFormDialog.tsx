import { AlertTriangle } from "lucide-react";
import { useState } from "react";
import type {
	CreateWorkspaceLlmModelRequest,
	UpdateWorkspaceLlmModelRequest,
	WorkspaceLlmModel,
} from "@/api/types.gen";
import { PriceModeEditor, type PriceModeValue } from "@/components/admin/llm/PriceModeEditor";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
	Dialog,
	DialogBody,
	DialogClose,
	DialogContent,
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
import {
	type FieldErrors,
	type LlmModelFormField,
	validateLlmModelForm,
} from "@/lib/llm-form-validation";

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
 * separate price endpoint, unlike the instance catalog.
 *
 * The body is a separate component keyed on the edited model, so switching which model is edited
 * remounts it with fresh initial state instead of copying props into state from an effect.
 */
export function WorkspaceLlmModelFormDialog({
	open,
	onOpenChange,
	editing,
	...contentProps
}: WorkspaceLlmModelFormDialogProps) {
	return (
		<Dialog open={open} onOpenChange={onOpenChange}>
			{open && (
				<WorkspaceLlmModelFormDialogContent
					key={editing?.id ?? "new"}
					editing={editing}
					{...contentProps}
				/>
			)}
		</Dialog>
	);
}

type WorkspaceLlmModelFormDialogContentProps = Omit<
	WorkspaceLlmModelFormDialogProps,
	"open" | "onOpenChange"
>;

function WorkspaceLlmModelFormDialogContent({
	editing,
	isSubmitting,
	onCreate,
	onUpdate,
}: WorkspaceLlmModelFormDialogContentProps) {
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
	const [errors, setErrors] = useState<FieldErrors<LlmModelFormField>>({});

	const validate = (): boolean => {
		const next = validateLlmModelForm({
			displayName,
			// Immutable once created, so an edit neither sends an upstream id nor validates one.
			upstreamModelId: isEdit ? undefined : upstreamModelId,
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
		<DialogContent className="sm:max-w-lg">
			{/* `contents`: the form wraps header, body and footer so submit works, without becoming a
			    layout box that would defeat the popup's pinned-header/scrolling-body column. */}
			<form onSubmit={handleSubmit} className="contents" noValidate>
				<DialogHeader>
					<DialogTitle>{isEdit ? "Edit model" : "Add model"}</DialogTitle>
				</DialogHeader>

				{/* The tallest form on this surface — six fields plus the whole price editor, around
				    950 px. Without an internal scroll region it overflowed every phone viewport in both
				    directions and neither the title nor the submit button could be reached. */}
				<DialogBody className="space-y-4 py-1">
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
						<Field data-invalid={Boolean(errors.contextWindow)}>
							<FieldLabel htmlFor="wm-context-window">
								Context window <span className="font-normal text-muted-foreground">(optional)</span>
							</FieldLabel>
							<Input
								id="wm-context-window"
								type="number"
								min={0}
								step={1}
								value={contextWindow}
								onChange={(e) => setContextWindow(e.target.value)}
								aria-invalid={Boolean(errors.contextWindow)}
							/>
							{errors.contextWindow && <FieldError>{errors.contextWindow}</FieldError>}
						</Field>
						<Field data-invalid={Boolean(errors.maxOutputTokens)}>
							<FieldLabel htmlFor="wm-max-output">
								Max output tokens{" "}
								<span className="font-normal text-muted-foreground">(optional)</span>
							</FieldLabel>
							<Input
								id="wm-max-output"
								type="number"
								min={0}
								step={1}
								value={maxOutputTokens}
								onChange={(e) => setMaxOutputTokens(e.target.value)}
								aria-invalid={Boolean(errors.maxOutputTokens)}
							/>
							{errors.maxOutputTokens && <FieldError>{errors.maxOutputTokens}</FieldError>}
						</Field>
					</div>

					<Field orientation="horizontal">
						<Checkbox
							id="wm-supports-reasoning"
							checked={supportsReasoning}
							onCheckedChange={(checked) => setSupportsReasoning(checked === true)}
						/>
						<FieldContent>
							<FieldLabel htmlFor="wm-supports-reasoning" className="font-normal">
								Supports a reasoning mode
							</FieldLabel>
						</FieldContent>
					</Field>

					<Field orientation="horizontal">
						<FieldContent>
							<FieldLabel htmlFor="wm-enabled">Active</FieldLabel>
							<FieldDescription>
								{isEdit
									? "Only active models with a declared price can be selected."
									: "Starts inactive. Add a price, then activate."}
							</FieldDescription>
						</FieldContent>
						<Switch
							id="wm-enabled"
							checked={enabled}
							disabled={!isEdit || price.pricingMode === "UNPRICED"}
							onCheckedChange={setEnabled}
						/>
					</Field>

					{editing?.enabled && !enabled && (
						<Alert variant="warning">
							<AlertTriangle aria-hidden />
							<AlertTitle>Work on this model stops immediately</AlertTitle>
							<AlertDescription>
								Practice detection and the mentor can't run until you reactivate this model or pick
								another.
							</AlertDescription>
						</Alert>
					)}

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
				</DialogBody>

				<DialogFooter>
					<DialogClose render={<Button type="button" variant="outline" />}>Cancel</DialogClose>
					<Button type="submit" disabled={isSubmitting}>
						{isEdit ? "Save changes" : "Add inactive model"}
					</Button>
				</DialogFooter>
			</form>
		</DialogContent>
	);
}

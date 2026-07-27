import { AlertTriangle } from "lucide-react";
import { useId } from "react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Checkbox } from "@/components/ui/checkbox";
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
import type { LlmAudience } from "@/lib/llm-pricing";
import { PriceModeEditor, type PriceModeValue } from "./PriceModeEditor";

/**
 * Everything both model forms ask, and the one place they ask it.
 *
 * The instance catalog and a workspace's own provider are two different things to own, but "what is
 * this model called, what is it called upstream, how big is it, what does it cost, is it on" is one
 * question asked twice. Held apart, the two forms drifted: the instance one rendered no message for
 * a rejected token count, so an out-of-range number made Save do nothing at all.
 *
 * Price is part of the value rather than a second piece of state, because the two are not
 * independent: a model whose price becomes unknown cannot stay active, and that rule now has one
 * place to live instead of one per form.
 */
export interface LlmModelFieldsValue {
	displayName: string;
	upstreamModelId: string;
	/** An `<input type="number">` hands back a string; empty means the admin left it out. */
	contextWindow: string;
	maxOutputTokens: string;
	supportsReasoning: boolean;
	enabled: boolean;
	price: PriceModeValue;
}

/** The metadata columns both `LlmModel` and `WorkspaceLlmModel` carry under the same names. */
type EditedModel = {
	displayName: string;
	upstreamModelId: string;
	contextWindow?: number;
	maxOutputTokens?: number;
	supportsReasoning?: boolean;
	enabled?: boolean;
};

/**
 * Seeds the form from the model being edited, or from nothing when creating one.
 *
 * The price is passed in rather than read off the model: the instance catalog keeps it in a
 * `currentPrice` record and a workspace model keeps it in flat columns, which is the one part of the
 * two shapes that genuinely differs.
 */
export function modelFieldsValueOf(
	model: EditedModel | null,
	price: PriceModeValue,
): LlmModelFieldsValue {
	return {
		displayName: model?.displayName ?? "",
		upstreamModelId: model?.upstreamModelId ?? "",
		contextWindow: model?.contextWindow != null ? String(model.contextWindow) : "",
		maxOutputTokens: model?.maxOutputTokens != null ? String(model.maxOutputTokens) : "",
		supportsReasoning: model?.supportsReasoning ?? false,
		enabled: model?.enabled ?? false,
		price,
	};
}

/**
 * The shared rules, not either form's own: a rule the workspace console enforces and the instance
 * console does not is a rule an admin discovers from a 400.
 */
export function validateModelFields(
	value: LlmModelFieldsValue,
	isEdit: boolean,
): FieldErrors<LlmModelFormField> {
	return validateLlmModelForm({
		displayName: value.displayName,
		// The upstream id is immutable, so an edit neither sends one nor validates one.
		upstreamModelId: isEdit ? undefined : value.upstreamModelId,
		contextWindow: value.contextWindow,
		maxOutputTokens: value.maxOutputTokens,
		...value.price,
	});
}

/**
 * The sentences that differ between the two scopes, side by side so a change to one is made next to
 * the other. Every other difference between the two forms turned out to be an accident.
 */
const COPY = {
	instance: {
		displayNamePlaceholder: "e.g. GPT-5",
		upstreamIdPlaceholder: "e.g. openai/gpt-5",
		upstreamIdHint: "The exact id the provider expects. Slashes are part of the id.",
		activeHintEdit: "Only active models can be selected for new workspace requests.",
		activeHintCreate:
			"New models are saved inactive. Review the saved price and sharing before activating.",
		deactivationTitle: "Work on this model stops immediately, in every workspace",
		deactivationBody:
			"Practice detection and Mentor can't run on it until you reactivate it, or until each workspace picks another model.",
	},
	workspace: {
		displayNamePlaceholder: "e.g. GPT-5 mini",
		upstreamIdPlaceholder: "e.g. openai/gpt-5-mini",
		upstreamIdHint: "The exact id your provider expects. Slashes are part of the id.",
		activeHintEdit: "Only active models with a declared price can be selected.",
		activeHintCreate: "Starts inactive. Add a price, then activate.",
		deactivationTitle: "Work on this model stops immediately",
		deactivationBody:
			"Practice detection and the mentor can't run until you reactivate this model or pick another.",
	},
} satisfies Record<LlmAudience, Record<string, string>>;

export interface LlmModelFieldsProps {
	/** Instance admin edits the shared catalog; a workspace admin edits their own provider's models. */
	audience: LlmAudience;
	/** Prefixes every control id, so two of these can never collide on one page. */
	idPrefix: string;
	isEdit: boolean;
	/** The model was active before this edit began, so switching it off is a consequence to warn about. */
	wasEnabled: boolean;
	value: LlmModelFieldsValue;
	onChange: (value: LlmModelFieldsValue) => void;
	errors: FieldErrors<LlmModelFormField>;
	/** Model ids from the connection's last successful probe, offered as a datalist. */
	upstreamIdSuggestions?: string[];
}

export function LlmModelFields({
	audience,
	idPrefix,
	isEdit,
	wasEnabled,
	value,
	onChange,
	errors,
	upstreamIdSuggestions,
}: LlmModelFieldsProps) {
	const copy = COPY[audience];
	const update = (patch: Partial<LlmModelFieldsValue>) => onChange({ ...value, ...patch });

	const displayNameErrorId = useId();
	const upstreamModelIdErrorId = useId();
	const contextWindowErrorId = useId();
	const maxOutputTokensErrorId = useId();
	const suggestionsId = `${idPrefix}-upstream-id-options`;

	return (
		<>
			<Field data-invalid={Boolean(errors.displayName)}>
				<FieldLabel htmlFor={`${idPrefix}-display-name`}>Display name</FieldLabel>
				<Input
					id={`${idPrefix}-display-name`}
					value={value.displayName}
					onChange={(e) => update({ displayName: e.target.value })}
					placeholder={copy.displayNamePlaceholder}
					// `required` is semantics only — the form is `noValidate`, so the browser never acts on
					// it — but it is what tells a screen reader the field is required before submit (SC 3.3.2).
					required
					aria-invalid={Boolean(errors.displayName)}
					aria-describedby={errors.displayName ? displayNameErrorId : undefined}
				/>
				{errors.displayName && (
					<FieldError id={displayNameErrorId}>{errors.displayName}</FieldError>
				)}
			</Field>

			<Field data-invalid={Boolean(errors.upstreamModelId)}>
				<FieldLabel htmlFor={`${idPrefix}-upstream-id`}>Upstream model id</FieldLabel>
				<Input
					id={`${idPrefix}-upstream-id`}
					value={value.upstreamModelId}
					onChange={(e) => update({ upstreamModelId: e.target.value })}
					disabled={isEdit}
					placeholder={copy.upstreamIdPlaceholder}
					required={!isEdit}
					autoComplete="off"
					list={suggestionsId}
					aria-invalid={Boolean(errors.upstreamModelId)}
					aria-describedby={errors.upstreamModelId ? upstreamModelIdErrorId : undefined}
				/>
				{upstreamIdSuggestions && upstreamIdSuggestions.length > 0 && (
					<datalist id={suggestionsId}>
						{upstreamIdSuggestions.map((id) => (
							<option key={id} value={id} />
						))}
					</datalist>
				)}
				<FieldDescription>
					{isEdit ? "Create a new model to use a different upstream id." : copy.upstreamIdHint}
				</FieldDescription>
				{errors.upstreamModelId && (
					<FieldError id={upstreamModelIdErrorId}>{errors.upstreamModelId}</FieldError>
				)}
			</Field>

			<div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
				<Field data-invalid={Boolean(errors.contextWindow)}>
					<FieldLabel htmlFor={`${idPrefix}-context-window`}>
						Context window <span className="font-normal text-muted-foreground">(optional)</span>
					</FieldLabel>
					<Input
						id={`${idPrefix}-context-window`}
						type="number"
						min={0}
						step={1}
						value={value.contextWindow}
						onChange={(e) => update({ contextWindow: e.target.value })}
						aria-invalid={Boolean(errors.contextWindow)}
						aria-describedby={errors.contextWindow ? contextWindowErrorId : undefined}
					/>
					{errors.contextWindow && (
						<FieldError id={contextWindowErrorId}>{errors.contextWindow}</FieldError>
					)}
				</Field>
				<Field data-invalid={Boolean(errors.maxOutputTokens)}>
					<FieldLabel htmlFor={`${idPrefix}-max-output`}>
						Max output tokens <span className="font-normal text-muted-foreground">(optional)</span>
					</FieldLabel>
					<Input
						id={`${idPrefix}-max-output`}
						type="number"
						min={0}
						step={1}
						value={value.maxOutputTokens}
						onChange={(e) => update({ maxOutputTokens: e.target.value })}
						aria-invalid={Boolean(errors.maxOutputTokens)}
						aria-describedby={errors.maxOutputTokens ? maxOutputTokensErrorId : undefined}
					/>
					{errors.maxOutputTokens && (
						<FieldError id={maxOutputTokensErrorId}>{errors.maxOutputTokens}</FieldError>
					)}
				</Field>
			</div>

			<Field orientation="horizontal">
				<Checkbox
					id={`${idPrefix}-supports-reasoning`}
					checked={value.supportsReasoning}
					onCheckedChange={(checked) => update({ supportsReasoning: checked === true })}
				/>
				<FieldContent>
					<FieldLabel htmlFor={`${idPrefix}-supports-reasoning`} className="font-normal">
						Supports a reasoning mode
					</FieldLabel>
				</FieldContent>
			</Field>

			<Field orientation="horizontal">
				<FieldContent>
					<FieldLabel htmlFor={`${idPrefix}-enabled`}>Active</FieldLabel>
					<FieldDescription>
						{isEdit ? copy.activeHintEdit : copy.activeHintCreate}
					</FieldDescription>
				</FieldContent>
				<Switch
					id={`${idPrefix}-enabled`}
					checked={value.enabled}
					disabled={!isEdit || value.price.pricingMode === "UNPRICED"}
					onCheckedChange={(enabled) => update({ enabled })}
				/>
			</Field>

			{wasEnabled && !value.enabled && (
				<Alert variant="warning">
					<AlertTriangle aria-hidden />
					<AlertTitle>{copy.deactivationTitle}</AlertTitle>
					<AlertDescription>{copy.deactivationBody}</AlertDescription>
				</Alert>
			)}

			<PriceModeEditor
				audience={audience}
				idPrefix={`${idPrefix}-price`}
				value={value.price}
				// A model nobody can price cannot stay active, so the switch follows the price down.
				onChange={(price) =>
					update(price.pricingMode === "UNPRICED" ? { price, enabled: false } : { price })
				}
				errors={errors}
			/>
		</>
	);
}

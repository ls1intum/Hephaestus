import { Plus, Trash2 } from "lucide-react";
import type {
	PracticeBinding,
	PracticeEvidenceOutcome,
	PracticeWorkTypeDefinitionOptions,
} from "@/api/types.gen";
import {
	ADD_BINDING_ID,
	belongsToBinding,
	bindingFieldId,
	bindingIdPrefix,
	claimedSignals,
	hasDrafts,
	MAX_BINDINGS,
	normalizeBinding,
	recommendedBinding,
} from "@/components/admin/practice-catalog/bindings";
import { PracticeEvidenceEditor } from "@/components/admin/practice-catalog/PracticeEvidenceEditor";
import { PracticeEvidenceOutcomeSummary } from "@/components/admin/practice-catalog/PracticeEvidenceOutcomeSummary";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
	Field,
	FieldDescription,
	FieldError,
	FieldGroup,
	FieldLabel,
	FieldLegend,
	FieldSet,
} from "@/components/ui/field";

/**
 * The one error this editor shows, and the id the control it names points at.
 *
 * <p>Rendering the message is not enough on its own: an author who submits an invalid form is sent to
 * the control that has to change, and unless that control is described by the message, what they hear
 * on arrival is the group's name and nothing about what is wrong with it.
 */
const BINDINGS_ERROR_ID = "practice-bindings-error";

export interface PracticeBindingsEditorProps {
	/** The work type every binding belongs to; supplies the signals and sources they may name. */
	options: PracticeWorkTypeDefinitionOptions;
	bindings: PracticeBinding[];
	onChange: (bindings: PracticeBinding[]) => void;
	/** Whether a review is attempted at all; when false, evidence is recorded but never checked. */
	canAttemptReview?: boolean;
	/** True while the practice runs no automated review, which forbids evidence outright. */
	guidanceOnly?: boolean;
	/** How these requirements have turned out on recent reviews; omitted while creating a practice. */
	outcome?: PracticeEvidenceOutcome;
	error?: string;
	/** The control the error points at, so the invalid occasion is the one that opens. */
	errorFocusId?: string;
	disabled?: boolean;
}

/**
 * The occasions a practice is reviewed on.
 *
 * <p>An occasion used to be two fields that could not disagree — one work type, one flat list of
 * trigger events — and the evidence for all of them lived on a policy shared by the practice. It is
 * now a list, because the two questions genuinely have different answers per occasion: reviewing a
 * change when it opens and reviewing it when it merges are different reviews reading different things.
 * The form shows them as separate cards rather than a merged list so that difference stays visible.
 */
export function PracticeBindingsEditor({
	options,
	bindings,
	onChange,
	canAttemptReview = true,
	guidanceOnly = false,
	outcome,
	error,
	errorFocusId,
	disabled = false,
}: PracticeBindingsEditorProps) {
	const claimed = claimedSignals(bindings);
	const allSignalsClaimed = options.signals.every((option) => claimed.has(option.signal));
	const canAdd = bindings.length < MAX_BINDINGS && !allSignalsClaimed;
	const replaceAt = (index: number, binding: PracticeBinding) =>
		onChange(bindings.map((item, itemIndex) => (itemIndex === index ? binding : item)));

	return (
		<div className="space-y-4">
			{outcome && (
				<PracticeEvidenceOutcomeSummary outcome={outcome} sources={options.allowedSources} />
			)}
			<ol className="space-y-4">
				{bindings.map((binding, index) => (
					<li
						// Position is the only identity an occasion has: its signals are what the author is
						// editing, so keying on them would remount the card mid-edit.
						key={index}
						className="rounded-lg border p-4"
					>
						<BindingCard
							options={options}
							binding={binding}
							index={index}
							total={bindings.length}
							claimedElsewhere={claimedSignals(bindings.filter((_, other) => other !== index))}
							canAttemptReview={canAttemptReview}
							guidanceOnly={guidanceOnly}
							errorFocusId={belongsToBinding(errorFocusId, index) ? errorFocusId : undefined}
							errorId={error ? BINDINGS_ERROR_ID : undefined}
							disabled={disabled}
							onChange={(next) => replaceAt(index, next)}
							onRemove={() => onChange(bindings.filter((_, other) => other !== index))}
						/>
					</li>
				))}
			</ol>
			<div className="flex flex-wrap items-center gap-3">
				<Button
					id={ADD_BINDING_ID}
					type="button"
					variant="outline"
					size="sm"
					aria-describedby={
						error && errorFocusId === ADD_BINDING_ID ? BINDINGS_ERROR_ID : undefined
					}
					disabled={disabled || !canAdd}
					onClick={() =>
						onChange([
							...bindings,
							guidanceOnly
								? { ...recommendedBinding(options, [...claimed]), needs: [] }
								: recommendedBinding(options, [...claimed]),
						])
					}
				>
					<Plus className="size-4" />
					Add occasion
				</Button>
				{allSignalsClaimed && bindings.length > 0 && (
					<p className="text-sm text-muted-foreground">
						Every moment this kind of work offers is already claimed by an occasion.
					</p>
				)}
			</div>
			{error && <FieldError id={BINDINGS_ERROR_ID}>{error}</FieldError>}
		</div>
	);
}

interface BindingCardProps {
	options: PracticeWorkTypeDefinitionOptions;
	binding: PracticeBinding;
	index: number;
	total: number;
	claimedElsewhere: ReadonlySet<string>;
	canAttemptReview: boolean;
	guidanceOnly: boolean;
	/** The control this occasion must send focus to, when the invalid one is in this occasion. */
	errorFocusId?: string;
	/** The id of the form-level message, so the control focus lands on is described by it. */
	errorId?: string;
	disabled: boolean;
	onChange: (binding: PracticeBinding) => void;
	onRemove: () => void;
}

function BindingCard({
	options,
	binding,
	index,
	total,
	claimedElsewhere,
	canAttemptReview,
	guidanceOnly,
	errorFocusId,
	errorId,
	disabled,
	onChange,
	onRemove,
}: BindingCardProps) {
	const idPrefix = bindingIdPrefix(index);
	const occasionLabel = `occasion ${index + 1}`;
	const signalsInvalid = errorFocusId === bindingFieldId(index, "signals");
	const evidenceInvalid = errorFocusId === bindingFieldId(index, "evidence");
	const chosen = options.signals.filter((option) => binding.signals.includes(option.signal));
	const summary =
		chosen.length > 0
			? chosen.map((option) => option.displayName).join(", ")
			: "No moment chosen yet";
	const toggleSignal = (signal: string, checked: boolean) =>
		onChange(
			normalizeBinding({
				...binding,
				signals: checked
					? [...binding.signals, signal]
					: binding.signals.filter((value) => value !== signal),
			}),
		);

	return (
		<div className="space-y-4">
			<div className="flex items-start justify-between gap-3">
				<div className="min-w-0">
					<p className="font-medium">Occasion {index + 1}</p>
					<p className="truncate text-sm text-muted-foreground">{summary}</p>
				</div>
				{total > 1 && (
					<Button
						type="button"
						variant="ghost"
						size="icon-sm"
						disabled={disabled}
						onClick={onRemove}
						aria-label={`Remove occasion ${index + 1}`}
					>
						<Trash2 className="size-4" />
					</Button>
				)}
			</div>

			<FieldSet
				data-invalid={signalsInvalid || undefined}
				aria-describedby={signalsInvalid ? errorId : undefined}
				id={bindingFieldId(index, "signals")}
				// Focusable only programmatically: a form-level error sends focus here so the occasion it
				// names is the one the author lands in, but the group stays out of the tab order.
				tabIndex={-1}
				// The legend is scoped by the occasion so a screen reader reading two of them apart hears
				// which one it is in rather than three identical "Starts a review when" groups.
				aria-label={`Starts a review when, ${occasionLabel}`}
			>
				<FieldLegend variant="label">Starts a review when *</FieldLegend>
				<FieldGroup data-slot="checkbox-group" className="grid gap-3 sm:grid-cols-2">
					{options.signals.map((option) => {
						const takenElsewhere = claimedElsewhere.has(option.signal);
						const controlId = `${idPrefix}-signal-${option.signal}`;
						return (
							<FieldLabel
								key={option.signal}
								htmlFor={controlId}
								className="flex cursor-pointer items-center gap-2 text-sm font-normal"
							>
								<Checkbox
									id={controlId}
									checked={binding.signals.includes(option.signal)}
									// The server refuses a signal bound twice outright, so the second occasion
									// cannot be allowed to claim it and discover that on save.
									disabled={disabled || takenElsewhere}
									onCheckedChange={(checked) => toggleSignal(option.signal, checked === true)}
								/>
								<span>
									{option.displayName}
									{takenElsewhere && (
										<span className="text-muted-foreground"> · used by another occasion</span>
									)}
								</span>
							</FieldLabel>
						);
					})}
				</FieldGroup>
			</FieldSet>

			{hasDrafts(options.artifactKind) && (
				<Field orientation="horizontal" className="gap-2" data-disabled={disabled}>
					<Checkbox
						id={`${idPrefix}-on-drafts`}
						disabled={disabled}
						checked={binding.onDrafts === true}
						onCheckedChange={(checked) =>
							onChange(normalizeBinding({ ...binding, onDrafts: checked === true }))
						}
					/>
					<FieldLabel htmlFor={`${idPrefix}-on-drafts`} className="font-normal">
						<span>
							Also while it is still a draft
							<FieldDescription>
								Off by default. Worth turning on where the point is to help early, and worth leaving
								off where the judgement is about finished work.
							</FieldDescription>
						</span>
					</FieldLabel>
				</Field>
			)}

			{guidanceOnly ? (
				<p className="text-sm text-muted-foreground">
					Guidance only: this occasion reads nothing, because no review runs.
				</p>
			) : (
				<PracticeEvidenceEditor
					options={options}
					needs={binding.needs}
					idPrefix={idPrefix}
					occasionLabel={occasionLabel}
					canAttemptReview={canAttemptReview}
					disabled={disabled}
					invalid={evidenceInvalid}
					errorId={evidenceInvalid ? errorId : undefined}
					onChange={(needs) => onChange({ ...binding, needs })}
				/>
			)}
		</div>
	);
}

/** Strips every occasion's evidence, which is what "guidance only" means server-side. */
export function withoutEvidence(bindings: readonly PracticeBinding[]): PracticeBinding[] {
	return bindings.map((binding) => ({ ...binding, needs: [] }));
}

/** Restores the recommended evidence on every occasion that has none. */
export function withRecommendedEvidence(
	bindings: readonly PracticeBinding[],
	options: PracticeWorkTypeDefinitionOptions,
): PracticeBinding[] {
	return bindings.map((binding) =>
		binding.needs.length > 0 ? binding : { ...binding, needs: [...options.recommendedNeeds] },
	);
}

import { PlusIcon, Trash2Icon } from "lucide-react";
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
	everyMomentClaimed,
	MAX_BINDINGS,
	normalizeBinding,
	recommendedBinding,
	signalOwners,
} from "@/components/admin/practice-catalog/bindings";
import { OccasionLifecycle } from "@/components/admin/practice-catalog/OccasionLifecycle";
import { PracticeEvidenceEditor } from "@/components/admin/practice-catalog/PracticeEvidenceEditor";
import { PracticeEvidenceOutcomeSummary } from "@/components/admin/practice-catalog/PracticeEvidenceOutcomeSummary";
import { Button } from "@/components/ui/button";
import { FieldError } from "@/components/ui/field";
import { artifactKindIcon, artifactKindLabel } from "@/lib/artifact-kinds";

/**
 * Rendering the message is not enough on its own: an author sent to the control that has to change
 * hears the group's name and nothing about what is wrong with it unless that control is described by
 * the message.
 */
const BINDINGS_ERROR_ID = "practice-bindings-error";

export interface PracticeBindingsEditorProps {
	options: PracticeWorkTypeDefinitionOptions;
	bindings: PracticeBinding[];
	onChange: (bindings: PracticeBinding[]) => void;
	/** When false, evidence is still recorded but never checked. */
	canAttemptReview?: boolean;
	/** True while the practice runs no automated review, which forbids evidence outright. */
	guidanceOnly?: boolean;
	outcome?: PracticeEvidenceOutcome;
	error?: string;
	/** The control the error points at, so the invalid occasion is the one that opens. */
	errorFocusId?: string;
	disabled?: boolean;
}

/**
 * A list rather than one shared set of fields: reviewing a change when it opens and reviewing it at
 * the merge are different reviews reading different things, and separate cards keep that visible.
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
	const allClaimed = everyMomentClaimed(options, claimed);
	const canAdd = bindings.length < MAX_BINDINGS && !allClaimed;
	const WorkIcon = artifactKindIcon(options.artifactKind);
	const replaceAt = (index: number, binding: PracticeBinding) =>
		onChange(bindings.map((item, itemIndex) => (itemIndex === index ? binding : item)));

	return (
		<div className="space-y-4">
			{/* The work type once, above every occasion, so each card can be about the moments alone
			    rather than restating which kind of work they belong to. */}
			<p className="flex items-center gap-2 text-sm font-medium">
				<WorkIcon className="size-4 text-muted-foreground" aria-hidden />
				{artifactKindLabel(options.artifactKind)}
			</p>
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
							heldElsewhere={signalOwners(bindings, index)}
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
					<PlusIcon className="size-4" />
					Add occasion
				</Button>
				{allClaimed && bindings.length > 0 && (
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
	heldElsewhere: ReadonlyMap<string, number>;
	canAttemptReview: boolean;
	guidanceOnly: boolean;
	errorFocusId?: string;
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
	heldElsewhere,
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
	const toggleSignal = (signal: string, chosen: boolean) =>
		onChange(
			normalizeBinding({
				...binding,
				signals: chosen
					? [...binding.signals, signal]
					: binding.signals.filter((value) => value !== signal),
			}),
		);

	return (
		<div className="space-y-4">
			{/* An ordinal only where there is something to tell apart. One occasion needs no number, and
			    the moments it holds are on the strip below rather than repeated as a sentence here. */}
			{total > 1 && (
				<div className="flex items-start justify-between gap-3">
					<p className="font-medium">Occasion {index + 1}</p>
					<Button
						type="button"
						variant="ghost"
						size="icon-sm"
						disabled={disabled}
						onClick={onRemove}
						aria-label={`Remove occasion ${index + 1}`}
					>
						<Trash2Icon className="size-4" />
					</Button>
				</div>
			)}

			<OccasionLifecycle
				workType={options}
				selected={binding.signals}
				heldElsewhere={heldElsewhere}
				onToggle={toggleSignal}
				onDrafts={binding.onDrafts === true}
				onDraftsChange={(onDrafts) => onChange(normalizeBinding({ ...binding, onDrafts }))}
				idPrefix={idPrefix}
				groupId={bindingFieldId(index, "signals")}
				occasionLabel={occasionLabel}
				disabled={disabled}
				invalid={signalsInvalid}
				errorId={errorId}
			/>

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

export function withRecommendedEvidence(
	bindings: readonly PracticeBinding[],
	options: PracticeWorkTypeDefinitionOptions,
): PracticeBinding[] {
	return bindings.map((binding) =>
		binding.needs.length > 0 ? binding : { ...binding, needs: [...options.recommendedNeeds] },
	);
}

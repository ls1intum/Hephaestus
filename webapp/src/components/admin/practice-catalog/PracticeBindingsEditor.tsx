import { HandIcon, PlusIcon, Trash2Icon } from "lucide-react";
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
	hasDrafts,
	MAX_BINDINGS,
	normalizeBinding,
	occasionLabel,
	recommendedBinding,
	signalOwners,
} from "@/components/admin/practice-catalog/bindings";
import { OccasionLifecycle } from "@/components/admin/practice-catalog/OccasionLifecycle";
import { manualRequestSignal } from "@/components/admin/practice-catalog/occasion-moments";
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
	const handAsked = manualRequestSignal(options.signals) !== undefined;
	const replaceAt = (index: number, binding: PracticeBinding) =>
		onChange(bindings.map((item, itemIndex) => (itemIndex === index ? binding : item)));

	return (
		<div className="space-y-4">
			{/* Everything true of the work type rather than of one occasion sits here once. Repeated on
			    every card — as the kind, the evidence rule and the hand-asked review all were — it is
			    three paragraphs an author reads twice and a wall before the first strip. */}
			<div className="space-y-1.5">
				<p className="flex items-center gap-2 text-sm font-medium">
					<WorkIcon className="size-4 text-muted-foreground" aria-hidden />
					{artifactKindLabel(options.artifactKind)}
				</p>
				{!guidanceOnly && (
					<p className="text-sm text-muted-foreground">
						{canAttemptReview
							? "Each occasion is checked for the evidence it must have before its review runs. Missing or incomplete evidence skips the practice rather than guessing."
							: "Evidence is recorded for each occasion, but nothing is reviewed while the practice asks for a human."}
					</p>
				)}
				{handAsked && (
					<p className="flex items-start gap-2 text-sm text-muted-foreground">
						<HandIcon className="mt-0.5 size-4 shrink-0" aria-hidden />
						<span>
							Anyone can also ask for a review by hand. That reviews this practice whatever state
							the work is in
							{hasDrafts(options.artifactKind) && ", drafts included"}, so it is not a moment to
							choose here.
						</span>
					</p>
				)}
			</div>
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
	guidanceOnly,
	errorFocusId,
	errorId,
	disabled,
	onChange,
	onRemove,
}: BindingCardProps) {
	const idPrefix = bindingIdPrefix(index);
	const label = occasionLabel(index);
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
				occasion={{ index, errorId: signalsInvalid ? errorId : undefined }}
				selected={binding.signals}
				heldElsewhere={heldElsewhere}
				onToggle={toggleSignal}
				onDrafts={binding.onDrafts === true}
				onDraftsChange={(onDrafts) => onChange(normalizeBinding({ ...binding, onDrafts }))}
				disabled={disabled}
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
					occasionLabel={label}
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

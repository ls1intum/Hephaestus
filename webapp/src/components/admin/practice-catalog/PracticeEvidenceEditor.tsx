import deepEqual from "fast-deep-equal";
import { ChevronRight, RotateCcw } from "lucide-react";
import { useState } from "react";
import type {
	PracticeEvidenceRequirement,
	PracticeEvidenceSourceOption,
	PracticeWorkTypeDefinitionOptions,
} from "@/api/types.gen";
import { type EvidenceRole, roleOf, withRole } from "@/components/admin/practice-catalog/bindings";
import {
	evidenceQualityLabel,
	evidenceSourceLabel,
} from "@/components/admin/practice-catalog/evidence-presentation";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import {
	Field,
	FieldDescription,
	FieldGroup,
	FieldLabel,
	FieldLegend,
	FieldSet,
	FieldTitle,
} from "@/components/ui/field";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";

/**
 * The three roles an author picks between. EXHAUSTIVE is deliberately not a fourth: it is REQUIRED
 * plus one further claim, and it is offered as that claim rather than as a separate role — see
 * {@link AbsenceClaim}.
 */
const EVIDENCE_ROLE_OPTIONS = [
	{ value: "REQUIRED", label: "Required" },
	{ value: "CONTEXTUAL", label: "Optional context" },
	{ value: "NOT_USED", label: "Not used" },
] satisfies Array<{ value: Exclude<EvidenceRole, "EXHAUSTIVE">; label: string }>;

/** Which of the three radios is selected; an exhaustive stance is a required one. */
function selectedRole(role: EvidenceRole): string {
	return role === "EXHAUSTIVE" ? "REQUIRED" : role;
}

export interface PracticeEvidenceEditorProps {
	/** The work type this occasion belongs to; supplies the sources it may read. */
	options: PracticeWorkTypeDefinitionOptions;
	/** One occasion's evidence — not the practice's. What a review reads depends on what started it. */
	needs: PracticeEvidenceRequirement[];
	onChange: (needs: PracticeEvidenceRequirement[]) => void;
	/**
	 * Whether a review will actually be attempted. Only changes the copy: an author who has said a
	 * human is needed is still choosing what a future automated review would read.
	 */
	canAttemptReview?: boolean;
	/** Prefix for control ids, so a form-level error can send focus into the right occasion. */
	idPrefix: string;
	/**
	 * Which occasion this is, appended to every group's accessible name. Three occasions otherwise
	 * present three identically named groups, and a screen-reader user cannot tell which is which.
	 */
	occasionLabel: string;
	disabled?: boolean;
	invalid?: boolean;
}

/**
 * What one occasion's review reads.
 *
 * <p>Evidence hangs off the occasion rather than the practice because the two answers genuinely
 * differ: a review that runs when a change is opened is reading what is in front of it, while the one
 * that runs at the merge is the review that can say nobody ever resolved a thread.
 */
export function PracticeEvidenceEditor({
	options,
	needs,
	onChange,
	canAttemptReview = true,
	idPrefix,
	occasionLabel,
	disabled = false,
	invalid = false,
}: PracticeEvidenceEditorProps) {
	// Open from the start when this occasion is already invalid — a form re-rendered into its error
	// state never crosses the transition below, and the fix is not reachable from the collapsed summary.
	const [open, setOpen] = useState(invalid);
	// Reveal it when a later submit lands an error. Adjusting during render rather than in an effect
	// keeps it keyed on the error itself, so editing afterwards no longer re-opens the panel under the
	// caret. https://react.dev/learn/you-might-not-need-an-effect
	const [lastInvalid, setLastInvalid] = useState(invalid);
	if (invalid !== lastInvalid) {
		setLastInvalid(invalid);
		if (invalid) setOpen(true);
	}
	const required = needs.filter((need) => need.stance !== "CONTEXTUAL");
	const contextual = needs.filter((need) => need.stance === "CONTEXTUAL");
	const usesRecommendedNeeds = deepEqual(needs, options.recommendedNeeds);

	return (
		<FieldSet
			data-invalid={invalid || undefined}
			aria-label={`What this review reads, ${occasionLabel}`}
		>
			<FieldLegend variant="label">What this review reads</FieldLegend>
			<div
				className="rounded-lg border bg-muted/30 p-3 text-sm"
				id={`${idPrefix}-evidence`}
				// Same reason as the signal group: reachable by a form-level error, not by Tab.
				tabIndex={-1}
			>
				<dl className="grid gap-2 sm:grid-cols-[8rem_1fr]">
					<dt className="font-medium text-muted-foreground">Must have</dt>
					<dd>
						{required.length > 0 ? (
							<ul className="space-y-1">
								{required.map((need) => (
									<li key={need.sourceKind}>
										{evidenceSourceLabel(need.sourceKind, options.allowedSources)}
										{need.stance === "EXHAUSTIVE" && (
											<span className="text-muted-foreground"> · and nothing missing from it</span>
										)}
									</li>
								))}
							</ul>
						) : (
							<span className="text-muted-foreground">Nothing yet</span>
						)}
					</dd>
					<dt className="font-medium text-muted-foreground">May also use</dt>
					<dd>
						{contextual.length > 0 ? (
							contextual
								.map((need) => evidenceSourceLabel(need.sourceKind, options.allowedSources))
								.join(", ")
						) : (
							<span className="text-muted-foreground">Nothing</span>
						)}
					</dd>
				</dl>
				<p className="mt-2 text-muted-foreground">
					{canAttemptReview
						? "Every source under “Must have” is checked before this review runs. Missing or incomplete evidence makes Hephaestus skip the practice instead of guessing."
						: "Recorded for this occasion, but nothing is reviewed while the practice asks for a human."}
				</p>
			</div>

			<Collapsible open={open} onOpenChange={setOpen}>
				<div className="flex flex-wrap items-center gap-2">
					<CollapsibleTrigger
						disabled={disabled}
						render={
							<Button type="button" variant="outline" size="sm" disabled={disabled}>
								<ChevronRight className="size-4 transition-transform group-aria-expanded:rotate-90" />
								Choose sources
							</Button>
						}
						className="group"
					/>
					{!usesRecommendedNeeds && (
						<Button
							type="button"
							variant="ghost"
							size="sm"
							disabled={disabled}
							onClick={() => onChange([...options.recommendedNeeds])}
						>
							<RotateCcw className="size-4" />
							Use recommended evidence
						</Button>
					)}
				</div>
				<CollapsibleContent className="mt-3 space-y-3">
					<FieldDescription>
						Choosing a source does not collect or authorize it; instance governance and workspace
						integrations control that separately.
					</FieldDescription>
					<FieldGroup className="gap-3">
						{options.allowedSources.map((source) => (
							<SourceRow
								key={source.sourceKind}
								source={source}
								role={roleOf(needs, source.sourceKind)}
								idPrefix={idPrefix}
								occasionLabel={occasionLabel}
								disabled={disabled}
								onRoleChange={(role) => onChange(withRole(needs, source.sourceKind, role))}
							/>
						))}
					</FieldGroup>
				</CollapsibleContent>
			</Collapsible>
		</FieldSet>
	);
}

interface SourceRowProps {
	source: PracticeEvidenceSourceOption;
	role: EvidenceRole;
	idPrefix: string;
	occasionLabel: string;
	disabled: boolean;
	onRoleChange: (role: EvidenceRole) => void;
}

function SourceRow({
	source,
	role,
	idPrefix,
	occasionLabel,
	disabled,
	onRoleChange,
}: SourceRowProps) {
	const controlId = `${idPrefix}-source-${source.sourceKind}`;
	const groupLabel = `Use ${source.displayName} in ${occasionLabel}`;
	return (
		<div className="rounded-lg border p-3">
			<div className="flex flex-wrap items-center gap-2">
				<p className="font-medium">{source.displayName}</p>
				<Badge variant="outline">{evidenceQualityLabel(source.requiredQuality)}</Badge>
			</div>
			<p className="mt-1 text-sm text-muted-foreground">{source.description}</p>
			<FieldSet className="mt-2">
				<FieldLegend variant="label" className="sr-only">
					{groupLabel}
				</FieldLegend>
				<RadioGroup
					value={selectedRole(role)}
					onValueChange={(next) => {
						// Leaving "Required" drops the absence claim with it: a source that is only optional
						// context can never be the ground for saying something is not there.
						if (next) onRoleChange(next as EvidenceRole);
					}}
					className="flex flex-wrap gap-x-4 gap-y-2"
					aria-label={groupLabel}
				>
					{EVIDENCE_ROLE_OPTIONS.map((option) => (
						<FieldLabel
							key={option.value}
							htmlFor={`${controlId}-${option.value}`}
							className="font-normal"
						>
							<Field orientation="horizontal" className="gap-2" data-disabled={disabled}>
								<RadioGroupItem
									id={`${controlId}-${option.value}`}
									value={option.value}
									disabled={disabled}
								/>
								<FieldTitle className="font-normal">{option.label}</FieldTitle>
							</Field>
						</FieldLabel>
					))}
				</RadioGroup>
				<AbsenceClaim
					source={source}
					role={role}
					controlId={controlId}
					disabled={disabled}
					onRoleChange={onRoleChange}
				/>
			</FieldSet>
		</div>
	);
}

interface AbsenceClaimProps {
	source: PracticeEvidenceSourceOption;
	role: EvidenceRole;
	controlId: string;
	disabled: boolean;
	onRoleChange: (role: EvidenceRole) => void;
}

/**
 * The one thing EXHAUSTIVE adds to REQUIRED, asked as that thing.
 *
 * <p>Offered as a follow-up to "Required" rather than as a fourth role because that is what the stance
 * is: the same source, read for the same review, with one further claim resting on the capture being
 * whole. Absent — not present-and-unselectable — where the source contract can never promise a whole
 * capture, since choosing it there is a request the server refuses.
 */
function AbsenceClaim({ source, role, controlId, disabled, onRoleChange }: AbsenceClaimProps) {
	if (role === "CONTEXTUAL" || role === "NOT_USED") return null;
	if (!source.supportsExhaustiveEvidence) return null;
	const checkboxId = `${controlId}-exhaustive`;
	return (
		<Field orientation="horizontal" className="mt-2 gap-2" data-disabled={disabled}>
			<Checkbox
				id={checkboxId}
				disabled={disabled}
				checked={role === "EXHAUSTIVE"}
				onCheckedChange={(checked) => onRoleChange(checked === true ? "EXHAUSTIVE" : "REQUIRED")}
			/>
			<FieldLabel htmlFor={checkboxId} className="font-normal">
				<span>
					This review says what is <em>missing</em> from {source.displayName}
					<FieldDescription>
						A partial capture then refuses the review: an incomplete list cannot tell “it is not
						there” apart from “we did not fetch it”.
					</FieldDescription>
				</span>
			</FieldLabel>
		</Field>
	);
}

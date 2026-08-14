import deepEqual from "fast-deep-equal";
import { ChevronRightIcon, RotateCcwIcon } from "lucide-react";
import { useState } from "react";
import type {
	PracticeEvidenceRequirement,
	PracticeEvidenceSourceOption,
	PracticeWorkTypeDefinitionOptions,
} from "@/api/types.gen";
import { type EvidenceRole, roleOf, withRole } from "@/components/admin/practice-catalog/bindings";
import {
	evidenceQualityRequirement,
	groupEvidenceSources,
} from "@/components/admin/practice-catalog/evidence-presentation";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import {
	Field,
	FieldContent,
	FieldDescription,
	FieldLabel,
	FieldLegend,
	FieldSet,
} from "@/components/ui/field";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { cn } from "@/lib/utils";

/**
 * EXHAUSTIVE is deliberately not a segment here: it is REQUIRED plus one further claim, and it is
 * offered as that claim — see {@link AbsenceClaim}.
 */
const EVIDENCE_ROLE_OPTIONS = [
	{
		value: "REQUIRED",
		label: "Required",
		selected: "bg-primary text-primary-foreground hover:bg-primary",
	},
	{
		value: "CONTEXTUAL",
		label: "Context",
		selected: "bg-secondary text-secondary-foreground hover:bg-secondary",
	},
	// Off is the answer on most of a work type's sources, and a filled black pill on every one of
	// them shouts the baseline and hides the two or three that are on. It is marked, not shouted.
	{ value: "NOT_USED", label: "Off", selected: "bg-muted text-foreground hover:bg-muted" },
] satisfies Array<{
	value: Exclude<EvidenceRole, "EXHAUSTIVE">;
	label: string;
	selected: string;
}>;

function selectedRole(role: EvidenceRole): string {
	return role === "EXHAUSTIVE" ? "REQUIRED" : role;
}

export interface PracticeEvidenceEditorProps {
	options: PracticeWorkTypeDefinitionOptions;
	/** One occasion's evidence, not the practice's. */
	needs: PracticeEvidenceRequirement[];
	onChange: (needs: PracticeEvidenceRequirement[]) => void;
	/** Prefix for control ids, so a form-level error can send focus into the right occasion. */
	idPrefix: string;
	/**
	 * Appended to every group's accessible name. Repeated occasions otherwise present identically
	 * named groups, and a screen-reader user cannot tell which one they are in.
	 */
	occasionLabel: string;
	disabled?: boolean;
	invalid?: boolean;
	errorId?: string;
}

/**
 * What one occasion reads, as chips over a grouped set of one-line choices.
 *
 * <p>The panel used to open on a flat list of eleven cards, each carrying a title, a badge, a sentence
 * of prose and three radios — a wall of text for a decision that is one word per source. The words are
 * still all here; they are just not all shouting at once. Collapsed, the answer is the chips. Open, a
 * source is a line: its name, what it is, and a three-way switch.
 */
export function PracticeEvidenceEditor({
	options,
	needs,
	onChange,
	idPrefix,
	occasionLabel,
	disabled = false,
	invalid = false,
	errorId,
}: PracticeEvidenceEditorProps) {
	// Open from the start when this occasion is already invalid: such a form never crosses the
	// transition below, and the fix is not reachable from the collapsed summary.
	const [open, setOpen] = useState(invalid);
	// Reveal it on the transition into invalid rather than on the flag, which stays true while the
	// author fixes it — an editor re-opening under the caret on every keystroke would be unusable.
	// https://react.dev/learn/you-might-not-need-an-effect
	const [lastInvalid, setLastInvalid] = useState(invalid);
	if (invalid !== lastInvalid) {
		setLastInvalid(invalid);
		if (invalid) setOpen(true);
	}
	const required = options.allowedSources.filter(
		(source) => roleOf(needs, source.sourceKind) !== "NOT_USED" && !isContextual(needs, source),
	);
	const contextual = options.allowedSources.filter((source) => isContextual(needs, source));
	const usesRecommendedNeeds = deepEqual(needs, options.recommendedNeeds);

	return (
		<FieldSet
			id={`${idPrefix}-evidence`}
			data-invalid={invalid || undefined}
			aria-describedby={errorId}
			aria-label={`What this review reads, ${occasionLabel}`}
			// A focus target for a form-level error, not a Tab stop.
			tabIndex={-1}
		>
			<FieldLegend variant="label">Reads</FieldLegend>
			<dl className="grid gap-x-3 gap-y-1.5 text-sm sm:grid-cols-[6.5rem_1fr]">
				<dt className="text-muted-foreground">Must have</dt>
				<dd className="flex flex-wrap gap-1.5">
					{required.length > 0 ? (
						required.map((source) => (
							<Badge key={source.sourceKind} variant="secondary">
								{source.displayName}
								{roleOf(needs, source.sourceKind) === "EXHAUSTIVE" && (
									<span className="text-muted-foreground">· whole</span>
								)}
							</Badge>
						))
					) : (
						<span className="text-muted-foreground">Nothing yet</span>
					)}
				</dd>
				<dt className="text-muted-foreground">May also use</dt>
				<dd className="flex flex-wrap gap-1.5">
					{contextual.length > 0 ? (
						contextual.map((source) => (
							<Badge key={source.sourceKind} variant="outline">
								{source.displayName}
							</Badge>
						))
					) : (
						<span className="text-muted-foreground">Nothing</span>
					)}
				</dd>
			</dl>
			<Collapsible open={open} onOpenChange={setOpen}>
				<div className="flex flex-wrap items-center gap-2">
					<CollapsibleTrigger
						disabled={disabled}
						render={
							<Button type="button" variant="outline" size="sm" disabled={disabled}>
								<ChevronRightIcon className="size-4 transition-transform group-aria-expanded:rotate-90" />
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
							<RotateCcwIcon className="size-4" />
							Use recommended evidence
						</Button>
					)}
				</div>
				<CollapsibleContent className="mt-3 space-y-4">
					<FieldDescription>
						Choosing a source does not collect or authorize it; instance governance and workspace
						integrations control that separately.
					</FieldDescription>
					{groupEvidenceSources(options.allowedSources).map((group) => (
						<div key={group.family} className="space-y-1.5">
							<p className="flex items-center gap-1.5 text-xs font-medium uppercase tracking-wide text-muted-foreground">
								<group.def.icon className="size-3.5" aria-hidden />
								{group.def.label}
							</p>
							<ul className="divide-y overflow-hidden rounded-lg border">
								{group.sources.map((source) => (
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
							</ul>
						</div>
					))}
				</CollapsibleContent>
			</Collapsible>
		</FieldSet>
	);
}

function isContextual(
	needs: readonly PracticeEvidenceRequirement[],
	source: PracticeEvidenceSourceOption,
): boolean {
	return roleOf(needs, source.sourceKind) === "CONTEXTUAL";
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
	const inUse = role !== "NOT_USED";
	const quality = evidenceQualityRequirement(source.requiredQuality);
	return (
		<li className={cn("flex flex-wrap items-start gap-x-4 gap-y-2 p-2.5", inUse && "bg-muted/40")}>
			<div className="min-w-0 flex-1 basis-56">
				<p className="text-sm font-medium">{source.displayName}</p>
				{/* Two lines is enough for every source the catalogue ships and bounds the height of a
				    list that is otherwise eleven paragraphs long. */}
				<p className="line-clamp-2 text-xs text-muted-foreground">{source.description}</p>
				{inUse && quality && <p className="mt-0.5 text-xs text-muted-foreground">{quality}</p>}
				<AbsenceClaim
					source={source}
					role={role}
					controlId={controlId}
					disabled={disabled}
					onRoleChange={onRoleChange}
				/>
			</div>
			{/* A segmented control drawn on radios rather than on the toggle group, on semantics alone:
			    the three roles are mutually exclusive and exactly one always holds, which is what a radio
			    group *means*. A toggle group is a set of independently pressed buttons that this screen
			    would then have to stop from all being off at once, and a reader would hear three
			    pressable things rather than one choice with three answers. */}
			<RadioGroup
				className="flex w-fit shrink-0 gap-0 overflow-hidden rounded-lg border"
				disabled={disabled}
				aria-label={`How ${source.displayName} is used, ${occasionLabel}`}
				value={selectedRole(role)}
				onValueChange={(next) => {
					if (next) onRoleChange(next as EvidenceRole);
				}}
			>
				{EVIDENCE_ROLE_OPTIONS.map((option) => (
					<label
						key={option.value}
						htmlFor={`${controlId}-${option.value}`}
						className={cn(
							"cursor-pointer border-l px-2.5 py-1 text-[0.8rem] font-medium text-muted-foreground transition-colors first:border-l-0",
							"hover:bg-muted",
							"has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-inset has-[:focus-visible]:ring-ring",
							selectedRole(role) === option.value && option.selected,
							disabled && "cursor-not-allowed opacity-70",
						)}
					>
						{/* Clipped, not replaced: the radio keeps its role, its arrow-key navigation and its
						    accessible name, and the label it is bound to is what a reader clicks. */}
						<span className="sr-only">
							<RadioGroupItem
								id={`${controlId}-${option.value}`}
								value={option.value}
								disabled={disabled}
							/>
						</span>
						{option.label}
					</label>
				))}
			</RadioGroup>
		</li>
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
 * Absent — not present-and-unselectable — where the source contract can never promise a whole
 * capture, since choosing it there is a request the server refuses.
 */
function AbsenceClaim({ source, role, controlId, disabled, onRoleChange }: AbsenceClaimProps) {
	if (role === "CONTEXTUAL" || role === "NOT_USED") return null;
	if (!source.supportsExhaustiveEvidence) return null;
	const checkboxId = `${controlId}-exhaustive`;
	const claimed = role === "EXHAUSTIVE";
	const consequenceId = `${checkboxId}-consequence`;
	return (
		<Field orientation="horizontal" className="mt-1.5 gap-2" data-disabled={disabled}>
			<Checkbox
				id={checkboxId}
				disabled={disabled}
				checked={claimed}
				onCheckedChange={(checked) => onRoleChange(checked === true ? "EXHAUSTIVE" : "REQUIRED")}
				aria-describedby={claimed ? consequenceId : undefined}
			/>
			{/* Label and description as siblings inside `FieldContent`, the kit's own anatomy. Nested, the
			    description put a `<p>` inside a `<label>` — invalid, its content model is phrasing content
			    — and ran into the checkbox's accessible name, so the box announced itself as the claim
			    plus the paragraph explaining what the claim costs. The consequence is a description. */}
			<FieldContent>
				<FieldLabel htmlFor={checkboxId} className="text-xs font-normal">
					Can say what is missing from {source.displayName}
				</FieldLabel>
				{claimed && (
					<FieldDescription id={consequenceId}>
						A partial capture then refuses the review: an incomplete list cannot tell “it is not
						there” apart from “we did not fetch it”.
					</FieldDescription>
				)}
			</FieldContent>
		</Field>
	);
}

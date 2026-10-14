import { ChevronRightIcon } from "lucide-react";
import { useState } from "react";

import { cn } from "cn";
import type { PracticeReadSource, ReadSourceOption } from "@/api/types.gen";
import { evidenceSourceDef } from "@/components/practice-vocabulary/evidence-source-defs";
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

import { canReadComplete, groupReadSources } from "./reads-presentation";
import { type ReadChoice, readNeedOf, withReadNeed } from "./review-settings";

/**
 * COMPLETE is deliberately not a segment here: it is REQUIRED plus one further claim, and it is
 * offered as that claim — see {@link CompleteCapture}.
 */
const READ_CHOICE_OPTIONS = [
	{
		value: "REQUIRED",
		label: "Required",
		selected: "bg-primary text-primary-foreground hover:bg-primary",
	},
	{
		value: "OPTIONAL",
		label: "Optional",
		selected: "bg-secondary text-secondary-foreground hover:bg-secondary",
	},
	// Off is the answer on most sources, so it is marked rather than shouted: a filled pill on the
	// majority would hide the few that are on.
	{ value: "NOT_USED", label: "Off", selected: "bg-muted text-foreground hover:bg-muted" },
] satisfies {
	value: SegmentedChoice;
	label: string;
	selected: string;
}[];

type SegmentedChoice = Exclude<ReadChoice, "COMPLETE">;

function segmentOf(choice: ReadChoice): SegmentedChoice {
	return choice === "COMPLETE" ? "REQUIRED" : choice;
}

export interface PracticeReadsEditorProps {
	/** The sources this kind of item can read, from the definition options. */
	readSources: readonly ReadSourceOption[];
	/** What the review reads. */
	sources: readonly PracticeReadSource[];
	onChange: (sources: PracticeReadSource[]) => void;
	/** Prefix for control ids, so a form-level error can send focus to the control that failed. */
	idPrefix: string;
	/** The legend, so a scan's reads and the practice's own read as two different lists. */
	legend?: string;
	disabled?: boolean;
	invalid?: boolean;
	errorId?: string;
}

/** What the review reads: collapsed, the answer is the chips; open, a source is one line. */
export function PracticeReadsEditor({
	readSources,
	sources,
	onChange,
	idPrefix,
	legend = "Reads",
	disabled = false,
	invalid = false,
	errorId,
}: PracticeReadsEditorProps) {
	// Open from the start when the reads are already invalid: such a form never crosses the
	// transition below, and the fix is not reachable from the collapsed summary.
	const [open, setOpen] = useState(invalid);
	// On the transition into invalid, not on the flag, which stays true while the author fixes it —
	// an editor re-opening under the caret on every keystroke would be unusable.
	// https://react.dev/learn/you-might-not-need-an-effect
	const [lastInvalid, setLastInvalid] = useState(invalid);
	if (invalid !== lastInvalid) {
		setLastInvalid(invalid);
		if (invalid) {
			setOpen(true);
		}
	}
	const required = readSources.filter((option) => {
		const need = readNeedOf(sources, option.source);
		return need === "REQUIRED" || need === "COMPLETE";
	});
	const optional = readSources.filter(
		(option) => readNeedOf(sources, option.source) === "OPTIONAL",
	);

	return (
		<FieldSet
			id={`${idPrefix}-reads`}
			data-invalid={invalid || undefined}
			aria-describedby={errorId}
			aria-label={legend}
			// A focus target for a form-level error, not a Tab stop.
			tabIndex={-1}
		>
			<FieldLegend variant="label">{legend}</FieldLegend>
			<dl className="grid gap-x-3 gap-y-1.5 text-sm sm:grid-cols-[6.5rem_1fr]">
				<dt className="text-muted-foreground">Must have</dt>
				<dd className="flex flex-wrap gap-1.5">
					{required.length > 0 ? (
						required.map((option) => (
							<Badge key={option.source} variant="secondary">
								{option.label}
								{/* "captured whole", never "nothing in the world is missing": the claim is about
								    this capture of this source, which is all the review can see. */}
								{readNeedOf(sources, option.source) === "COMPLETE" && (
									<span className="text-muted-foreground">· captured whole</span>
								)}
							</Badge>
						))
					) : (
						<span className="text-muted-foreground">Nothing yet</span>
					)}
				</dd>
				<dt className="text-muted-foreground">May also use</dt>
				<dd className="flex flex-wrap gap-1.5">
					{optional.length > 0 ? (
						optional.map((option) => (
							<Badge key={option.source} variant="outline">
								{option.label}
							</Badge>
						))
					) : (
						<span className="text-muted-foreground">Nothing</span>
					)}
				</dd>
			</dl>
			<Collapsible open={open} onOpenChange={setOpen}>
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
				<CollapsibleContent className="mt-3 space-y-4">
					<FieldDescription>
						Choosing a source does not collect or authorize it. Instance governance and workspace
						integrations control that separately.
					</FieldDescription>
					{groupReadSources(readSources).map((group) => (
						<div key={group.family} className="space-y-1.5">
							<p className="flex items-center gap-1.5 text-xs font-medium tracking-wide text-muted-foreground uppercase">
								<group.def.icon className="size-3.5" aria-hidden />
								{group.def.label}
							</p>
							<ul className="divide-y overflow-hidden rounded-lg border">
								{group.sources.map((option) => (
									<SourceRow
										key={option.source}
										option={option}
										choice={readNeedOf(sources, option.source)}
										idPrefix={idPrefix}
										disabled={disabled}
										onChoiceChange={(choice) =>
											onChange(withReadNeed(sources, option.source, choice))
										}
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

interface SourceRowProps {
	option: ReadSourceOption;
	choice: ReadChoice;
	idPrefix: string;
	disabled: boolean;
	onChoiceChange: (choice: ReadChoice) => void;
}

function SourceRow({ option, choice, idPrefix, disabled, onChoiceChange }: SourceRowProps) {
	const controlId = `${idPrefix}-source-${option.source}`;
	return (
		<li
			className={cn(
				"flex flex-wrap items-start gap-x-4 gap-y-2 p-2.5",
				choice !== "NOT_USED" && "bg-muted/40",
			)}
		>
			<div className="min-w-0 flex-1 basis-56">
				<p className="text-sm font-medium">{option.label}</p>
				{/* Clamped: the list is one line per source, and a source's own prose is not. */}
				<p className="line-clamp-2 text-xs text-muted-foreground">
					{evidenceSourceDef(option.source).description}
				</p>
				<CompleteCapture
					option={option}
					choice={choice}
					controlId={controlId}
					disabled={disabled}
					onChoiceChange={onChoiceChange}
				/>
			</div>
			{/* Radios, not the toggle group, on semantics: the choices are mutually exclusive and exactly
			    one always holds, which is what a radio group means. */}
			<RadioGroup
				className="flex w-fit shrink-0 gap-0 overflow-hidden rounded-lg border"
				disabled={disabled}
				aria-label={`How ${option.label} is read`}
				value={segmentOf(choice)}
				onValueChange={(next) => onChoiceChange(next)}
			>
				{READ_CHOICE_OPTIONS.map((segment) => (
					<label
						key={segment.value}
						htmlFor={`${controlId}-${segment.value}`}
						className={cn(
							"cursor-pointer border-l px-2.5 py-1 text-xs font-medium text-muted-foreground transition-colors first:border-l-0",
							"hover:bg-muted",
							"has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-ring has-[:focus-visible]:ring-inset",
							segmentOf(choice) === segment.value && segment.selected,
							disabled && "cursor-not-allowed opacity-70",
						)}
					>
						{/* Clipped, not replaced: the radio keeps its role, arrow-key navigation and name. */}
						<span className="sr-only">
							<RadioGroupItem
								id={`${controlId}-${segment.value}`}
								value={segment.value}
								disabled={disabled}
							/>
						</span>
						{segment.label}
					</label>
				))}
			</RadioGroup>
		</li>
	);
}

interface CompleteCaptureProps {
	option: ReadSourceOption;
	choice: ReadChoice;
	controlId: string;
	disabled: boolean;
	onChoiceChange: (choice: ReadChoice) => void;
}

/**
 * The Complete need, named by what it lets the review do and what it costs. Completeness is measured
 * against one capture of one source, so the claim never reaches beyond that source.
 *
 * Absent — not present-and-unselectable — where the source contract can never promise a whole
 * capture, since choosing it there is a request the server refuses.
 */
function CompleteCapture({
	option,
	choice,
	controlId,
	disabled,
	onChoiceChange,
}: CompleteCaptureProps) {
	if ((choice !== "REQUIRED" && choice !== "COMPLETE") || !canReadComplete(option)) {
		return null;
	}
	const checkboxId = `${controlId}-complete`;
	const complete = choice === "COMPLETE";
	const descriptionId = `${checkboxId}-description`;
	return (
		<Field orientation="horizontal" className="mt-1.5 gap-2" data-disabled={disabled}>
			<Checkbox
				id={checkboxId}
				disabled={disabled}
				checked={complete}
				onCheckedChange={(checked) => onChoiceChange(checked ? "COMPLETE" : "REQUIRED")}
				aria-describedby={descriptionId}
			/>
			{/* Label and description are siblings, never nested: a `<label>` takes phrasing content only,
			    and a description inside it joins the checkbox's accessible name. */}
			<FieldContent>
				<FieldLabel htmlFor={checkboxId} className="text-xs font-normal">
					Must be captured whole
				</FieldLabel>
				<FieldDescription id={descriptionId}>
					{complete
						? "A partial capture then withholds the review: an incomplete list cannot tell “it is not there” apart from “we did not fetch it”."
						: "Lets the review say that something is missing from this source."}
				</FieldDescription>
			</FieldContent>
		</Field>
	);
}

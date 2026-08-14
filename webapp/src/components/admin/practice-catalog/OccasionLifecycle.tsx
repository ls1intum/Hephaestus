import { HandIcon } from "lucide-react";
import type { PracticeSignalOption, PracticeWorkTypeDefinitionOptions } from "@/api/types.gen";
import { hasDrafts } from "@/components/admin/practice-catalog/bindings";
import {
	lifecycleSignals,
	manualRequestSignal,
	momentBands,
	momentDef,
	PHASE_LABEL,
} from "@/components/admin/practice-catalog/occasion-moments";
import { Checkbox } from "@/components/ui/checkbox";
import { Field, FieldDescription, FieldLabel, FieldLegend, FieldSet } from "@/components/ui/field";
import { Switch } from "@/components/ui/switch";
import { cn } from "@/lib/utils";

export interface OccasionLifecycleProps {
	/**
	 * The work type whole, not its `signals` alone: the strip has to know the artifact kind to decide
	 * whether drafts are a state this work can even be in.
	 */
	workType: PracticeWorkTypeDefinitionOptions;
	/** The moments this occasion reviews on. */
	selected: readonly string[];
	/** Moment id to the occasion number already holding it, since the server refuses a moment bound twice. */
	heldElsewhere?: ReadonlyMap<string, number>;
	onToggle: (signal: string, chosen: boolean) => void;
	onDrafts: boolean;
	onDraftsChange: (onDrafts: boolean) => void;
	/** Prefix for control ids, so ticking a moment in occasion 2 cannot toggle occasion 1's. */
	idPrefix: string;
	/** The id a form-level error sends focus to. */
	groupId: string;
	/**
	 * Appended to the group's accessible name. Two occasions otherwise present two identically named
	 * groups and a screen-reader user cannot tell which one they are in.
	 */
	occasionLabel: string;
	disabled?: boolean;
	invalid?: boolean;
	errorId?: string;
}

/**
 * The moments of one occasion, drawn as the life of the work rather than as a list of checkboxes.
 *
 * <p>An occasion is a point on an artifact's lifecycle, and the previous two-column checkbox grid hid
 * exactly that: "Opened", "Merged" and "Closed without merging" sat side by side as peers, so nothing
 * on screen said that the first happens once at the top, the middle two are alternatives, and the
 * moments in between repeat. The strip says all three without a sentence.
 *
 * <p>The bands are derived from the moments the work type actually offers, so this renders a pull
 * request's six, an issue's three, a document's three and a conversation's single one from the same
 * code. A kind this build has never met still draws — its moments land in the middle band under a
 * neutral glyph.
 */
export function OccasionLifecycle({
	workType,
	selected,
	heldElsewhere,
	onToggle,
	onDrafts,
	onDraftsChange,
	idPrefix,
	groupId,
	occasionLabel,
	disabled = false,
	invalid = false,
	errorId,
}: OccasionLifecycleProps) {
	const chosen = new Set(selected);
	// A moment that is not on the lifecycle is not on the strip — with one exception: a practice saved
	// before this screen stopped offering the hand-asked review still holds it, and hiding it would
	// leave a moment nobody can see and nobody can remove.
	const offLifecycle = manualRequestSignal(workType.signals);
	const strays = offLifecycle && chosen.has(offLifecycle.signal) ? [offLifecycle] : [];
	const bands = momentBands([...lifecycleSignals(workType.signals), ...strays]);

	return (
		<FieldSet
			id={groupId}
			data-invalid={invalid || undefined}
			aria-describedby={invalid ? errorId : undefined}
			// Focusable only programmatically: a form-level error sends focus here so the author lands in
			// the occasion it names, but the group stays out of the tab order.
			tabIndex={-1}
			aria-label={`Reviews when, ${occasionLabel}`}
		>
			<FieldLegend variant="label">Reviews when *</FieldLegend>
			<div className="flex flex-wrap items-start gap-x-6 gap-y-4">
				{bands.map((band) => {
					const rail = (
						<div className="flex items-start">
							{band.moments.map((moment) => (
								<MomentNode
									key={moment.signal}
									moment={moment}
									chosen={chosen.has(moment.signal)}
									heldBy={heldElsewhere?.get(moment.signal)}
									controlId={`${idPrefix}-signal-${moment.signal}`}
									disabled={disabled}
									invalid={invalid}
									onToggle={(next) => onToggle(moment.signal, next)}
								/>
							))}
						</div>
					);
					// A work type with one band has nothing to tell apart, so it gets no heading — and
					// with no heading there is nothing for a group to be named by.
					if (bands.length === 1) return <div key={band.phase}>{rail}</div>;
					// Named groups rather than loose headings: a reader who cannot see the bands would
					// otherwise meet six checkboxes in a row with three stray words between them, and lose
					// the one thing the strip is drawn to say.
					const headingId = `${idPrefix}-band-${band.phase}`;
					return (
						<div key={band.phase} role="group" aria-labelledby={headingId}>
							<p
								id={headingId}
								className="mb-1.5 pl-1 text-[0.7rem] font-medium uppercase tracking-wide text-muted-foreground"
							>
								{PHASE_LABEL[band.phase]}
							</p>
							{rail}
						</div>
					);
				})}
			</div>

			{hasDrafts(workType.artifactKind) && (
				<Field orientation="horizontal" className="mt-1 gap-3" data-disabled={disabled}>
					<Switch
						id={`${idPrefix}-on-drafts`}
						disabled={disabled}
						checked={onDrafts}
						onCheckedChange={onDraftsChange}
					/>
					<FieldLabel htmlFor={`${idPrefix}-on-drafts`} className="font-normal">
						<span>
							Include drafts
							<FieldDescription>
								Off by default, so these moments are read once the work is offered as finished. Turn
								it on where the point is to help early.
							</FieldDescription>
						</span>
					</FieldLabel>
				</Field>
			)}

			{offLifecycle && (
				<p className="flex items-start gap-2 text-sm text-muted-foreground">
					<HandIcon className="mt-0.5 size-4 shrink-0" aria-hidden />
					<span>
						Anyone can also ask for a review by hand. That reviews this practice whatever state the
						work is in, drafts included, so it is not a moment to choose here.
					</span>
				</p>
			)}
		</FieldSet>
	);
}

interface MomentNodeProps {
	moment: PracticeSignalOption;
	chosen: boolean;
	/** The occasion number already holding this moment, if it is not this one. */
	heldBy?: number;
	controlId: string;
	disabled: boolean;
	invalid: boolean;
	onToggle: (chosen: boolean) => void;
}

/**
 * One point on the strip: a real checkbox with a label, drawn as a node on a rail.
 *
 * <p>The checkbox is clipped rather than replaced. Nothing here reimplements checkbox behaviour — the
 * label is bound to the control with `htmlFor`, so a click, a Space press, and the accessible name all
 * come from the platform, and the focus ring is drawn on the node because that is what a reader sees.
 */
function MomentNode({
	moment,
	chosen,
	heldBy,
	controlId,
	disabled,
	invalid,
	onToggle,
}: MomentNodeProps) {
	const def = momentDef(moment.signal);
	const Icon = def.icon;
	const locked = heldBy !== undefined;
	return (
		<label
			htmlFor={controlId}
			className={cn(
				// The rail: a hairline from the node's left edge to the circle it feeds, drawn on every
				// node but the first of its band, so the line never leaves a band or dangles.
				"group/moment relative flex w-24 shrink-0 flex-col items-center gap-1 px-1 pt-1 pb-0.5 text-center",
				"before:absolute before:top-5 before:left-0 before:right-1/2 before:mr-4 before:h-px before:bg-border first:before:hidden",
				"rounded-md has-[:focus-visible]:ring-[3px] has-[:focus-visible]:ring-ring/50",
				disabled || locked ? "cursor-not-allowed" : "cursor-pointer",
			)}
		>
			{/* Clipped, not hidden: the control stays in the accessibility tree and stays focusable. */}
			<span className="sr-only">
				<Checkbox
					id={controlId}
					checked={chosen}
					disabled={disabled || locked}
					onCheckedChange={(next) => onToggle(next === true)}
				/>
			</span>
			<span
				className={cn(
					"grid size-8 shrink-0 place-items-center rounded-full border transition-colors",
					chosen && "border-primary bg-primary text-primary-foreground",
					!chosen && locked && "border-dashed border-border bg-muted text-muted-foreground",
					!chosen && !locked && "border-border bg-background text-muted-foreground",
					!chosen && !locked && invalid && "border-destructive/60",
					!chosen &&
						!locked &&
						!disabled &&
						"group-hover/moment:border-primary/60 group-hover/moment:text-foreground",
					(disabled || locked) && "opacity-70",
				)}
			>
				<Icon className="size-4" aria-hidden />
			</span>
			<span
				className={cn(
					"text-xs leading-tight",
					chosen ? "font-medium text-foreground" : "text-muted-foreground",
					(disabled || locked) && "opacity-70",
				)}
			>
				{moment.displayName}
			</span>
			{def.repeats && (
				<span className="text-[0.65rem] leading-none text-muted-foreground">every time</span>
			)}
			{locked && (
				<span className="text-[0.65rem] leading-none text-muted-foreground">
					in occasion {heldBy}
				</span>
			)}
		</label>
	);
}

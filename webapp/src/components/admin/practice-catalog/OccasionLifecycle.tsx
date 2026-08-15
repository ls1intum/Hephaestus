import type { PracticeSignalOption, PracticeWorkTypeDefinitionOptions } from "@/api/types.gen";
import {
	bindingFieldId,
	bindingIdPrefix,
	hasDrafts,
	occasionLabel,
} from "@/components/admin/practice-catalog/bindings";
import {
	lifecycleSignals,
	manualRequestSignal,
	momentBands,
	momentDef,
	PHASE_LABEL,
} from "@/components/admin/practice-catalog/occasion-moments";
import { Checkbox } from "@/components/ui/checkbox";
import {
	Field,
	FieldContent,
	FieldDescription,
	FieldLabel,
	FieldLegend,
	FieldSet,
} from "@/components/ui/field";
import { Switch } from "@/components/ui/switch";
import { cn } from "@/lib/utils";

/**
 * Which occasion this is. Control ids, the focus target for a form-level error and the words naming
 * the group are all derived from the position, so no caller can spell one of them differently.
 */
export interface OccasionIdentity {
	/** The occasion's position among the practice's occasions, from zero. */
	index: number;
	/**
	 * The id of the message describing what is wrong, passed only while *this* occasion is the one
	 * failing validation; its presence is also what draws the moments in the invalid state. One field
	 * rather than an `invalid` flag beside an id, so neither can be set without the other.
	 */
	errorId?: string;
}

export interface OccasionLifecycleProps {
	/** The whole work type, not its `signals`: the artifact kind decides whether drafts can occur. */
	workType: PracticeWorkTypeDefinitionOptions;
	occasion: OccasionIdentity;
	selected: readonly string[];
	/** Moment id to the occasion number already holding it, since the server refuses a moment bound twice. */
	heldElsewhere?: ReadonlyMap<string, number>;
	onToggle: (signal: string, chosen: boolean) => void;
	onDrafts: boolean;
	onDraftsChange: (onDrafts: boolean) => void;
	disabled?: boolean;
}

/**
 * The moments of one occasion, drawn as the life of the work rather than as a list of checkboxes.
 * Bands come from the moments the work type offers, so every kind renders from the same code.
 */
export function OccasionLifecycle({
	workType,
	occasion,
	selected,
	heldElsewhere,
	onToggle,
	onDrafts,
	onDraftsChange,
	disabled = false,
}: OccasionLifecycleProps) {
	const { index, errorId } = occasion;
	const idPrefix = bindingIdPrefix(index);
	const invalid = errorId !== undefined;
	const draftsId = `${idPrefix}-on-drafts`;
	const draftsHintId = `${draftsId}-hint`;
	const chosen = new Set(selected);
	// Off-lifecycle moments are not offered, but an already-saved one is still drawn: hiding it would
	// leave a moment nobody can see and nobody can remove.
	const offLifecycle = manualRequestSignal(workType.signals);
	const strays = offLifecycle && chosen.has(offLifecycle.signal) ? [offLifecycle] : [];
	const bands = momentBands([...lifecycleSignals(workType.signals), ...strays]);

	return (
		<FieldSet
			id={bindingFieldId(index, "signals")}
			data-invalid={invalid || undefined}
			aria-describedby={errorId}
			// Focusable only programmatically: a form-level error sends focus here so the author lands in
			// the occasion it names, but the group stays out of the tab order.
			tabIndex={-1}
			aria-label={`Reviews when, ${occasionLabel(index)}`}
		>
			<FieldLegend variant="label">Reviews when *</FieldLegend>
			<div className="flex flex-wrap items-start gap-x-6 gap-y-4">
				{bands.map((band) => {
					// No rail inside "Ends": merged and closed without merging are alternatives, and a line
					// between them would say a pull request is merged and then closed.
					const railed = band.phase !== "end";
					const rail = (
						<div className="flex items-start">
							{band.moments.map((moment) => (
								<MomentNode
									key={moment.signal}
									moment={moment}
									railed={railed}
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
					// A single band has nothing to tell apart, so no heading — and so nothing to name a
					// group by.
					if (bands.length === 1) return <div key={band.phase}>{rail}</div>;
					// Named groups rather than loose headings: a reader who cannot see the bands would meet
					// one run of checkboxes with stray words between them.
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
						id={draftsId}
						disabled={disabled}
						checked={onDrafts}
						onCheckedChange={onDraftsChange}
						aria-describedby={draftsHintId}
					/>
					{/* Label and description are siblings, never nested: a `<label>` takes phrasing content
					    only, and a description inside it joins the switch's accessible name. A name is what
					    the control *is*; the sentence explaining the default is `aria-describedby`. */}
					<FieldContent>
						<FieldLabel htmlFor={draftsId} className="font-normal">
							Include drafts
						</FieldLabel>
						<FieldDescription id={draftsHintId}>
							Off by default: read the work once it is offered as finished.
						</FieldDescription>
					</FieldContent>
				</Field>
			)}
		</FieldSet>
	);
}

interface MomentNodeProps {
	moment: PracticeSignalOption;
	/** Draw the hairline reaching back to the moment before it, where the band is a progression. */
	railed: boolean;
	chosen: boolean;
	/** The occasion number already holding this moment, if it is not this one. */
	heldBy?: number;
	controlId: string;
	disabled: boolean;
	invalid: boolean;
	onToggle: (chosen: boolean) => void;
}

/**
 * One point on the strip: a real checkbox with a label, drawn as a node on a rail. Nothing here
 * reimplements checkbox behaviour — `htmlFor` leaves the click, the Space press and the accessible
 * name to the platform, and only the focus ring is moved onto the node a reader actually sees.
 */
function MomentNode({
	moment,
	railed,
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
				"group/moment relative flex w-24 shrink-0 flex-col items-center gap-1 px-1 pt-1 pb-0.5 text-center",
				// The rail: a hairline spanning circle edge to circle edge. Every node is the same width
				// with its circle centred, so -50% of the node plus half a circle lands on the previous
				// one. Hidden on the first node of a band, so the line never dangles.
				railed &&
					"before:absolute before:top-5 before:-left-1/2 before:right-1/2 before:mr-4 before:ml-4 before:h-px before:bg-border first:before:hidden",
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

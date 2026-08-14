import { type LucideIcon, PencilLineIcon } from "lucide-react";
import type * as React from "react";
import type { ReactNode } from "react";
import type { ReviewFeedback, ReviewPlacement } from "@/api/types.gen";
import { RelativeTime } from "@/components/common/RelativeTime";
import { cn } from "@/lib/utils";
import { type DeliveryFacts, deliveryOutcome, isWithheld } from "./delivery-outcome-defs";
import { DELIVERY_PLACE_DEFS } from "./delivery-place-defs";
import { placementLabel } from "./placement-defs";
import { StatusBadge } from "./StatusBadge";
import { statusToneClass } from "./status-def";
import {
	WITHHOLDING_FAMILY_DEFS,
	type WithholdingReason,
	withholdingFamily,
	withholdingReasonSentence,
} from "./withholding-defs";

/**
 * Everything the trace reads. Both feedback read models satisfy it as they are: the detail view
 * brings placements, the list and observation-linked views simply have none, which is the honest
 * shape for a piece of feedback that was never posted anywhere.
 */
export type DeliveryTraceFeedback = DeliveryFacts &
	Pick<ReviewFeedback, "createdAt" | "deliveredAt"> & { placements?: ReviewPlacement[] };

interface DeliveryTraceOwnProps {
	feedback: DeliveryTraceFeedback;
}

/**
 * `className` and the rest of the list's DOM props reach the root, always — a stability contract for
 * a shared component rather than a configuration knob, and so exempt from the two-caller rule.
 * https://github.com/carbon-design-system/carbon/blob/main/docs/style.md
 */
export type DeliveryTraceProps = DeliveryTraceOwnProps &
	Omit<React.ComponentProps<"ol">, keyof DeliveryTraceOwnProps>;

/**
 * What became of one piece of feedback, as the two or three things that actually happened to it.
 *
 * <p>The delivery model is four orthogonal enums, and showing four of anything is why an operator
 * could not answer "where did this go". A trace reads as a sentence with a beginning and an end
 * instead: it was composed, something may have stopped it, and it ended up somewhere. Place and
 * outcome stay on separate lines of the last step — the badge says what happened, the line under it
 * says where, and neither one ever stands in for the other.
 *
 * <p>The middle step appears only when a gate stopped it, because a step that says "nothing stopped
 * this" on every delivered row is a step nobody reads. Three steps is also why this is hand-built
 * from a border and a rounded span rather than a timeline dependency: shadcn has no Timeline
 * component, and the registry copies that call themselves one are Radix-based, while this kit is
 * Base UI.
 */
export function DeliveryTrace({ feedback, className, ...props }: DeliveryTraceProps) {
	const outcome = deliveryOutcome(feedback);
	const place = DELIVERY_PLACE_DEFS[feedback.channel];
	const placements = feedback.placements ?? [];
	// Distinct shapes, not one phrase per row. The server writes a placement per inline note, so a
	// review with four of them would otherwise repeat "As an inline note on the work" four times. How
	// many there were, and where each landed, is the list of anchors the detail page renders below.
	const where = placements.length
		? [
				...new Set(
					placements.map((placement) => placementLabel(feedback.channel, placement.placementType)),
				),
			]
		: [place.label];

	return (
		<ol className={cn("min-w-0", className)} {...props}>
			<TraceStep icon={PencilLineIcon} title="Composed">
				<RelativeTime value={feedback.createdAt} />
			</TraceStep>
			{isWithheld(feedback) && feedback.suppressionReason && (
				<GateStep reason={feedback.suppressionReason} />
			)}
			<TraceStep icon={place.icon} iconClassName={statusToneClass(outcome.badgeVariant)} last>
				<div className="flex min-w-0 flex-col items-start gap-1.5">
					<StatusBadge def={outcome} />
					<p className="text-sm text-muted-foreground">
						{where.join(" · ")}
						{/* Labelled, because `deliveredAt` survives a later replacement: on a superseded unit a
						    bare timestamp here reads as when it was replaced, and it is when it was posted. */}
						{feedback.deliveredAt && (
							<>
								{" · delivered "}
								<RelativeTime value={feedback.deliveredAt} />
							</>
						)}
					</p>
				</div>
			</TraceStep>
		</ol>
	);
}

/**
 * The family names who decided, the sentence says what they decided — both, because an operator
 * scanning several traces needs the first and an operator chasing one needs the second.
 */
function GateStep({ reason }: { reason: WithholdingReason }) {
	const family = WITHHOLDING_FAMILY_DEFS[withholdingFamily(reason)];
	return (
		<TraceStep icon={family.icon} title={family.label}>
			{withholdingReasonSentence(reason)}
		</TraceStep>
	);
}

interface TraceStepProps {
	icon: LucideIcon;
	iconClassName?: string;
	title?: string;
	last?: boolean;
	children: ReactNode;
}

function TraceStep({ icon: Icon, iconClassName, title, last = false, children }: TraceStepProps) {
	return (
		<li className="grid grid-cols-[1.5rem_1fr] gap-x-3">
			<div className="flex flex-col items-center">
				<span className="flex size-6 shrink-0 items-center justify-center rounded-full border bg-background">
					<Icon className={cn("size-3.5", iconClassName ?? "text-muted-foreground")} aria-hidden />
				</span>
				{!last && <span className="w-px flex-1 bg-border" aria-hidden />}
			</div>
			<div className={cn("min-w-0", last ? "pb-0" : "pb-4")}>
				{title && <p className="text-sm font-medium leading-6">{title}</p>}
				<div className="min-w-0 text-sm text-muted-foreground">{children}</div>
			</div>
		</li>
	);
}

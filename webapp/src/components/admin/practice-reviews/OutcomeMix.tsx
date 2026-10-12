import type { ReactNode } from "react";

import { cn } from "cn";
import { InlineLink } from "@/components/common/InlineLink";
import { statusToneClass } from "@/components/common/status-def";
import { Skeleton } from "@/components/ui/skeleton";

import { OVERVIEW_TYPE } from "./overview-type-scale";
import type { OutcomeSlot } from "./review-outcomes";
import { ReviewListLink } from "./ReviewListLink";

interface OutcomeLegendProps {
	slots: readonly OutcomeSlot[];
	/** What the counts are of, as the list's accessible name: "Feedback by delivery". */
	"aria-label": string;
	/** Given, each count with a target opens its list filtered to exactly the rows it counts. */
	workspaceSlug?: string;
	/** Drawn quiet: the icons take no colour, for a second piece of information beside the parts. */
	quiet?: boolean;
	className?: string;
}

/**
 * Each count with its registry's icon and word. Given a workspace, each count is a link to the list
 * filtered to exactly the rows it counts; without one, it is text. Zeroes are left out — a legend of
 * noughts hides the numbers that matter.
 */
function OutcomeLegend({ slots, workspaceSlug, quiet, className, ...props }: OutcomeLegendProps) {
	const shown = slots.filter((slot) => slot.count > 0);
	if (shown.length === 0) {
		return null;
	}
	return (
		<ul
			className={cn(
				"flex flex-wrap gap-x-4 gap-y-1 [&_svg]:size-3.5",
				OVERVIEW_TYPE.label,
				className,
			)}
			{...props}
		>
			{shown.map((slot) => {
				const Icon = slot.def.icon;
				const text = (
					<>
						<span className={OVERVIEW_TYPE.value}>{slot.count}</span> {slot.label}
					</>
				);
				return (
					<li key={slot.key} className="inline-flex items-center gap-1.5">
						<Icon
							aria-hidden
							className={cn(
								"shrink-0",
								quiet === true ? "text-muted-foreground" : statusToneClass(slot.def.badgeVariant),
							)}
						/>
						{workspaceSlug !== undefined && slot.target !== undefined ? (
							<InlineLink
								tone="count"
								render={<ReviewListLink workspaceSlug={workspaceSlug} destination={slot.target} />}
							>
								{text}
							</InlineLink>
						) : (
							<span>{text}</span>
						)}
					</li>
				);
			})}
		</ul>
	);
}

export interface OutcomeMixProps {
	/** Everything the figure counts, which may be more than the legend splits it into. */
	total: number;
	slots: readonly OutcomeSlot[];
	/**
	 * Counts that overlap the parts — a met result marked incorrect is still a met
	 * result — listed last in the same row, under their own name, quiet, and
	 * never added to the total. No threshold colours them: they are a correction, not an outcome.
	 */
	flags?: { label: string; slots: readonly OutcomeSlot[] };
	/** The total's noun for its count: "results", "pieces of feedback". */
	noun: (total: number) => string;
	/** The legend's accessible name. */
	label: string;
	/**
	 * The total set against the period before: "4 more than the previous 30 days". `null` while that
	 * period loads, which holds the line so the figure does not move when it lands.
	 */
	delta?: string | null;
	/** Drawn between the total and the legend: the total over time. */
	children?: ReactNode;
	/**
	 * A last item of the legend's row that is no count, such as a rate: "8 % failed or timed out".
	 * Drawn after the flags, in the same row.
	 */
	trailing?: ReactNode;
	/** The card's one link, to the list it counts, under the legend. */
	link?: ReactNode;
	/** Given, each count of the legend opens its list filtered to exactly the rows it counts. */
	workspaceSlug?: string;
}

/** A total, how it changed, then what it splits into. */
export function OutcomeMix({
	total,
	slots,
	flags,
	noun,
	label,
	delta,
	children,
	trailing,
	link,
	workspaceSlug,
}: OutcomeMixProps) {
	const shownFlags = flags?.slots.filter((slot) => slot.count > 0) ?? [];
	return (
		<div className="flex flex-col gap-3">
			<div className="space-y-1">
				<p className="flex items-baseline gap-1.5">
					<span className={cn(OVERVIEW_TYPE.hero, total === 0 && "text-muted-foreground")}>
						{total}
					</span>
					<span className={OVERVIEW_TYPE.heroNoun}>{noun(total)}</span>
				</p>
				{delta === null && <Skeleton className="h-4 w-40" />}
				{typeof delta === "string" && <p className={OVERVIEW_TYPE.aside}>{delta}</p>}
			</div>
			{children}
			{shownFlags.length === 0 && trailing === undefined ? (
				<OutcomeLegend slots={slots} aria-label={label} workspaceSlug={workspaceSlug} />
			) : (
				// One row: the parts, then what overlaps them as its quiet last items, so a wrap never
				// leaves a rule or a lone line behind.
				<div className="flex flex-wrap items-center gap-x-4 gap-y-1">
					<OutcomeLegend
						slots={slots}
						aria-label={label}
						workspaceSlug={workspaceSlug}
						className="contents"
					/>
					<OutcomeLegend
						slots={shownFlags}
						aria-label={flags?.label ?? ""}
						workspaceSlug={workspaceSlug}
						quiet
						className="contents"
					/>
					{trailing}
				</div>
			)}
			{link}
		</div>
	);
}

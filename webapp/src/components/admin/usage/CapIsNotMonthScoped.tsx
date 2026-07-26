export interface CapIsNotMonthScopedProps {
	/** What is being edited, as the reader's own word: `cap` on the workspace, `budget` on the host's. */
	subject: "cap" | "budget";
	className?: string;
}

/**
 * Why the amount editor is not on screen, said once on the surface the reader would look for it on.
 *
 * A cap takes effect the moment it is saved and applies to spend from then on — it is not a property
 * of the month being read. Offered from a closed month, "Change cap" would silently move what runs
 * today, and the estimate under its amount field ("≈ €44 at today's rate") would be quoting a rate
 * frozen inside a month the new cap has nothing to do with.
 *
 * One sentence for both consoles: the instance table's rows lose a button each and say it once above
 * them, the workspace card says it in the space its own button vacated.
 */
export function CapIsNotMonthScoped({ subject, className }: CapIsNotMonthScopedProps) {
	return (
		<p className={className ?? "text-sm text-muted-foreground"}>
			A {subject} applies from the moment it is saved, not to the month you are reading. Step
			forward to this month to change it.
		</p>
	);
}

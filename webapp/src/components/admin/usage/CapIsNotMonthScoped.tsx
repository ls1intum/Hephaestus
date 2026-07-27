export interface CapIsNotMonthScopedProps {
	/** What is being edited, as the reader's own word: `cap` on the workspace, `budget` on the host's. */
	subject: "cap" | "budget";
}

/**
 * Why the amount editor is not on screen. A cap takes effect when saved and applies from then on, so
 * offering one from a closed month would silently move what runs today.
 */
export function CapIsNotMonthScoped({ subject }: CapIsNotMonthScopedProps) {
	return (
		<p className="text-sm text-muted-foreground">
			A {subject} applies from the moment it is saved, not to the month you are reading. Step
			forward to this month to change it.
		</p>
	);
}

import { useId } from "react";

import type { Practice } from "@/api/types.gen";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";

/** What decides whether a practice is turned off: its practice setup as it is now. */
export type CurrentPractice = Pick<Practice, "slug" | "autonomy">;

/**
 * Whether a practice is turned off in this workspace today: its autonomy is Off now, whatever it was
 * when its results were made. A practice that is no longer in the workspace is turned off too.
 * While the practices load, nothing is: a list must not hide rows on a guess.
 */
export function isTurnedOff(
	slug: string,
	practices: readonly CurrentPractice[] | undefined,
): boolean {
	if (practices === undefined) {
		return false;
	}
	const practice = practices.find((candidate) => candidate.slug === slug);
	return practice === undefined || practice.autonomy.effective === "OFF";
}

export interface ShowTurnedOffSwitchProps {
	checked: boolean;
	onCheckedChange: (checked: boolean) => void;
	/** How many practices the switch hides while it is off; the label says it when there are some. */
	hidden?: number;
}

/** "Show turned-off practices", off by default, wherever practices are listed for review. */
export function ShowTurnedOffSwitch({
	checked,
	onCheckedChange,
	hidden,
}: ShowTurnedOffSwitchProps) {
	const id = useId();
	return (
		// Not a `Field`: that fills its row, and this sits beside the filters it belongs to.
		<div className="flex h-8 items-center gap-2">
			<Switch id={id} size="sm" checked={checked} onCheckedChange={onCheckedChange} />
			<Label htmlFor={id} className="font-normal">
				Show turned-off practices
				{!checked && hidden !== undefined && hidden > 0 && (
					<>
						{" "}
						<span className="text-muted-foreground">({hidden} hidden)</span>
					</>
				)}
			</Label>
		</div>
	);
}

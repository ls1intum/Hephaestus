import { useId } from "react";

import type { Practice } from "@/api/types.gen";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";

/** What decides whether a practice is turned off: its practice setup as it is now. */
export type CurrentPractice = Pick<Practice, "slug" | "feedbackMode">;

// Do not hide results while practice settings are loading; the current feedback mode determines visibility.
export function isTurnedOff(
	slug: string,
	practices: readonly CurrentPractice[] | undefined,
): boolean {
	if (practices === undefined) {
		return false;
	}
	const practice = practices.find((candidate) => candidate.slug === slug);
	return practice === undefined || practice.feedbackMode.effective === "OFF";
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
	const labelId = `${id}-label`;
	return (
		// Not a `Field`: that fills its row, and this sits beside the filters it belongs to.
		<div className="flex h-8 items-center gap-2">
			<Switch
				id={id}
				aria-labelledby={labelId}
				size="sm"
				checked={checked}
				onCheckedChange={onCheckedChange}
			/>
			<Label id={labelId} htmlFor={id} className="font-normal">
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

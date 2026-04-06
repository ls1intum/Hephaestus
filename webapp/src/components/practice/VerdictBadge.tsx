import type { PracticeFindingList } from "@/api/types.gen";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { VERDICT_STYLES } from "./verdict-styles";

export interface VerdictBadgeProps {
	verdict: PracticeFindingList["verdict"];
	className?: string;
}

export function VerdictBadge({ verdict, className }: VerdictBadgeProps) {
	const style = VERDICT_STYLES[verdict];
	return (
		<Badge className={cn(style.bgColor, style.fgColor, "border-transparent", className)}>
			{style.label}
		</Badge>
	);
}

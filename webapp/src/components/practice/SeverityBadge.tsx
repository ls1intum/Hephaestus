import type { PracticeFindingList } from "@/api/types.gen";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { SEVERITY_STYLES } from "./verdict-styles";

export interface SeverityBadgeProps {
	severity: PracticeFindingList["severity"];
	className?: string;
}

export function SeverityBadge({ severity, className }: SeverityBadgeProps) {
	const style = SEVERITY_STYLES[severity];
	return (
		<Badge className={cn(style.bgColor, style.fgColor, "border-transparent", className)}>
			{style.label}
		</Badge>
	);
}

import { formatDistanceToNow } from "date-fns";
import type { PracticeFindingList } from "@/api/types.gen";
import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import { SeverityBadge } from "./SeverityBadge";
import { VerdictBadge } from "./VerdictBadge";
import { VERDICT_STYLES } from "./verdict-styles";

export interface FindingsListItemProps {
	finding: PracticeFindingList;
	isLoading?: boolean;
}

export function FindingsListItem({ finding, isLoading = false }: FindingsListItemProps) {
	if (isLoading) {
		return (
			<li>
				<Card className="border-l-3 p-4 gap-2 flex flex-col">
					<div className="flex items-center gap-2">
						<Skeleton className="h-5 w-24" />
						<Skeleton className="h-5 w-14" />
						<Skeleton className="h-5 w-16" />
						<Skeleton className="h-4 w-20" />
					</div>
					<Skeleton className="h-5 w-3/4" />
				</Card>
			</li>
		);
	}

	const verdictStyle = VERDICT_STYLES[finding.verdict];
	const relativeTime = formatDistanceToNow(finding.detectedAt, { addSuffix: true });

	return (
		<li>
			<Card className={cn("border-l-3 p-4 gap-1.5 flex flex-col", verdictStyle.borderColor)}>
				{/* Metadata row */}
				<div className="flex flex-wrap items-center gap-2 text-sm">
					<Badge variant="outline" className="text-xs">
						{finding.practiceName}
					</Badge>
					<VerdictBadge verdict={finding.verdict} />
					<SeverityBadge severity={finding.severity} />
					<span className="text-muted-foreground text-xs">{relativeTime}</span>
				</div>
				{/* Title */}
				<span className="font-medium text-sm leading-snug">{finding.title}</span>
			</Card>
		</li>
	);
}

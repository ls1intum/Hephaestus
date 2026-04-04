import { useQuery } from "@tanstack/react-query";
import { formatDistanceToNow } from "date-fns";
import { ChevronDown, ChevronUp } from "lucide-react";
import { useState } from "react";
import { getFindingOptions } from "@/api/@tanstack/react-query.gen";
import type { PracticeFindingDetail, PracticeFindingList } from "@/api/types.gen";
import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import { FeedbackBadge } from "./FeedbackBadge";
import { formatTargetLabel, GUIDANCE_METHOD_LABELS, parseEvidence } from "./finding-helpers";
import { SeverityBadge } from "./SeverityBadge";
import { VerdictBadge } from "./VerdictBadge";
import { VERDICT_STYLES } from "./verdict-styles";

export interface FindingsListItemProps {
	finding: PracticeFindingList;
	workspaceSlug?: string;
	isLoading?: boolean;
}

export function FindingsListItem({
	finding,
	workspaceSlug,
	isLoading = false,
}: FindingsListItemProps) {
	const [isOpen, setIsOpen] = useState(false);

	const detailQuery = useQuery({
		...getFindingOptions({
			path: { workspaceSlug: workspaceSlug ?? "", findingId: finding.id },
		}),
		enabled: isOpen && !!workspaceSlug && !!finding.id,
		staleTime: 60_000,
	});

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
	const hasDetail = !!workspaceSlug;

	return (
		<li>
			<Collapsible open={isOpen} onOpenChange={setIsOpen}>
				<Card className={cn("border-l-3 flex flex-col", verdictStyle.borderColor)}>
					<CollapsibleTrigger
						className={cn(
							"w-full p-4 gap-1.5 flex flex-col text-left",
							hasDetail && "cursor-pointer hover:bg-accent/30 transition-colors",
						)}
						disabled={!hasDetail}
					>
						<div className="flex flex-wrap items-center gap-2 text-sm">
							<Badge variant="outline" className="text-xs">
								{finding.practiceName}
							</Badge>
							<VerdictBadge verdict={finding.verdict} />
							<SeverityBadge severity={finding.severity} />
							<span className="text-muted-foreground text-xs">{relativeTime}</span>
							{hasDetail && (
								<span className="ml-auto text-muted-foreground">
									{isOpen ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
								</span>
							)}
						</div>
						<span className="font-medium text-sm leading-snug">{finding.title}</span>
					</CollapsibleTrigger>

					<CollapsibleContent>
						<div className="px-4 pb-4 pt-1 flex flex-col gap-3 border-t">
							{detailQuery.isPending ? (
								<div className="flex flex-col gap-2 pt-2">
									<Skeleton className="h-4 w-full" />
									<Skeleton className="h-4 w-5/6" />
									<Skeleton className="h-4 w-2/3" />
								</div>
							) : detailQuery.isError ? (
								<p className="text-sm text-muted-foreground pt-2">Failed to load details.</p>
							) : (
								<FindingDetail
									detail={detailQuery.data}
									finding={finding}
									workspaceSlug={workspaceSlug}
								/>
							)}
						</div>
					</CollapsibleContent>
				</Card>
			</Collapsible>
		</li>
	);
}

/** Expanded detail section showing guidance, evidence, reasoning, and feedback status. */
function FindingDetail({
	detail,
	finding,
	workspaceSlug,
}: {
	detail?: PracticeFindingDetail;
	finding: PracticeFindingList;
	workspaceSlug?: string;
}) {
	const evidence = detail ? parseEvidence(detail.evidence) : null;

	return (
		<>
			{detail?.guidance && (
				<div className="flex flex-col gap-1 pt-2">
					<span className="text-xs font-medium text-muted-foreground uppercase tracking-wide">
						Guidance
						{detail.guidanceMethod && (
							<span className="ml-1.5 normal-case tracking-normal font-normal">
								({GUIDANCE_METHOD_LABELS[detail.guidanceMethod]})
							</span>
						)}
					</span>
					<p className="text-sm leading-relaxed">{detail.guidance}</p>
				</div>
			)}

			{evidence && evidence.locations.length > 0 && (
				<div className="flex flex-col gap-1">
					<span className="text-xs font-medium text-muted-foreground uppercase tracking-wide">
						Evidence
					</span>
					<ul className="text-sm space-y-0.5">
						{evidence.locations.map((loc, index) => (
							<li
								key={`${loc.path}:${loc.startLine}:${index}`}
								className="font-mono text-xs text-muted-foreground"
							>
								{loc.path}
								{loc.startLine != null && `:${loc.startLine}`}
								{loc.endLine != null && `-${loc.endLine}`}
							</li>
						))}
					</ul>
				</div>
			)}

			{evidence && evidence.snippets.length > 0 && (
				<div className="flex flex-col gap-1">
					<span className="text-xs font-medium text-muted-foreground uppercase tracking-wide">
						Snippets
					</span>
					{evidence.snippets.map((snippet, index) => (
						<pre key={index} className="text-xs bg-muted/50 rounded p-2 overflow-x-auto font-mono">
							{snippet}
						</pre>
					))}
				</div>
			)}

			{detail?.reasoning && (
				<div className="flex flex-col gap-1">
					<span className="text-xs font-medium text-muted-foreground uppercase tracking-wide">
						Reasoning
					</span>
					<p className="text-sm text-muted-foreground leading-relaxed">{detail.reasoning}</p>
				</div>
			)}

			<div className="flex items-center gap-2 text-xs text-muted-foreground">
				<span>
					{formatTargetLabel(finding.targetType)} #{finding.targetId}
				</span>
				{workspaceSlug && <FeedbackBadge workspaceSlug={workspaceSlug} findingId={finding.id} />}
			</div>
		</>
	);
}

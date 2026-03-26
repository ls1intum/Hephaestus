import { useQuery } from "@tanstack/react-query";
import { Check, MessageSquare, X } from "lucide-react";
import { getLatestFeedbackOptions } from "@/api/@tanstack/react-query.gen";
import type { FindingFeedback } from "@/api/types.gen";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";

const FEEDBACK_DISPLAY: Record<
	FindingFeedback["action"],
	{ label: string; icon: typeof Check; className: string }
> = {
	APPLIED: {
		label: "Applied",
		icon: Check,
		className: "text-provider-success-foreground bg-provider-success",
	},
	DISPUTED: {
		label: "Disputed",
		icon: MessageSquare,
		className: "text-provider-attention-foreground bg-provider-attention",
	},
	NOT_APPLICABLE: {
		label: "N/A",
		icon: X,
		className: "text-provider-muted-foreground bg-provider-muted",
	},
};

export interface FeedbackBadgeProps {
	workspaceSlug: string;
	findingId: string;
}

/** Read-only badge showing the latest feedback status for a finding. */
export function FeedbackBadge({ workspaceSlug, findingId }: FeedbackBadgeProps) {
	const { data: feedback } = useQuery({
		...getLatestFeedbackOptions({
			path: { workspaceSlug, findingId },
		}),
		staleTime: 60_000,
	});

	if (!feedback?.action) return null;

	const display = FEEDBACK_DISPLAY[feedback.action];
	const Icon = display.icon;

	return (
		<Badge variant="outline" className={cn("text-xs gap-1", display.className)}>
			<Icon className="h-3 w-3" />
			{display.label}
		</Badge>
	);
}

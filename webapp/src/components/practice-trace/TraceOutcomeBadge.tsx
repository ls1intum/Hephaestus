import {
	BellOffIcon,
	CircleAlertIcon,
	CircleCheckIcon,
	CircleDashedIcon,
	CircleHelpIcon,
	CircleSlashIcon,
	ClockIcon,
	HourglassIcon,
	LoaderIcon,
	UnplugIcon,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { OUTCOME_LABELS, type TraceOutcome } from "./trace-format";

type BadgeVariant = "success" | "secondary" | "outline" | "warning" | "destructive";

/**
 * Colour is the last channel, never the only one: every outcome also carries its own icon and its
 * own written label, so the badge still distinguishes itself in greyscale, under a colour-vision
 * deficiency, and to a screen reader reading the text alone (WCAG 2.2 SC 1.4.1).
 */
const OUTCOME_BADGE = {
	REVIEWED: { variant: "success", Icon: CircleCheckIcon },
	RUNNING: { variant: "secondary", Icon: LoaderIcon },
	PENDING: { variant: "secondary", Icon: ClockIcon },
	SKIPPED: { variant: "outline", Icon: CircleSlashIcon },
	NOT_ASSESSABLE: { variant: "warning", Icon: CircleHelpIcon },
	SILENCED: { variant: "outline", Icon: BellOffIcon },
	NOT_OCCASIONED: { variant: "outline", Icon: CircleDashedIcon },
	DORMANT: { variant: "warning", Icon: UnplugIcon },
	LAPSED: { variant: "outline", Icon: HourglassIcon },
	FAILED: { variant: "destructive", Icon: CircleAlertIcon },
} as const satisfies Record<TraceOutcome, { variant: BadgeVariant; Icon: typeof CircleCheckIcon }>;

export interface TraceOutcomeBadgeProps {
	outcome: TraceOutcome;
	className?: string;
}

/** What became of one practice on one piece of work, in one glanceable token. */
export function TraceOutcomeBadge({ outcome, className }: TraceOutcomeBadgeProps) {
	const { variant, Icon } = OUTCOME_BADGE[outcome];
	return (
		<Badge variant={variant} className={cn("max-w-full", className)}>
			<Icon aria-hidden />
			<span className="truncate">{OUTCOME_LABELS[outcome]}</span>
		</Badge>
	);
}

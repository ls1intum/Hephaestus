import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import type { StatusDef } from "./status-def";

export interface StatusBadgeProps {
	/**
	 * The registry entry, not a label and a variant and an icon. A caller that can pass three
	 * scalars can pass three that do not belong together — a "Delivered" label wearing the
	 * destructive variant — and nothing would catch it.
	 */
	def: StatusDef;
	className?: string;
}

/**
 * The one badge every practice-review status renders through.
 *
 * It takes a whole registry entry so the words, the colour and the icon travel together and cannot
 * be recombined at a call site. Per-enum wrappers (`DeliveryOutcomeBadge`, `SeverityBadge`) exist
 * only to pick the entry; none of them re-decide how a badge looks.
 */
export function StatusBadge({ def, className }: StatusBadgeProps) {
	const { icon: Icon, label, badgeVariant } = def;
	return (
		<Badge variant={badgeVariant} className={cn("max-w-full", className)}>
			<Icon aria-hidden />
			<span className="truncate">{label}</span>
		</Badge>
	);
}

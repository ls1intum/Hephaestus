import type * as React from "react";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import type { StatusDef } from "./status-def";

interface StatusBadgeOwnProps {
	/**
	 * The registry entry, not a label and a variant and an icon. A caller that can pass three
	 * scalars can pass three that do not belong together — a "Delivered" label wearing the
	 * destructive variant — and nothing would catch it.
	 */
	def: StatusDef;
}

/**
 * `className` and the rest of the DOM props reach the root, always. It is a stability contract for a
 * shared component rather than a configuration knob, so it is exempt from the two-caller rule — a
 * screen that needs one width or one margin here must not have to fork the component to get it.
 * https://github.com/carbon-design-system/carbon/blob/main/docs/style.md
 */
export type StatusBadgeProps = StatusBadgeOwnProps &
	Omit<React.ComponentProps<typeof Badge>, "variant" | keyof StatusBadgeOwnProps>;

/**
 * The one badge every practice-review status renders through.
 *
 * It takes a whole registry entry so the words, the colour and the icon travel together and cannot
 * be recombined at a call site. Per-enum wrappers (`DeliveryOutcomeBadge`, `SeverityBadge`) exist
 * only to pick the entry; none of them re-decide how a badge looks.
 */
export function StatusBadge({ def, className, ...props }: StatusBadgeProps) {
	const { icon: Icon, label, badgeVariant } = def;
	return (
		<Badge variant={badgeVariant} className={cn("max-w-full", className)} {...props}>
			<Icon aria-hidden />
			<span className="truncate">{label}</span>
		</Badge>
	);
}

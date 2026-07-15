import type { VariantProps } from "class-variance-authority";
import { Badge, type badgeVariants } from "@/components/ui/badge";
import { type ConnectionHealth, HEALTH_LABEL } from "./sync-format";

type BadgeVariant = NonNullable<VariantProps<typeof badgeVariants>["variant"]>;

const HEALTH_VARIANT: Record<ConnectionHealth, BadgeVariant> = {
	PENDING: "secondary",
	HEALTHY: "success",
	DEGRADED: "warning",
	FAILED: "destructive",
	SUSPENDED: "outline",
};

export interface ConnectionHealthBadgeProps {
	health: ConnectionHealth;
	className?: string;
}

/** Derived connection health, badge-coded: healthy=success, degraded=warning, failed=destructive,
 * pending=secondary, suspended=outline. The one place this mapping lives. */
export function ConnectionHealthBadge({ health, className }: ConnectionHealthBadgeProps) {
	return (
		<Badge
			variant={HEALTH_VARIANT[health]}
			className={className}
			// Live region so screen readers hear health transitions (e.g. Healthy -> Degraded)
			// as they arrive over the live-push stream, not only on a manual re-read.
			role="status"
			aria-live="polite"
			aria-label={`Connection health: ${HEALTH_LABEL[health]}`}
		>
			{HEALTH_LABEL[health]}
		</Badge>
	);
}

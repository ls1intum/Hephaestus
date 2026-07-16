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
	/** A running job supersedes a stale last-failed health so the badge reads "Syncing", not "Failed". */
	isSyncing?: boolean;
}

export function ConnectionHealthBadge({ health, isSyncing = false }: ConnectionHealthBadgeProps) {
	const variant: BadgeVariant = isSyncing ? "secondary" : HEALTH_VARIANT[health];
	const label = isSyncing ? "Syncing" : HEALTH_LABEL[health];
	return (
		<Badge
			variant={variant}
			role="status"
			aria-live="polite"
			aria-label={`Connection health: ${label}`}
		>
			{label}
		</Badge>
	);
}

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

export function ConnectionHealthBadge({ health, className }: ConnectionHealthBadgeProps) {
	return (
		<Badge
			variant={HEALTH_VARIANT[health]}
			className={className}
			role="status"
			aria-live="polite"
			aria-label={`Connection health: ${HEALTH_LABEL[health]}`}
		>
			{HEALTH_LABEL[health]}
		</Badge>
	);
}

import { Badge } from "@/components/ui/badge";
import { type Currentness, NON_CURRENT } from "./claim-currentness";

export function ClaimCurrentnessBadge({ currentness }: { currentness: Currentness }) {
	if (currentness === "CURRENT") {
		return null;
	}
	const config = NON_CURRENT[currentness];
	return <Badge variant={config.badgeVariant}>{config.badge}</Badge>;
}

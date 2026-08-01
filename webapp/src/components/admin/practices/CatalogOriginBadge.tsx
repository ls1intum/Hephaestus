import type { CatalogOrigin } from "@/api/types.gen";
import { Badge } from "@/components/ui/badge";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";

/**
 * How a workspace's practice or area stands against the instance catalog entry it was copied from.
 *
 * <p>A workspace's copies are its own and the instance never rewrites them. Showing this is what
 * makes that a choice rather than a surprise: the workspace can see it is running an older
 * definition, and see when the instance has stopped offering the entry at all.
 */
export interface CatalogOriginBadgeProps {
	origin?: CatalogOrigin | null;
	kind: "practice" | "area";
}

export function CatalogOriginBadge({ origin, kind }: CatalogOriginBadgeProps) {
	if (!origin || origin.link === "LOCAL") {
		return null;
	}
	const label =
		origin.link === "IN_SYNC"
			? "From the catalog"
			: origin.link === "UPDATE_AVAILABLE"
				? "Catalog has a newer version"
				: "Edited here";
	const explanation =
		origin.link === "IN_SYNC"
			? `This ${kind} is exactly what the instance catalog offers.`
			: origin.link === "UPDATE_AVAILABLE"
				? `Nobody has changed this ${kind} here, and the instance now offers a different version.`
				: `This ${kind} started from the instance catalog and has since been changed here.`;
	const retired = origin.sourceOffered
		? ""
		: " The instance no longer offers it to new workspaces; yours is unaffected.";

	return (
		<Tooltip>
			<TooltipTrigger
				render={
					<Badge variant={origin.link === "UPDATE_AVAILABLE" ? "warning" : "outline"}>
						{label}
					</Badge>
				}
			/>
			<TooltipContent>
				{explanation}
				{retired}
			</TooltipContent>
		</Tooltip>
	);
}

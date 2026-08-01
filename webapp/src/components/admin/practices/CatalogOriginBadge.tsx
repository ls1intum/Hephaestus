import type { CatalogOrigin } from "@/api/types.gen";
import { Badge } from "@/components/ui/badge";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";

/**
 * How a workspace's practice or area stands against the instance catalog entry it was copied from.
 *
 * <p>A workspace's copies are its own and the instance never rewrites them. Showing this is what
 * makes that a choice rather than a surprise: the workspace can see it is running an older
 * definition, and see when the instance has stopped offering the entry at all.
 *
 * <p>What is compared is the detection fingerprint, not the whole definition — editing only the
 * text developers read keeps the match. The copy says that rather than claiming nothing changed.
 */
export interface CatalogOriginBadgeProps {
	origin?: CatalogOrigin | null;
	kind: "practice" | "area";
}

export function CatalogOriginBadge({ origin, kind }: CatalogOriginBadgeProps) {
	if (!origin || origin.link === "LOCAL") {
		return null;
	}
	// An entry the instance has stopped offering has no newer version to take, whatever the
	// fingerprints say. Claiming both at once would contradict itself.
	if (!origin.sourceOffered) {
		return (
			<Tooltip>
				<TooltipTrigger render={<Badge variant="outline">No longer in the catalog</Badge>} />
				<TooltipContent>
					{`The instance has stopped offering this ${kind} to new workspaces. Yours keeps running, unchanged.`}
				</TooltipContent>
			</Tooltip>
		);
	}

	const label =
		origin.link === "IN_SYNC"
			? "From the catalog"
			: origin.link === "UPDATE_AVAILABLE"
				? "Catalog has a newer version"
				: "Edited in this workspace";
	const explanation =
		origin.link === "IN_SYNC"
			? `Everything this ${kind} detects matches what the instance catalog offers.`
			: origin.link === "UPDATE_AVAILABLE"
				? `The instance now offers a different version. Nothing here changes unless you edit it.`
				: `This ${kind} started from the instance catalog, and what it detects has since been changed here.`;

	return (
		<Tooltip>
			<TooltipTrigger
				render={
					<Badge variant={origin.link === "UPDATE_AVAILABLE" ? "warning" : "outline"}>
						{label}
					</Badge>
				}
			/>
			<TooltipContent>{explanation}</TooltipContent>
		</Tooltip>
	);
}

import type { CatalogOrigin } from "@/api/types.gen";
import { badgeVariants } from "@/components/ui/badge";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";

export interface CatalogOriginBadgeProps {
	origin?: CatalogOrigin | null;
	kind: "practice" | "area";
}

export function CatalogOriginBadge({ origin, kind }: CatalogOriginBadgeProps) {
	if (!origin || (origin.link === "IN_SYNC" && origin.sourceOffered)) {
		return null;
	}
	if (!origin.sourceOffered) {
		return (
			<Tooltip>
				<TooltipTrigger className={badgeVariants({ variant: "outline" })}>
					No longer included in new workspaces
				</TooltipTrigger>
				<TooltipContent>
					{`The instance catalog no longer includes this ${kind} in new workspaces. This workspace's version remains unchanged.`}
				</TooltipContent>
			</Tooltip>
		);
	}

	const isUpdate = origin.link === "UPDATE_AVAILABLE";
	const subject = kind === "practice" ? "review rules" : "area details";
	const label = isUpdate ? "Instance catalog changed" : "Customized for this workspace";
	const explanation = isUpdate
		? `The instance catalog now has different ${subject}. This workspace keeps its current version.`
		: `The ${subject} differ from the version copied into this workspace.`;

	return (
		<Tooltip>
			<TooltipTrigger className={badgeVariants({ variant: "outline" })}>{label}</TooltipTrigger>
			<TooltipContent>{explanation}</TooltipContent>
		</Tooltip>
	);
}

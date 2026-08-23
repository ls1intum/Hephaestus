import type { CatalogOrigin } from "@/api/types.gen";
import { badgeVariants } from "@/components/ui/badge";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { cn } from "@/lib/utils";

export interface CatalogOriginBadgeProps {
	origin?: CatalogOrigin | null;
	kind: "practice" | "area";
	className?: string;
}

/**
 * How a workspace copy stands against the catalog entry it came from.
 *
 * Every one of these says the same underlying thing, because a copy never tracks its source:
 * `UPDATE_AVAILABLE` is computed for display and nothing in the product applies it, and the one-time
 * install is guarded by an installation record so it never runs twice. The wording is explicit about
 * that, because "the catalog changed" alone invites the opposite reading — that the copy is about to
 * change too.
 *
 * A copy with no provenance at all renders nothing, which is a fourth state; the matching case is
 * named rather than silent so the two are distinguishable.
 */
export function CatalogOriginBadge({ origin, kind, className }: CatalogOriginBadgeProps) {
	if (!origin) {
		return null;
	}
	// `kind` is the code word; these are the two words the reader sees.
	const subject = kind === "practice" ? "review rules" : "group details";
	const noun = kind === "practice" ? "practice" : "group";

	if (!origin.sourceOffered) {
		return (
			<OriginBadge
				className={className}
				label="No longer in the catalog"
				explanation={`New workspaces no longer receive this ${noun}. Yours keeps working exactly as it is.`}
			/>
		);
	}
	if (origin.link === "UPDATE_AVAILABLE") {
		return (
			<OriginBadge
				className={className}
				label="Catalog changed, yours did not"
				explanation={`The catalog now has different ${subject}. Your copy is untouched — bring anything you want across by editing it.`}
			/>
		);
	}
	if (origin.link === "IN_SYNC") {
		return (
			<OriginBadge
				className={className}
				label="Same as the catalog"
				explanation={`These ${subject} still match the entry this was copied from. It will stay that way: the catalog never edits your copy.`}
			/>
		);
	}
	return (
		<OriginBadge
			className={className}
			label="Edited here"
			explanation={`The ${subject} differ from the version copied into this workspace.`}
		/>
	);
}

function OriginBadge({
	label,
	explanation,
	className,
}: {
	label: string;
	explanation: string;
	className?: string;
}) {
	return (
		<Tooltip>
			<TooltipTrigger className={cn(badgeVariants({ variant: "outline" }), className)}>
				{label}
			</TooltipTrigger>
			<TooltipContent>{explanation}</TooltipContent>
		</Tooltip>
	);
}

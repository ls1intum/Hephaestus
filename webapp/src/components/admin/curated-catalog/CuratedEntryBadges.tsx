import type { CatalogEntryStatus } from "@/api/types.gen";
import { Badge } from "@/components/ui/badge";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { curatedEntryCopy, isOrdinary } from "./curated-entry-state";

const TONE_VARIANT = { neutral: "outline", info: "secondary", attention: "warning" } as const;

export interface CuratedEntryBadgesProps {
	status: CatalogEntryStatus;
	kind: "practice" | "area";
}

/**
 * How an entry stands, badged only where that is worth saying. An entry that follows Hephaestus and
 * is offered gets no badge at all: it is the ordinary case, and labelling every row with it would
 * bury the few rows that actually want a decision.
 */
export function CuratedEntryBadges({ status, kind }: CuratedEntryBadgesProps) {
	if (isOrdinary(status)) {
		return null;
	}
	const copy = curatedEntryCopy(status, kind);
	return (
		<>
			{!status.offered && <Badge variant="outline">Not offered</Badge>}
			{status.state !== "FROM_HEPHAESTUS" && (
				<Tooltip>
					<TooltipTrigger render={<Badge variant={TONE_VARIANT[copy.tone]}>{copy.label}</Badge>} />
					<TooltipContent>{copy.detail}</TooltipContent>
				</Tooltip>
			)}
		</>
	);
}

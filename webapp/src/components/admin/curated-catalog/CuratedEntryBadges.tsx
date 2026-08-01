import type { CatalogEntryStatus } from "@/api/types.gen";
import { Badge } from "@/components/ui/badge";
import { curatedEntryCopy } from "./curated-entry-state";

const TONE_VARIANT = { neutral: "outline", info: "secondary", attention: "warning" } as const;

export interface CuratedEntryBadgesProps {
	status: CatalogEntryStatus;
	kind: "practice" | "area";
}

export function CuratedEntryBadges({ status, kind }: CuratedEntryBadgesProps) {
	const copy = curatedEntryCopy(status, kind);
	return (
		<>
			<Badge variant={status.offered ? "success" : "secondary"}>
				{status.offered ? "Offered" : "Not offered"}
			</Badge>
			<Badge variant={TONE_VARIANT[copy.tone]}>{copy.label}</Badge>
		</>
	);
}

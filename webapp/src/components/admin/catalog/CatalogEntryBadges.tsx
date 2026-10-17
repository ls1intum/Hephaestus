import { Badge } from "@/components/ui/badge";

import { type CatalogEntryStatus, catalogEntryCopy, isOrdinary } from "./catalog-entry-state";

const TONE_VARIANT = { neutral: "outline", info: "secondary", attention: "warning" } as const;

export interface CatalogEntryBadgesProps {
	status: CatalogEntryStatus;
	kind: "practice" | "group";
}

export function CatalogEntryBadges({ status, kind }: CatalogEntryBadgesProps) {
	if (isOrdinary(status)) {
		return null;
	}
	const copy = catalogEntryCopy(status, kind);
	return (
		<>
			{status.excluded && <Badge variant="outline">Excluded</Badge>}
			{status.state !== "FROM_HEPHAESTUS" && (
				<Badge variant={TONE_VARIANT[copy.tone]}>{copy.label}</Badge>
			)}
		</>
	);
}

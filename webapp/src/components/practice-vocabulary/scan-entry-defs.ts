import {
	CircleSlashIcon,
	ClockIcon,
	EyeOffIcon,
	FileCheck2Icon,
	FileXIcon,
	RecycleIcon,
} from "lucide-react";

import type { StatusDef, StatusDefs } from "@/components/common/status-def";

/**
 * Where one file stands against one practice in a scan’s report. The generated item types are
 * circular, so the values are written out here, as the server’s report item `status` names them.
 */
export type ScanEntryStatus = "JUDGED" | "REUSED" | "WITHHELD" | "LISTED" | "NOT_REVIEWED";
export const SCAN_ENTRY_STATUSES = [
	"JUDGED",
	"REUSED",
	"WITHHELD",
	"LISTED",
	"NOT_REVIEWED",
] as const satisfies readonly ScanEntryStatus[];

/**
 * Reviewed, reused, withheld and not reviewed are disjoint. Only the first two carry a result:
 * "Not reviewed" never means met, and "Withheld" is a decision with a reason, not a result.
 */
export const SCAN_ENTRY_DEFS: StatusDefs<ScanEntryStatus> = {
	JUDGED: {
		label: "Reviewed",
		icon: FileCheck2Icon,
		badgeVariant: "secondary",
		description: "Reviewed in this scan. The outcome beside it is this scan’s result.",
	},
	REUSED: {
		label: "Reused",
		icon: RecycleIcon,
		badgeVariant: "secondary",
		description:
			"The file and the practice are unchanged since an earlier review, so this scan reused its result.",
	},
	WITHHELD: {
		label: "Withheld",
		icon: EyeOffIcon,
		badgeVariant: "warning",
		description: "Not reviewed, for the recorded reason. This is not a result.",
	},
	LISTED: {
		label: "Waiting",
		icon: ClockIcon,
		badgeVariant: "outline",
		description: "Listed for review. The scan has not reached it yet.",
	},
	NOT_REVIEWED: {
		label: "Not reviewed",
		icon: CircleSlashIcon,
		badgeVariant: "outline",
		description: "The scan ended before this file was reviewed. This is not a result.",
	},
};

/** A file the repository no longer has: report history, never a fixed problem. */
export const REMOVED_FILE_DEF: StatusDef = {
	label: "Removed",
	icon: FileXIcon,
	badgeVariant: "outline",
	description: "The file is no longer in the repository. Earlier results stay as history.",
};

export const NOT_REVIEWED_SENTENCE = "The scan ended before this file was reviewed.";

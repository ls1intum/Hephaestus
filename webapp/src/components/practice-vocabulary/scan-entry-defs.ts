import {
	CircleSlashIcon,
	ClockIcon,
	EyeOffIcon,
	FileCheck2Icon,
	FileXIcon,
	RecycleIcon,
} from "lucide-react";

import type { ScanExcludedFile } from "@/api/types.gen";
import type { StatusDef, StatusDefs } from "@/components/common/status-def";

// File status describes one practice-file pair in the scan report.
export type ScanEntryStatus = "JUDGED" | "REUSED" | "WITHHELD" | "LISTED" | "NOT_REVIEWED";
export const SCAN_ENTRY_STATUSES = [
	"JUDGED",
	"REUSED",
	"WITHHELD",
	"LISTED",
	"NOT_REVIEWED",
] as const satisfies readonly ScanEntryStatus[];

// Only reviewed and reused pairs carry this scan's result; withheld and not reviewed imply no verdict.
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
		description:
			"Admission refused the candidate result for the recorded reason. No result was recorded.",
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

// Removed files remain report history; removal does not establish a fixed problem.
export const REMOVED_FILE_DEF: StatusDef = {
	label: "Removed",
	icon: FileXIcon,
	badgeVariant: "outline",
	description: "The file is no longer in the repository. Earlier results stay as history.",
};

export const NOT_REVIEWED_SENTENCE = "The scan ended before this file was reviewed.";

// Excluded files were never eligible, so they do not keep a scan from being complete.
export const SCAN_EXCLUSION_SENTENCES = {
	FILE_TOO_LARGE: "Larger than the 2 MiB a review can read, so no practice reviews them.",
	BINARY_FILE: "Binary files, so no practice reviews them.",
} as const satisfies Record<ScanExcludedFile["reason"], string>;

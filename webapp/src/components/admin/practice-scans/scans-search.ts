import { z } from "zod";

import { PROBLEM_BASELINE_STATES } from "@/components/practice-vocabulary/scan-baseline-defs";
import {
	type ScanEntryStatus,
	SCAN_ENTRY_STATUSES,
} from "@/components/practice-vocabulary/scan-entry-defs";

export const scansSearchSchema = z.object({
	page: z.coerce.number().int().min(0).optional().catch(undefined),
});

export const SCAN_REPORT_TABS = ["problems", "files", "practices"] as const;
export type ScanReportTab = (typeof SCAN_REPORT_TABS)[number];

export const SCAN_FILE_FILTERS = [
	"JUDGED",
	"REUSED",
	"WITHHELD",
	"NOT_REVIEWED",
	"LISTED",
] as const satisfies readonly ScanEntryStatus[];

export const scanReportSearchSchema = z.object({
	tab: z.enum(SCAN_REPORT_TABS).optional().catch(undefined),
	baseline: z.enum(PROBLEM_BASELINE_STATES).optional().catch(undefined),
	fileStatus: z.enum(SCAN_ENTRY_STATUSES).optional().catch(undefined),
});

import { z } from "zod";

import {
	type DetailStackEntry,
	detailStackSchema,
} from "@/components/layout/detail-drawer/detail-stack";
import { PROBLEM_BASELINE_STATES } from "@/components/practice-vocabulary/scan-baseline-defs";
import {
	type ScanEntryStatus,
	SCAN_ENTRY_STATUSES,
} from "@/components/practice-vocabulary/scan-entry-defs";

/** The one level the scans list opens: the start form. A scan's report is a page, not a level. */
export const SCAN_LEVEL_KINDS = ["scan-new"] as const;
export type ScanLevelKind = (typeof SCAN_LEVEL_KINDS)[number];

/** The start form holds a draft, so leaving it asks first — `webapp/AGENTS.md` § Guarded levels. */
export const GUARDED_SCAN_LEVEL_KINDS = ["scan-new"] as const satisfies readonly ScanLevelKind[];

/** A scan that has not started has no id yet. */
export function startScanLevel(): DetailStackEntry<ScanLevelKind> {
	return { kind: "scan-new", id: "draft" };
}

export const scansSearchSchema = z.object({
	...detailStackSchema(SCAN_LEVEL_KINDS).shape,
	page: z.coerce.number().int().min(0).optional().catch(undefined),
});

export const SCAN_REPORT_TABS = ["problems", "files", "practices"] as const;
export type ScanReportTab = (typeof SCAN_REPORT_TABS)[number];

/** The file statuses a reader can filter by. "Waiting" is only ever seen while the scan runs. */
export const SCAN_FILE_FILTERS = [
	"JUDGED",
	"REUSED",
	"WITHHELD",
	"NOT_REVIEWED",
	"LISTED",
] as const satisfies readonly ScanEntryStatus[];

/** What the report's address holds: the tab, and one filter per list. Anything malformed is dropped. */
export const scanReportSearchSchema = z.object({
	tab: z.enum(SCAN_REPORT_TABS).optional().catch(undefined),
	baseline: z.enum(PROBLEM_BASELINE_STATES).optional().catch(undefined),
	fileStatus: z.enum(SCAN_ENTRY_STATUSES).optional().catch(undefined),
});

export type ScanReportSearch = z.infer<typeof scanReportSearchSchema>;

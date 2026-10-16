import { z } from "zod";

import { positiveIdParam } from "@/components/admin/practice-reviews/review-selection";
import { statusValues } from "@/components/common/status-def";
import { REVIEW_STATUS_DEFS } from "@/components/practice-vocabulary/review-status-defs";
import { PROBLEM_BASELINE_STATES } from "@/components/practice-vocabulary/scan-baseline-defs";
import {
	type ScanEntryStatus,
	SCAN_ENTRY_STATUSES,
} from "@/components/practice-vocabulary/scan-entry-defs";

export const scansSearchSchema = z.object({
	page: z.coerce.number().int().min(0).optional().catch(undefined),
	status: z.enum(statusValues(REVIEW_STATUS_DEFS)).optional().catch(undefined),
	/** The workspace item of the repository, as the scan list's own filter takes it. */
	repositoryItemId: positiveIdParam,
});

/** The server's own scan-list filters; a filter left out lists every scan. */
export type ScanListFilters = Pick<
	z.infer<typeof scansSearchSchema>,
	"status" | "repositoryItemId"
>;

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

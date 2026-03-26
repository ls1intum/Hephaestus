import type { PracticeFindingList } from "@/api/types.gen";

/** Valid verdict filter values for runtime validation. */
export const VERDICT_FILTER_VALUES = ["ALL", "POSITIVE", "NEGATIVE"] as const;

/** Filter type used across practice components for verdict-based filtering. */
export type VerdictFilter = (typeof VERDICT_FILTER_VALUES)[number];

/** Type predicate for safe runtime validation of VerdictFilter values. */
export function isVerdictFilter(value: unknown): value is VerdictFilter {
	return typeof value === "string" && (VERDICT_FILTER_VALUES as readonly string[]).includes(value);
}

type Verdict = PracticeFindingList["verdict"];
type Severity = PracticeFindingList["severity"];

interface VerdictStyle {
	label: string;
	fgColor: string;
	bgColor: string;
	borderColor: string;
}

interface SeverityStyle {
	label: string;
	fgColor: string;
	bgColor: string;
}

export const VERDICT_STYLES: Record<Verdict, VerdictStyle> = {
	POSITIVE: {
		label: "Positive",
		fgColor: "text-provider-success-foreground",
		bgColor: "bg-provider-success",
		borderColor: "border-l-provider-success-foreground",
	},
	NEGATIVE: {
		label: "Negative",
		fgColor: "text-provider-danger-foreground",
		bgColor: "bg-provider-danger",
		borderColor: "border-l-provider-danger-foreground",
	},
	NOT_APPLICABLE: {
		label: "N/A",
		fgColor: "text-provider-muted-foreground",
		bgColor: "bg-provider-muted",
		borderColor: "border-l-provider-muted-foreground",
	},
	NEEDS_REVIEW: {
		label: "Needs Review",
		fgColor: "text-provider-attention-foreground",
		bgColor: "bg-provider-attention",
		borderColor: "border-l-provider-attention-foreground",
	},
};

export const SEVERITY_STYLES: Record<Severity, SeverityStyle> = {
	CRITICAL: {
		label: "Critical",
		fgColor: "text-provider-danger-foreground",
		bgColor: "bg-provider-danger",
	},
	MAJOR: {
		label: "Major",
		fgColor: "text-provider-severe-foreground",
		bgColor: "bg-provider-severe",
	},
	MINOR: {
		label: "Minor",
		fgColor: "text-provider-attention-foreground",
		bgColor: "bg-provider-attention",
	},
	INFO: {
		label: "Info",
		fgColor: "text-provider-muted-foreground",
		bgColor: "bg-provider-muted",
	},
};

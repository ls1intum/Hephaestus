import { z } from "zod";

/**
 * The header of a released research dataset, read from the file the server returned. Only these
 * fields are read; the rows stay in the downloaded file and are never kept in the page.
 */
const releaseHeader = z.object({
	schema: z.literal("hephaestus.research-dataset"),
	schemaVersion: z.number().int().positive(),
	datasetId: z.uuid(),
	classification: z.literal("PSEUDONYMOUS_PERSONAL_DATA"),
	window: z.object({ from: z.iso.date(), to: z.iso.date() }),
	useUntil: z.iso.date(),
	counts: z.object({
		participants: z.number().int().nonnegative(),
		results: z.number().int().nonnegative(),
		problems: z.number().int().nonnegative(),
		feedback: z.number().int().nonnegative(),
		feedbackExcludedIncompleteBasis: z.number().int().nonnegative(),
	}),
});

export type ResearchReleaseReceipt = z.infer<typeof releaseHeader>;

/** The header of the released file, or undefined when the file is not a dataset this page can read. */
export async function readReleaseReceipt(file: Blob): Promise<ResearchReleaseReceipt | undefined> {
	try {
		const parsed = releaseHeader.safeParse(JSON.parse(await file.text()));
		return parsed.success ? parsed.data : undefined;
	} catch {
		return undefined;
	}
}

/** The release body's calendar-day fields, `format: date` in the contract. */
const CALENDAR_DAY_FIELDS = new Set(["firstDay", "lastDay"]);

/**
 * The JSON body of one release request, with its calendar days written as `yyyy-MM-dd`. The generated
 * types hold them as `Date`, which `JSON.stringify` would write as a timestamp. Each is the UTC
 * midnight `utcDay` built, so its first ten characters are the picked day. `useUntil` stays an instant.
 */
export function serializeReleaseBody(body: unknown): string {
	return JSON.stringify(body, (key: string, value: unknown) =>
		CALENDAR_DAY_FIELDS.has(key) && typeof value === "string" ? value.slice(0, 10) : value,
	);
}

/** A picked calendar day as the UTC midnight that starts it; the server reads days in UTC. */
export function utcDay(value: string): Date {
	return new Date(`${value}T00:00:00Z`);
}

/** The instant use ends: the start of the UTC day after the last permitted day. */
export function startOfDayAfter(lastPermittedDay: string): Date {
	const end = utcDay(lastPermittedDay);
	end.setUTCDate(end.getUTCDate() + 1);
	return end;
}

/** The last moment use is allowed, for showing the UTC day an exclusive end instant closes. */
export function lastPermittedMoment(useUntil: Date): Date {
	return new Date(useUntil.getTime() - 1);
}

/** The last day a half-open UTC day window [from, to) covers. */
export function lastDayBefore(exclusiveEnd: string): string {
	const day = utcDay(exclusiveEnd);
	day.setUTCDate(day.getUTCDate() - 1);
	return day.toISOString().slice(0, 10);
}

/** The download name the server also gives in Content-Disposition. */
export function releaseFilename(datasetId: string | undefined): string {
	return datasetId === undefined
		? "hephaestus-research-dataset.json"
		: `hephaestus-research-${datasetId}.json`;
}

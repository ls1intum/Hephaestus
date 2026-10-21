import { describe, expect, it } from "vitest";

import {
	lastDayBefore,
	lastPermittedMoment,
	readReleaseReceipt,
	releaseFilename,
	serializeReleaseBody,
	startOfDayAfter,
	utcDay,
} from "./research-release";

const header = {
	schema: "hephaestus.research-dataset",
	schemaVersion: 1,
	datasetId: "6f1c2b8e-4d3a-4f6b-9a1e-2c7d8e9f0a1b",
	classification: "PSEUDONYMOUS_PERSONAL_DATA",
	purpose: "RESEARCH_PARTICIPATION",
	window: { from: "2026-04-01", to: "2026-10-01" },
	useUntil: "2027-09-30",
	counts: {
		participants: 12,
		results: 340,
		problems: 81,
		feedback: 57,
		feedbackExcludedIncompleteBasis: 2,
	},
	participants: [{ key: "p_aaaaaaaaaaaaaaaaaaaaaaaaaa" }],
};

function file(body: unknown): Blob {
	return new Blob([JSON.stringify(body)], { type: "application/json" });
}

describe("readReleaseReceipt", () => {
	it("reads the header of a released dataset and nothing else", async () => {
		const receipt = await readReleaseReceipt(file(header));
		expect(receipt).toStrictEqual({
			schema: "hephaestus.research-dataset",
			schemaVersion: 1,
			datasetId: header.datasetId,
			classification: "PSEUDONYMOUS_PERSONAL_DATA",
			window: header.window,
			useUntil: "2027-09-30",
			counts: header.counts,
		});
	});

	it.each([
		["another schema", { ...header, schema: "hephaestus.person-data" }],
		["a dataset that claims to be anonymous", { ...header, classification: "ANONYMOUS" }],
		["a missing count", { ...header, counts: { participants: 1 } }],
	])("gives no receipt for %s", async (_, body) => {
		await expect(readReleaseReceipt(file(body))).resolves.toBeUndefined();
	});

	it("gives no receipt for a body that is not JSON", async () => {
		await expect(readReleaseReceipt(new Blob(["not json"]))).resolves.toBeUndefined();
	});
});

describe("serializeReleaseBody", () => {
	it("writes the calendar days as days and keeps the use end an instant", () => {
		const body = {
			workspaceId: "2",
			firstDay: utcDay("2026-04-01"),
			lastDay: utcDay("2026-09-30"),
			useUntil: startOfDayAfter("2027-09-30"),
		};
		expect(JSON.parse(serializeReleaseBody(body))).toStrictEqual({
			workspaceId: "2",
			firstDay: "2026-04-01",
			lastDay: "2026-09-30",
			useUntil: "2027-10-01T00:00:00.000Z",
		});
	});
});

describe("UTC days", () => {
	it("sends a picked day as the UTC midnight that starts it", () => {
		expect(utcDay("2026-04-01").toISOString()).toBe("2026-04-01T00:00:00.000Z");
	});

	it("ends use at the start of the day after the last permitted day", () => {
		expect(startOfDayAfter("2027-12-31").toISOString()).toBe("2028-01-01T00:00:00.000Z");
	});

	it("shows a use end at midnight as the day before it", () => {
		expect(lastPermittedMoment(new Date("2028-01-01T00:00:00Z")).toISOString()).toBe(
			"2027-12-31T23:59:59.999Z",
		);
	});

	it("names the last day of a half-open window", () => {
		expect(lastDayBefore("2026-03-01")).toBe("2026-02-28");
	});

	it("names the file by its dataset", () => {
		expect(releaseFilename(header.datasetId)).toBe(`hephaestus-research-${header.datasetId}.json`);
		expect(releaseFilename(undefined)).toBe("hephaestus-research-dataset.json");
	});
});

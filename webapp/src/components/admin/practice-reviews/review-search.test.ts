import { describe, expect, it } from "vitest";
import { feedbackQuery, feedbackSearchSchema, findingsSearchSchema } from "./review-search";

describe("practice review search", () => {
	it("canonicalizes invalid URL filters", () => {
		const feedback = feedbackSearchSchema.parse({
			deliveryState: ["DELIVERED", "made-up"],
			channel: "PROFILE",
			from: "not-a-date",
			page: -4,
		});
		const findings = findingsSearchSchema.parse({
			assessment: ["GOOD", "unknown"],
			severity: "MAJOR",
			to: "2026-02-31",
		});

		expect(feedback).toMatchObject({ deliveryState: ["DELIVERED"] });
		expect(feedback.channel).toBeUndefined();
		expect(feedback.from).toBeUndefined();
		expect(feedback.page).toBeUndefined();
		expect(findings).toMatchObject({ assessment: ["GOOD"], severity: ["MAJOR"] });
		expect(findings.to).toBeUndefined();
	});

	it("uses an inclusive day start and exclusive next-day end", () => {
		const search = feedbackSearchSchema.parse({ from: "2026-07-01", to: "2026-07-03" });
		const query = feedbackQuery(search, 25);

		expect(String(query.from)).toContain("2026-07-01T00:00:00");
		expect(String(query.to)).toContain("2026-07-04T00:00:00");
	});

	it("does not send an artifact id without its type", () => {
		const query = feedbackQuery(feedbackSearchSchema.parse({ artifactId: "42" }), 25);
		expect(query.artifactId).toBeUndefined();
	});
});

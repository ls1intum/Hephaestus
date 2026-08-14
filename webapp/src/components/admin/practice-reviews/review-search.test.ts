import { describe, expect, it } from "vitest";
import { feedbackQuery, feedbackSearchSchema, findingsSearchSchema } from "./review-search";

describe("practice review search", () => {
	it("canonicalizes invalid URL filters", () => {
		const feedback = feedbackSearchSchema.parse({
			deliveryState: ["DELIVERED", "made-up"],
			channel: ["PROFILE", "CONVERSATION", "made-up"],
			from: "not-a-date",
			page: -4,
		});
		const findings = findingsSearchSchema.parse({
			assessment: ["GOOD", "GOOD", "unknown"],
			severity: "MAJOR",
			from: "2026-08-10",
			to: "2026-08-01",
		});

		expect(feedback).toMatchObject({ deliveryState: ["DELIVERED"] });
		// PROFILE is a real wire value the API accepts, and is dropped here anyway: the toolbar never
		// offers it, so a filter on it would be applied with nothing on screen saying so and no way to
		// clear it short of a full reset.
		expect(feedback.channel).toEqual(["CONVERSATION"]);
		expect(feedback.from).toBeUndefined();
		expect(feedback.page).toBeUndefined();
		expect(findings).toMatchObject({ assessment: ["GOOD"], severity: ["MAJOR"] });
		expect(findings.to).toBeUndefined();
		expect(feedbackSearchSchema.parse({ to: "2026-07-03" }).to).toBeUndefined();
	});

	it("uses an inclusive day start and exclusive next-day end", () => {
		const search = feedbackSearchSchema.parse({ from: "2026-07-01", to: "2026-07-03" });
		const query = feedbackQuery(search, 25);

		expect(query.from).toBeInstanceOf(Date);
		expect(query.from?.getDate()).toBe(1);
		expect(query.from?.getHours()).toBe(0);
		expect(query.to).toBeInstanceOf(Date);
		expect(query.to?.getDate()).toBe(4);
		expect(query.to?.getHours()).toBe(0);
	});

	it("does not send an artifact id without its type", () => {
		const query = feedbackQuery(feedbackSearchSchema.parse({ artifactId: "42" }), 25);
		expect(query.artifactId).toBeUndefined();
	});
});

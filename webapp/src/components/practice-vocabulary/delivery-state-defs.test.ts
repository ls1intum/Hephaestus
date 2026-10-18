import { describe, expect, it } from "vitest";

import { DELIVERY_STATE_DEFS, deliveryOutcome } from "./delivery-state-defs";

describe("deliveryOutcome for unconfirmed feedback", () => {
	it("names no conversation for a write on the work", () => {
		const outcome = deliveryOutcome({
			destination: "ON_WORK",
			state: "UNCONFIRMED",
			stopReason: "DELIVERY_UNCONFIRMED",
		});
		expect(outcome).toBe(DELIVERY_STATE_DEFS.UNCONFIRMED);
		expect(outcome.description).not.toMatch(/conversation/iu);
	});

	// The label stays the state's, so a filter by state still matches what the row says.
	it("keeps the conversation's own words for a conversation", () => {
		expect(
			deliveryOutcome({
				destination: "CONVERSATION",
				state: "UNCONFIRMED",
				stopReason: "DELIVERY_UNCONFIRMED",
			}),
		).toMatchObject({
			label: DELIVERY_STATE_DEFS.UNCONFIRMED.label,
			description: "The conversation linked this feedback, but its appearance was not recorded.",
		});
	});
});

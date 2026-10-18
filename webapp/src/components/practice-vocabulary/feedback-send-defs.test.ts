import { describe, expect, it } from "vitest";

import type { FeedbackCopy, FeedbackSend, ReviewFeedbackDetail } from "@/api/types.gen";
import { uncertainWriteFeedbackDetail } from "@/components/admin/practice-reviews/fixtures";

import { deliveryCheck } from "./feedback-send-defs";

type Checked = Pick<ReviewFeedbackDetail, "destination" | "sends" | "copies">;

/** A summary on the work whose write the provider never confirmed, after its post stopped trying. */
function unknownWrite({
	copy = {},
	send = {},
	destination = "ON_WORK",
}: {
	copy?: Partial<FeedbackCopy>;
	send?: Partial<FeedbackSend>;
	destination?: ReviewFeedbackDetail["destination"];
} = {}): Checked {
	return {
		destination,
		sends: uncertainWriteFeedbackDetail.sends.map((post) => ({
			...post,
			action: "POST" as const,
			state: "DONE" as const,
			...send,
		})),
		copies: uncertainWriteFeedbackDetail.copies.map((written) => ({
			...written,
			state: "WRITE_STARTED" as const,
			postedAt: null,
			externalRef: null,
			readback: "UNKNOWN" as const,
			...copy,
		})),
	};
}

describe("deliveryCheck", () => {
	it("offers a check once the post finished with the write still unknown", () => {
		expect(deliveryCheck(unknownWrite())).toBe("available");
	});

	// A withdrawal queues no removal while the write is unknown; only a check can settle it.
	it("offers a check on withdrawn feedback too", () => {
		const withdrawn: ReviewFeedbackDetail = {
			...uncertainWriteFeedbackDetail,
			...unknownWrite(),
			withdrawn: true,
		};
		expect(deliveryCheck(withdrawn)).toBe("available");
	});

	it.each(["PENDING", "SENDING", "UNCERTAIN"] as const)(
		"reports a check in progress while the post is %s",
		(state) => {
			expect(deliveryCheck(unknownWrite({ send: { state } }))).toBe("checking");
		},
	);

	it.each<[string, Checked]>([
		["a conversation", unknownWrite({ destination: "CONVERSATION" })],
		["the developer's practice pages", unknownWrite({ destination: "PRACTICE_PAGE" })],
		["a removal", unknownWrite({ send: { action: "RETRACT" } })],
		["no send", { ...unknownWrite(), sends: [] }],
		["a write proved absent", unknownWrite({ copy: { state: "FAILED", readback: "ABSENT" } })],
		["an absent readback on the started write", unknownWrite({ copy: { readback: "ABSENT" } })],
		["a provider reference", unknownWrite({ copy: { externalRef: "2481933" } })],
		["a posted time", unknownWrite({ copy: { postedAt: new Date("2026-07-29T08:23:00Z") } })],
		["no started write", unknownWrite({ copy: { writeStartedAt: null } })],
		["a posted copy", unknownWrite({ copy: { state: "POSTED", externalRef: "2481933" } })],
	])("offers nothing with %s", (_case, feedback) => {
		expect(deliveryCheck(feedback)).toBeUndefined();
	});
});

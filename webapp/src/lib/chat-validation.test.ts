import { describe, expect, it } from "vitest";
import { extractVotesFromThreadDetail, parseThreadMessages } from "./chat-validation";

// chat-validation is the Zod boundary on untrusted server-provided chat content that mentor hydration
// depends on. Its rejection branches are security-relevant (a malformed payload must fail closed, not
// corrupt the chat) and were only exercised indirectly through a heavily-mocked hook — where a non-UUID
// id once silently skipped hydration and masked a bug. These are direct, millisecond assertions.

const UUID = "f47ac10b-58cc-4372-a567-0e02b2c3d479";
const UUID2 = "c9bf9e57-1685-4c89-bafb-ff5af830be8a";

function msg(id: string, role: string) {
	return { id, role, parts: [{ type: "text", text: "hi" }] };
}

describe("parseThreadMessages", () => {
	it("accepts well-formed messages and preserves passthrough part fields", () => {
		const result = parseThreadMessages([
			{ id: UUID, role: "user", parts: [{ type: "text", text: "hi", extra: 1 }] },
			msg(UUID2, "assistant"),
		]);
		expect(result).toHaveLength(2);
		expect(result?.[0].id).toBe(UUID);
		// forward-compat passthrough: unknown part fields survive validation
		expect((result?.[0].parts[0] as Record<string, unknown>).extra).toBe(1);
	});

	it("rejects a non-UUID message id (the exact class that silently skipped hydration)", () => {
		expect(parseThreadMessages([msg("msg-1", "user")])).toBeUndefined();
	});

	it("rejects an unknown role", () => {
		expect(parseThreadMessages([msg(UUID, "robot")])).toBeUndefined();
	});

	it("rejects a non-array payload", () => {
		expect(parseThreadMessages({ id: UUID })).toBeUndefined();
		expect(parseThreadMessages(null)).toBeUndefined();
	});
});

describe("extractVotesFromThreadDetail", () => {
	it("returns the validated votes for a well-formed payload", () => {
		const votes = extractVotesFromThreadDetail({ votes: [{ messageId: UUID, isUpvoted: true }] });
		expect(votes).toEqual([{ messageId: UUID, isUpvoted: true }]);
	});

	it("returns [] when votes are absent, not an array, or the container is not an object", () => {
		expect(extractVotesFromThreadDetail({})).toEqual([]);
		expect(extractVotesFromThreadDetail({ votes: "nope" })).toEqual([]);
		expect(extractVotesFromThreadDetail(null)).toEqual([]);
	});

	it("returns [] (fail closed) when any vote is malformed", () => {
		expect(extractVotesFromThreadDetail({ votes: [{ messageId: "not-a-uuid" }] })).toEqual([]);
	});
});

import { describe, expect, it, vi } from "vitest";
import { deferred } from "@/test/async";
import { handlePasskeyChallenge } from "./passkey-challenge";

describe("handlePasskeyChallenge", () => {
	it("navigates for a passkey requirement without consuming the response", async () => {
		const navigate = vi.fn().mockResolvedValue(undefined);
		const response = Response.json({ code: "passkey_required" }, { status: 403 });
		await handlePasskeyChallenge(response, navigate);
		expect(navigate).toHaveBeenCalledOnce();
		await expect(response.json()).resolves.toStrictEqual({ code: "passkey_required" });
	});
	it.each([
		{ status: 401, body: '{"code":"passkey_required"}' },
		{ status: 403, body: '{"code":"access_denied"}' },
		{ status: 403, body: "invalid JSON" },
	])("does not navigate for $status with $body", async ({ status, body }) => {
		const navigate = vi.fn().mockResolvedValue(undefined);
		await handlePasskeyChallenge(new Response(body, { status }), navigate);
		expect(navigate).not.toHaveBeenCalled();
	});
	it("shares navigation while a draft blocker is pending", async () => {
		const decision = deferred();
		const navigate = vi.fn(async () => decision.promise);
		const response = Response.json({ code: "passkey_required" }, { status: 403 });
		const first = handlePasskeyChallenge(response, navigate);
		const second = handlePasskeyChallenge(response, navigate);
		await vi.waitFor(() => expect(navigate).toHaveBeenCalledOnce());
		decision.resolve();
		await Promise.all([first, second]);
	});
});

import { beforeEach, describe, expect, it, type Mock, vi } from "vitest";
import { refresh } from "@/api/sdk.gen";
import { refreshAccessToken } from "./session-refresh";

vi.mock("@/api/sdk.gen", () => ({ refresh: vi.fn() }));

const refreshMock = refresh as unknown as Mock;

describe("refreshAccessToken", () => {
	beforeEach(() => {
		refreshMock.mockReset();
	});

	it("collapses callers that overlap a rotation onto one POST /auth/refresh", async () => {
		let settle: ((value: { error?: unknown }) => void) | undefined;
		refreshMock.mockReturnValue(
			new Promise((resolve) => {
				settle = resolve;
			}),
		);

		const overlapping = Promise.all([
			refreshAccessToken(),
			refreshAccessToken(),
			refreshAccessToken(),
		]);
		settle?.({ error: undefined });

		expect(await overlapping).toEqual([true, true, true]);
		expect(refreshMock).toHaveBeenCalledTimes(1);
	});

	it("rotates again once the previous rotation has settled", async () => {
		refreshMock.mockResolvedValue({ error: undefined });

		await refreshAccessToken();
		await refreshAccessToken();

		expect(refreshMock).toHaveBeenCalledTimes(2);
	});

	it("answers false rather than throwing when the rotation fails", async () => {
		refreshMock.mockRejectedValue(new Error("offline"));

		await expect(refreshAccessToken()).resolves.toBe(false);
	});
});

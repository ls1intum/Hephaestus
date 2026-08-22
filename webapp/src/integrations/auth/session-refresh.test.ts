import { beforeEach, describe, expect, it, vi } from "vitest";
import { refresh } from "@/api/sdk.gen";
import { refreshAccessToken } from "./session-refresh";

vi.mock("@/api/sdk.gen", () => ({ refresh: vi.fn() }));

const refreshMock = vi.mocked(refresh);

/** A rotation the server accepted — the generated client reports failure via `error`, not by throwing. */
const rotated = { data: undefined, error: undefined };

describe("refreshAccessToken", () => {
	beforeEach(() => {
		refreshMock.mockReset();
	});

	it("collapses callers that overlap a rotation onto one POST /auth/refresh", async () => {
		let settle: (() => void) | undefined;
		refreshMock.mockReturnValue(
			new Promise<typeof rotated>((resolve) => {
				settle = () => resolve(rotated);
			}),
		);

		const overlapping = Promise.all([
			refreshAccessToken(),
			refreshAccessToken(),
			refreshAccessToken(),
		]);
		settle?.();

		expect(await overlapping).toEqual([true, true, true]);
		expect(refreshMock).toHaveBeenCalledTimes(1);
	});

	it("rotates again once the previous rotation has settled", async () => {
		refreshMock.mockResolvedValue(rotated);

		await refreshAccessToken();
		await refreshAccessToken();

		expect(refreshMock).toHaveBeenCalledTimes(2);
	});

	it("answers false rather than throwing when the rotation fails", async () => {
		refreshMock.mockRejectedValue(new Error("offline"));

		await expect(refreshAccessToken()).resolves.toBe(false);
	});
});

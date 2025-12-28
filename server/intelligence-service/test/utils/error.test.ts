import { afterEach, describe, expect, it, vi } from "vitest";
import { extractErrorMessage, toLoggableError } from "@/shared/utils/error";

describe("extractErrorMessage", () => {
	it("should extract message from Error instance", () => {
		const error = new Error("Test error message");
		expect(extractErrorMessage(error)).toBe("Test error message");
	});

	it("should return string directly if string is thrown", () => {
		expect(extractErrorMessage("Raw string error")).toBe("Raw string error");
	});

	it("should return 'Unknown error' for non-Error, non-string values", () => {
		expect(extractErrorMessage(null)).toBe("Unknown error");
		expect(extractErrorMessage(undefined)).toBe("Unknown error");
		expect(extractErrorMessage(123)).toBe("Unknown error");
		expect(extractErrorMessage({ custom: "error" })).toBe("Unknown error");
	});
});

describe("toLoggableError", () => {
	afterEach(() => {
		vi.unstubAllEnvs();
	});

	it("should return message only in production", () => {
		vi.stubEnv("NODE_ENV", "production");

		const error = new Error("Production error");
		const result = toLoggableError(error);

		expect(result.message).toBe("Production error");
		expect(result.stack).toBeUndefined();
	});

	it("should include stack trace in development", () => {
		vi.stubEnv("NODE_ENV", "development");

		const error = new Error("Dev error");
		const result = toLoggableError(error);

		expect(result.message).toBe("Dev error");
		expect(result.stack).toBeDefined();
		expect(result.stack).toContain("Dev error");
	});

	it("should handle non-Error values gracefully", () => {
		const result = toLoggableError("string error");
		expect(result.message).toBe("string error");
		expect(result.stack).toBeUndefined();
	});
});

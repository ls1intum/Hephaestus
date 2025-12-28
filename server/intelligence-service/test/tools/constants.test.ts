/**
 * Constants Tests
 *
 * Ensures all exported constants are used and have valid values.
 */

import { describe, expect, it } from "vitest";
import {
	CONTENT_PREVIEW_LENGTH,
	DATA_FETCH_ERROR,
	DOCUMENT_PREVIEW_LENGTH,
	MAX_DISPLAY_REVIEWS,
	MAX_DOCUMENTS,
	MAX_ISSUES,
	MAX_LOOKBACK_DAYS,
	MAX_PULL_REQUESTS,
	MAX_REVIEW_REQUESTS,
	MAX_REVIEWS,
	MAX_SESSIONS,
	MAX_TOP_AUTHORS,
	MESSAGE_PREVIEW_LENGTH,
	MS_PER_DAY,
	OPEN_PR_WARNING_THRESHOLD,
	REVIEW_WAIT_URGENCY_DAYS,
} from "@/mentor/tools/constants";

describe("Tool Constants", () => {
	describe("Query Limits", () => {
		it("should have reasonable maximum session limit", () => {
			expect(MAX_SESSIONS).toBeGreaterThanOrEqual(5);
			expect(MAX_SESSIONS).toBeLessThanOrEqual(50);
		});

		it("should have reasonable maximum document limit", () => {
			expect(MAX_DOCUMENTS).toBeGreaterThanOrEqual(5);
			expect(MAX_DOCUMENTS).toBeLessThanOrEqual(50);
		});

		it("should have reasonable maximum issues limit", () => {
			expect(MAX_ISSUES).toBeGreaterThanOrEqual(10);
			expect(MAX_ISSUES).toBeLessThanOrEqual(100);
		});

		it("should have reasonable maximum PR limit", () => {
			expect(MAX_PULL_REQUESTS).toBeGreaterThanOrEqual(10);
			expect(MAX_PULL_REQUESTS).toBeLessThanOrEqual(100);
		});

		it("should have reasonable maximum reviews limit", () => {
			expect(MAX_REVIEWS).toBeGreaterThanOrEqual(10);
			expect(MAX_REVIEWS).toBeLessThanOrEqual(100);
		});

		it("should have reasonable review requests limit", () => {
			expect(MAX_REVIEW_REQUESTS).toBeGreaterThanOrEqual(5);
			expect(MAX_REVIEW_REQUESTS).toBeLessThanOrEqual(25);
		});
	});

	describe("Time Windows", () => {
		it("should have 90 day max lookback", () => {
			expect(MAX_LOOKBACK_DAYS).toBe(90);
		});

		it("should have correct milliseconds per day", () => {
			expect(MS_PER_DAY).toBe(24 * 60 * 60 * 1000);
			expect(MS_PER_DAY).toBe(86400000);
		});
	});

	describe("Display Limits", () => {
		it("should have reasonable display review limit", () => {
			expect(MAX_DISPLAY_REVIEWS).toBeGreaterThanOrEqual(5);
			expect(MAX_DISPLAY_REVIEWS).toBeLessThanOrEqual(15);
		});

		it("should have reasonable top authors limit", () => {
			expect(MAX_TOP_AUTHORS).toBeGreaterThanOrEqual(3);
			expect(MAX_TOP_AUTHORS).toBeLessThanOrEqual(10);
		});

		it("should have reasonable message preview length", () => {
			expect(MESSAGE_PREVIEW_LENGTH).toBeGreaterThanOrEqual(50);
			expect(MESSAGE_PREVIEW_LENGTH).toBeLessThanOrEqual(300);
		});

		it("should have reasonable document preview length", () => {
			expect(DOCUMENT_PREVIEW_LENGTH).toBeGreaterThanOrEqual(100);
			expect(DOCUMENT_PREVIEW_LENGTH).toBeLessThanOrEqual(500);
		});

		it("should have reasonable content preview length", () => {
			expect(CONTENT_PREVIEW_LENGTH).toBeGreaterThanOrEqual(50);
			expect(CONTENT_PREVIEW_LENGTH).toBeLessThanOrEqual(200);
		});
	});

	describe("Thresholds", () => {
		it("should have reasonable open PR warning threshold", () => {
			expect(OPEN_PR_WARNING_THRESHOLD).toBeGreaterThanOrEqual(2);
			expect(OPEN_PR_WARNING_THRESHOLD).toBeLessThanOrEqual(10);
		});

		it("should have reasonable review wait urgency days", () => {
			expect(REVIEW_WAIT_URGENCY_DAYS).toBeGreaterThanOrEqual(1);
			expect(REVIEW_WAIT_URGENCY_DAYS).toBeLessThanOrEqual(7);
		});
	});

	describe("Error Messages", () => {
		it("should have informative error message", () => {
			expect(DATA_FETCH_ERROR).toBeTruthy();
			expect(DATA_FETCH_ERROR).toContain("temporarily");
			expect(DATA_FETCH_ERROR).toContain("unavailable");
		});
	});

	describe("All constants should be defined", () => {
		it("should export all expected constants", () => {
			// This test ensures we don't accidentally remove constants
			const constants = {
				MAX_SESSIONS,
				MAX_DOCUMENTS,
				MAX_ISSUES,
				MAX_PULL_REQUESTS,
				MAX_REVIEWS,
				MAX_REVIEW_REQUESTS,
				MAX_LOOKBACK_DAYS,
				MS_PER_DAY,
				MAX_DISPLAY_REVIEWS,
				MAX_TOP_AUTHORS,
				MESSAGE_PREVIEW_LENGTH,
				DOCUMENT_PREVIEW_LENGTH,
				CONTENT_PREVIEW_LENGTH,
				OPEN_PR_WARNING_THRESHOLD,
				REVIEW_WAIT_URGENCY_DAYS,
				DATA_FETCH_ERROR,
			};

			for (const [name, value] of Object.entries(constants)) {
				expect(value, `${name} should be defined`).toBeDefined();
			}
		});
	});
});

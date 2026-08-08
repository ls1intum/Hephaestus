import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { holdReasonCopy, jobWait, MoneyCell } from "./job-utils";

const NOW = new Date("2026-05-20T12:00:00Z").getTime();
const SOON = new Date("2026-05-20T12:05:00Z");
const EARLIER = new Date("2026-05-20T11:55:00Z");

describe("jobWait", () => {
	it("reports a hold whenever the server names a reason", () => {
		expect(jobWait({ status: "QUEUED", holdReason: "BUDGET", availableAt: SOON }, NOW)).toEqual({
			kind: "hold",
			reason: "BUDGET",
		});
	});

	it("still reports a hold once the parked instant has lapsed", () => {
		expect(jobWait({ status: "QUEUED", holdReason: "BUDGET", availableAt: EARLIER }, NOW)).toEqual({
			kind: "hold",
			reason: "BUDGET",
		});
	});

	it("reports a backoff for a queued run whose next attempt is still ahead", () => {
		expect(jobWait({ status: "QUEUED", availableAt: SOON }, NOW)).toEqual({ kind: "backoff" });
	});

	it("reports nothing for a queued run that is already claimable", () => {
		expect(jobWait({ status: "QUEUED", availableAt: EARLIER }, NOW)).toBeNull();
	});

	it("reports nothing once the run has left the queue, whatever its timestamps say", () => {
		expect(jobWait({ status: "RUNNING", availableAt: SOON }, NOW)).toBeNull();
		expect(
			jobWait({ status: "COMPLETED", holdReason: "BUDGET", availableAt: SOON }, NOW),
		).toBeNull();
	});
});

describe("holdReasonCopy", () => {
	it("names the reason it knows in words an operator can act on", () => {
		expect(holdReasonCopy("BUDGET").label).toBe("Over the AI budget");
		expect(holdReasonCopy("BUDGET").detail).toMatch(/resumes on its own/);
	});

	it("reads a reason it has never seen as English rather than as a constant", () => {
		expect(holdReasonCopy("MODEL_UNAVAILABLE").label).toBe("Model unavailable");
		expect(holdReasonCopy("MODEL_UNAVAILABLE").detail).toMatch(/resumes on its own/);
	});

	it("never suggests a held run failed", () => {
		for (const reason of ["BUDGET", "MODEL_UNAVAILABLE"]) {
			expect(holdReasonCopy(reason).detail).toMatch(/rather than failed/);
		}
	});
});

describe("MoneyCell", () => {
	it("announces the figure without the cents it pads the column with", () => {
		render(
			<table>
				<tbody>
					<tr>
						<td>
							<MoneyCell>$0</MoneyCell>
						</td>
					</tr>
				</tbody>
			</table>,
		);

		// The pad is `aria-hidden`: announcing "$0.00" would be a different amount of money.
		expect(screen.getByRole("row", { name: "$0" })).toBeTruthy();
	});

	it("leaves a figure that already prints its cents exactly as it is", () => {
		// Padding a figure that already ends in cents would render "$4.50.00".
		render(
			<table>
				<tbody>
					<tr>
						<td>
							<MoneyCell>$4.50</MoneyCell>
						</td>
						<td>
							<MoneyCell>{"<$0.01"}</MoneyCell>
						</td>
					</tr>
				</tbody>
			</table>,
		);

		const [cents, bound] = screen.getAllByRole("cell");
		expect(cents.textContent).toBe("$4.50");
		expect(bound.textContent).toBe("<$0.01");
	});
});

import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { MoneyCell } from "./job-utils";

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

		// The pad is `aria-hidden`, so it is absent from the accessible name — announcing "$0.00"
		// would be a different amount of money.
		expect(screen.getByRole("row", { name: "$0" })).toBeTruthy();
	});

	it("leaves a figure that already prints its cents exactly as it is", () => {
		// The pad is `visibility: hidden` and `aria-hidden`, so it is neither seen nor announced — it
		// reserves column width and nothing else, which is a Chromatic matter and not assertable here.
		// What *is* assertable is the failure the guard prevents: padding a figure that already ends
		// in cents renders "$4.50.00", which is visible and wrong.
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
		// Already ends in cents, so its decimal point already lands where "$4.50"'s does.
		expect(bound.textContent).toBe("<$0.01");
	});
});

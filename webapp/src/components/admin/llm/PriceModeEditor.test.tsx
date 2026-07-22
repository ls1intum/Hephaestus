import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { PriceModeEditor } from "./PriceModeEditor";

describe("PriceModeEditor", () => {
	it("prices reasoning through billable output instead of a second overlapping rate", () => {
		render(
			<PriceModeEditor
				audience="instance"
				idPrefix="test-price"
				value={{ pricingMode: "PRICED" }}
				onChange={vi.fn()}
			/>,
		);
		expect(screen.getByLabelText(/Input \(USD\)/)).toBeTruthy();
		expect(screen.getByLabelText(/Output \(USD\)/)).toBeTruthy();
		expect(screen.queryByLabelText(/Reasoning \(USD\)/)).toBeNull();
		expect(screen.getByText(/reasoning tokens are included in output/i)).toBeTruthy();
	});

	it("describes an intentional zero API rate without calling infrastructure free", () => {
		render(
			<PriceModeEditor
				audience="instance"
				idPrefix="test-price"
				value={{ pricingMode: "NO_CHARGE" }}
				onChange={vi.fn()}
			/>,
		);
		expect(screen.getByText("No metered API cost")).toBeTruthy();
		expect(screen.queryByText(/^Free$/)).toBeNull();
		expect(screen.getByText(/infrastructure cost may still apply/i)).toBeTruthy();
	});
});

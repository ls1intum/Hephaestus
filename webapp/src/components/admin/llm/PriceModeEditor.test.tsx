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
		screen.getByLabelText(/Input \(USD\)/);
		screen.getByLabelText(/Output \(USD\)/);
		expect(screen.queryByLabelText(/Reasoning \(USD\)/)).toBeNull();
		screen.getByText(/reasoning tokens are included in output/i);
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
		screen.getByText("No metered API cost");
		expect(screen.queryByText(/^Free$/)).toBeNull();
		screen.getByText(/infrastructure cost may still apply/i);
	});
});

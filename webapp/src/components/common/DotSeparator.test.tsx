import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { DotSeparator } from "./DotSeparator";

describe("DotSeparator", () => {
	it("is hidden from assistive technology", () => {
		const { container } = render(<DotSeparator />);
		expect(container.firstElementChild?.getAttribute("aria-hidden")).toBe("true");
	});

	it("keeps the separator in the text, so a row reads as before when copied", () => {
		const { container } = render(
			<p>
				one
				<DotSeparator />
				two
			</p>,
		);
		expect(container.textContent).toBe("one · two");
	});
});

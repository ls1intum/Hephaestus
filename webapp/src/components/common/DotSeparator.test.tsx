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

	it("stands in for the spaces around the character only in running text", () => {
		const { container } = render(
			<>
				<DotSeparator inline />
				<DotSeparator />
			</>,
		);
		const [inline, inRow] = container.querySelectorAll("[data-dot-separator]");
		expect(inline?.className).toContain("mx-[0.35em]");
		expect(inRow?.className).not.toContain("mx-");
	});
});

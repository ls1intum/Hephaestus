import { render } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { ThemeProvider } from "./ThemeContext";

describe("ThemeProvider", () => {
	afterEach(() => {
		localStorage.clear();
		document.documentElement.className = "";
		document.documentElement.removeAttribute("data-color-mode");
	});

	// `localStorage` is user-writable and outlives any rename of the themes, so a value from another
	// build — or one typed in by hand — must not reach the document as a class nobody styles.
	it("falls back to the default theme when the stored one is not a theme this build knows", () => {
		localStorage.setItem("theme", "solarized");

		render(<ThemeProvider defaultTheme="light" />);

		expect(document.documentElement.getAttribute("data-color-mode")).toBe("light");
	});
});

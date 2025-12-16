import "@testing-library/jest-dom/vitest";
import type { ReactNode } from "react";

import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import Footer from "./Footer";

vi.mock("@tanstack/react-router", () => ({
	Link: ({ to, children, ...props }: { to: string; children: ReactNode }) => (
		<a href={typeof to === "string" ? to : String(to)} {...props}>
			{children}
		</a>
	),
}));

describe("Footer", () => {
	it("does not render placeholder build info", () => {
		render(
			<Footer
				buildInfo={{
					branch: "WEB_ENV_GIT_BRANCH",
					commit: "WEB_ENV_GIT_COMMIT",
				}}
			/>,
		);

		expect(screen.queryByText(/WEB_ENV/)).toBeNull();
	});

	it("renders provided build info values", () => {
		render(
			<Footer
				buildInfo={{
					branch: "main",
					commit: "abcdef1234567",
				}}
			/>,
		);

		expect(screen.getByText("main")).toBeInTheDocument();
		expect(screen.getByText("abcdef1")).toBeInTheDocument();
	});
});

import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { WorkspaceMultiSelect } from "./WorkspaceMultiSelect";

describe("WorkspaceMultiSelect", () => {
	it("filters large workspace lists by name or slug", () => {
		render(
			<WorkspaceMultiSelect
				options={[
					{ id: 1, displayName: "Alpha Team", workspaceSlug: "alpha" },
					{ id: 2, displayName: "Beta Team", workspaceSlug: "engineering-beta" },
				]}
				selectedIds={[]}
				onChange={vi.fn()}
			/>,
		);

		fireEvent.click(screen.getByRole("button", { name: "Select workspaces…" }));
		fireEvent.change(screen.getByPlaceholderText("Search workspaces…"), {
			target: { value: "engineering" },
		});

		expect(screen.queryByText("Alpha Team")).toBeNull();
		expect(screen.getByText("Beta Team")).toBeTruthy();
	});
});

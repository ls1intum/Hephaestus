import { act, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";

import { renderWithRouter } from "@/test/router-harness";

import { stackInSearch } from "./detail-stack";
import { DetailStackLink } from "./DetailStackLink";

/** The stack in a location's search. */
function detailOf(search: unknown): string[] {
	return stackInSearch(
		typeof search === "object" && search !== null && "detail" in search ? search.detail : undefined,
	);
}

describe("DetailStackLink", () => {
	it("marks a level it pushes, and adds no history entry for a swap or the level in front", async () => {
		const { router } = await renderWithRouter(
			<>
				<DetailStackLink entry={{ kind: "group", id: "a" }}>Group</DetailStackLink>
				<DetailStackLink entry={{ kind: "practice", id: "p" }}>Practice</DetailStackLink>
				<DetailStackLink entry={{ kind: "practice", id: "q" }} swap>
					Other practice
				</DetailStackLink>
			</>,
			"/",
		);
		const user = userEvent.setup();
		const stack = () => detailOf(router.state.location.search);
		const index = () => router.history.location.state.__TSR_index;
		const start = index();

		await act(async () => user.click(screen.getByRole("link", { name: "Group" })));
		await waitFor(() => expect(stack()).toStrictEqual(["group:a"]));
		expect(index()).toBe(start + 1);
		expect(router.history.location.state).toMatchObject({ detailPush: true, detailPushes: 1 });

		await act(async () => user.click(screen.getByRole("link", { name: "Practice" })));
		await waitFor(() => expect(stack()).toStrictEqual(["group:a", "practice:p"]));
		expect(index()).toBe(start + 2);
		expect(router.history.location.state).toMatchObject({ detailPush: true, detailPushes: 2 });

		// A swap stands in for the level in front: the same entry, its mark kept.
		await act(async () => user.click(screen.getByRole("link", { name: "Other practice" })));
		await waitFor(() => expect(stack()).toStrictEqual(["group:a", "practice:q"]));
		expect(index()).toBe(start + 2);
		expect(router.history.location.state).toMatchObject({ detailPush: true, detailPushes: 2 });

		// A link to the level already in front changes nothing.
		await act(async () => user.click(screen.getByRole("link", { name: "Other practice" })));
		await waitFor(() => expect(stack()).toStrictEqual(["group:a", "practice:q"]));
		expect(index()).toBe(start + 2);
		expect(router.history.location.state).toMatchObject({ detailPush: true, detailPushes: 2 });
	});
});

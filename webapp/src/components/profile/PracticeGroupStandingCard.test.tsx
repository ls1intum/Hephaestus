import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { PracticeGroup, PracticeGroupStanding } from "@/api/types.gen";
import { PracticeGroupStandingCard } from "./PracticeGroupStandingCard";

const group: PracticeGroup = {
	id: 1,
	slug: "review-ready-work",
	name: "Packaging work for review",
	displayOrder: 0,
	visibleInPracticeDashboards: true,
	autonomy: { effective: "AUTOMATIC", inherited: true, source: "WORKSPACE" },
	createdAt: new Date("2026-01-01T00:00:00Z"),
};
const standing: PracticeGroupStanding = {
	groupSlug: group.slug,
	groupName: group.name,
	standing: "NO_OPPORTUNITY",
	observations: [],
	sources: [],
};

describe("PracticeGroupStandingCard", () => {
	it("shows the server-derived standing", () => {
		render(
			<PracticeGroupStandingCard
				groups={[group]}
				standings={{ [group.slug]: standing }}
				isLoading={false}
			/>,
		);
		screen.getByText("Nothing to report yet");
		screen.getByText(/nothing here could be judged/i);
	});

	it("opens a group", () => {
		const onOpenDetails = vi.fn();
		render(
			<PracticeGroupStandingCard
				groups={[group]}
				standings={{ [group.slug]: standing }}
				isLoading={false}
				onOpenDetails={onOpenDetails}
			/>,
		);
		fireEvent.click(screen.getByRole("button", { name: `See details about ${group.name}` }));
		expect(onOpenDetails).toHaveBeenCalledWith(group);
	});

	it("shows an empty workspace state", () => {
		render(<PracticeGroupStandingCard groups={[]} standings={{}} isLoading={false} />);
		screen.getByText("No practice groups are configured yet.");
	});
});

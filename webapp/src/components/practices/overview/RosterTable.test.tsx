import { render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { PracticeReportSummary } from "@/api/types.gen";
import { RosterTable } from "./RosterTable";

const entries: PracticeReportSummary[] = [
	{
		userId: 42,
		userLogin: "zoe",
		name: "Zoe Attention",
		avatarUrl: "",
		needsAttention: true,
		attentionReasons: ["3 critical findings unaddressed"],
		standings: [
			{ name: "Clear PR description", slug: "clear-pr", standing: "DEVELOPING" },
			{ name: "Small PRs", slug: "small-prs", standing: "NO_ACTIVITY" },
		],
	},
	{
		userId: 43,
		userLogin: "aaron",
		name: "Aaron Fine",
		avatarUrl: "",
		needsAttention: false,
		attentionReasons: [],
		standings: [
			{ name: "Clear PR description", slug: "clear-pr", standing: "STRENGTH" },
			{ name: "Small PRs", slug: "small-prs", standing: "MIXED" },
		],
	},
];

describe("RosterTable", () => {
	it("renders one row per developer in the given (server) order — never re-sorted", () => {
		render(<RosterTable entries={entries} onSelectDeveloper={vi.fn()} />);
		const buttons = screen.getAllByRole("button");
		expect(buttons).toHaveLength(2);
		expect(within(buttons[0]).getByText("Zoe Attention")).toBeTruthy();
		expect(within(buttons[1]).getByText("Aaron Fine")).toBeTruthy();
	});

	it("renders attention reasons as badges for flagged developers", () => {
		render(<RosterTable entries={entries} onSelectDeveloper={vi.fn()} />);
		expect(screen.getByText("3 critical findings unaddressed")).toBeTruthy();
	});

	it("invokes onSelectDeveloper when the developer button is activated", () => {
		const onSelect = vi.fn();
		render(<RosterTable entries={entries} onSelectDeveloper={onSelect} />);
		screen.getAllByRole("button")[0].click();
		expect(onSelect).toHaveBeenCalledWith(entries[0]);
	});
});

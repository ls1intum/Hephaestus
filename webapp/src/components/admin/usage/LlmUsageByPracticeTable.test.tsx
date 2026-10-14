import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import {
	commentQualityUsage,
	deletedPracticeUsage,
	expectedBehaviourUsage,
	NO_PRACTICE_SCRIPT_USAGE,
	notAttributedUsage,
} from "./fixtures";
import { LlmUsageByPracticeTable } from "./LlmUsageByPracticeTable";

// Deliberately inconsistent totals prove the footer uses the server's aggregate.
const report = {
	byPractice: [commentQualityUsage, notAttributedUsage],
	practiceScriptTotal: {
		...NO_PRACTICE_SCRIPT_USAGE.practiceScriptTotal,
		reviews: 7,
		calls: 99,
		inputTokens: 11,
		outputTokens: 22,
		instanceTotalCostUsd: 1.4,
		unpricedEventCount: 5,
	},
};

function table(): HTMLElement {
	return screen.getByRole("table", { name: "Spend on models for practice scripts, by practice" });
}

function cellsOf(name: RegExp): (string | null)[] {
	return within(within(table()).getByRole("row", { name }))
		.getAllByRole("cell")
		.map((cell) => cell.textContent);
}

function practiceOrder(): (string | null)[] {
	return within(table())
		.getAllByRole("row")
		.slice(2)
		.filter((row) => !row.textContent.startsWith("Total"))
		.map((row) => within(row).getAllByRole("cell")[0]?.textContent ?? null);
}

describe("models for practice scripts by practice", () => {
	it("prints the server's total, never a re-addition of the rows", () => {
		render(<LlmUsageByPracticeTable report={report} purses={["SHARED"]} />);

		within(within(table()).getByRole("row", { name: /^Total/u })).getByRole("rowheader", {
			name: "Total",
		});
		expect(cellsOf(/^Total/u)).toStrictEqual(["", "$1.40", "$0.200", "7", "11", "22", "99"]);
	});

	it("averages spend over reviews, not calls", () => {
		render(<LlmUsageByPracticeTable report={report} purses={["SHARED"]} />);

		expect(cellsOf(/^Comments explain why/u)[3]).toBe("$0.020");
	});

	it("names calls with no practice as not attributed, as plain text without a workspace", () => {
		render(<LlmUsageByPracticeTable report={report} purses={["SHARED"]} />);

		expect(cellsOf(/^Not attributed/u)[1]).toBe("Decision model");
		expect(screen.queryByRole("link")).toBeNull();
	});

	it("leaves out the own-provider group when the page has no own-provider spend or cap", () => {
		render(<LlmUsageByPracticeTable report={report} purses={["SHARED"]} />);

		expect(within(table()).queryByText("Own provider")).toBeNull();
		within(table()).getByRole("columnheader", { name: "Shared models" });
	});

	it("shows reviews with no price only while some row has one", () => {
		const { unmount } = render(<LlmUsageByPracticeTable report={report} purses={["SHARED"]} />);
		expect(within(table()).queryByRole("columnheader", { name: "Unavailable cost" })).toBeNull();
		unmount();

		render(
			<LlmUsageByPracticeTable
				report={{ ...report, byPractice: [commentQualityUsage, deletedPracticeUsage] }}
				purses={["SHARED"]}
			/>,
		);
		within(table()).getByRole("columnheader", { name: "Unavailable cost" });
		expect(cellsOf(/small-pull-requests/u)[5]).toBe("2");
	});

	it("lists the most shared-model spend first, re-sorts by the column the reader picks, and keeps not attributed last", () => {
		render(
			<LlmUsageByPracticeTable
				report={{
					...report,
					byPractice: [notAttributedUsage, expectedBehaviourUsage, commentQualityUsage],
				}}
				purses={["SHARED", "OWN_PROVIDER"]}
			/>,
		);

		expect(practiceOrder()).toStrictEqual([
			"Comments explain why",
			"Issues state the expected behaviour",
			"Not attributed",
		]);
		const sharedSpend = within(table()).getByRole("columnheader", {
			name: "Spend (Shared models)",
		});
		within(table()).getByRole("button", { name: "Spend (Own provider)" });
		expect(sharedSpend.getAttribute("aria-sort")).toBe("descending");

		const reviews = within(table()).getByRole("columnheader", { name: "Reviews" });
		fireEvent.click(within(reviews).getByRole("button"));

		expect(reviews.getAttribute("aria-sort")).toBe("descending");
		expect(sharedSpend.getAttribute("aria-sort")).toBe("none");
		expect(practiceOrder()).toStrictEqual([
			"Comments explain why",
			"Issues state the expected behaviour",
			"Not attributed",
		]);

		fireEvent.click(within(reviews).getByRole("button"));
		expect(reviews.getAttribute("aria-sort")).toBe("ascending");
		expect(practiceOrder()).toStrictEqual([
			"Issues state the expected behaviour",
			"Comments explain why",
			"Not attributed",
		]);
	});
});

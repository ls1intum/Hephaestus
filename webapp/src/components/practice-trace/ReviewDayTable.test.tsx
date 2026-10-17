import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import type { ItemRef } from "@/api/types.gen";
import {
	outlineDocument,
	reviewItem,
	trackerIssue,
} from "@/components/admin/practice-reviews/fixtures";
import { Table, TableBody, TableRow } from "@/components/ui/table";

import { ReviewItemCell } from "./ReviewDayTable";

const PULL_REQUEST_TITLE = reviewItem.title ?? "";

/** The cell's text: the name, then the line under it. */
function workCell(item: ItemRef): string | null {
	render(
		<Table>
			<TableBody>
				<TableRow>
					<ReviewItemCell item={item} linked={false} />
				</TableRow>
			</TableBody>
		</Table>,
	);
	return screen.getByRole("cell").textContent;
}

describe("ReviewItemCell", () => {
	it("names the kind with the number, and the title without repeating it", () => {
		expect(workCell({ ...trackerIssue, title: "Make checkout retries safe #204" })).toBe(
			"Issue #204Make checkout retries safe",
		);
	});

	it("tells a pull request from an issue", () => {
		expect(workCell(reviewItem)).toBe(`Pull request #1423${PULL_REQUEST_TITLE}`);
	});

	it("names a document by its kind and then its title, once", () => {
		expect(workCell({ ...outlineDocument, title: outlineDocument.label })).toBe(
			"DocumentRunbook: restoring a workspace from backup",
		);
	});

	it("shows no second line when the title says nothing the name does not", () => {
		expect(workCell({ ...trackerIssue, title: trackerIssue.label })).toBe("Issue #204");
	});
});

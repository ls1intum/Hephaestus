import { fireEvent, screen } from "@testing-library/react";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { renderWithRouter } from "@/test/router-harness";
import { CuratedPracticeCatalog } from "./CuratedPracticeCatalog";
import type { CuratedCatalogSearch } from "./curated-catalog-search";

const practice = {
	slug: "clear-pr-description",
	name: "Write a clear pull request description",
	artifactType: "PULL_REQUEST" as const,
	areaSlug: "communication",
	revisionNumber: 3,
	revisionCreatedAt: "2026-07-30T12:00:00Z",
	version: 4,
	status: "AVAILABLE" as const,
	sourceKind: "BUNDLED" as const,
	syncStatus: "SYNCED" as const,
	latestBundledCatalogRevision: 3,
};

describe("CuratedPracticeCatalog", () => {
	it("requires confirmation and explains that retirement does not mutate workspace copies", async () => {
		const onStatusChange = vi.fn();
		await renderWithRouter(
			<CuratedPracticeCatalog
				areas={[{ slug: "communication", name: "Communication", displayOrder: 0 }]}
				practices={[practice]}
				search={{}}
				onSearchChange={vi.fn()}
				onStatusChange={onStatusChange}
			/>,
			"/admin/catalog",
		);

		fireEvent.click(
			screen.getByRole("button", {
				name: "Retire Write a clear pull request description",
			}),
		);
		expect(screen.getByText(/Existing workspace copies are unaffected/)).toBeTruthy();
		expect(onStatusChange).not.toHaveBeenCalled();

		fireEvent.click(screen.getByRole("button", { name: "Retire practice" }));
		expect(onStatusChange).toHaveBeenCalledWith(practice, "RETIRED");
	});

	it("groups available practices and applies search and status filters", async () => {
		function Catalog() {
			const [search, setSearch] = useState<CuratedCatalogSearch>({});
			return (
				<CuratedPracticeCatalog
					areas={[
						{ slug: "communication", name: "Communication", displayOrder: 1 },
						{ slug: "version-control", name: "Version control", displayOrder: 0 },
					]}
					practices={[
						practice,
						{
							...practice,
							slug: "focused-commits",
							name: "Keep commits focused",
							areaSlug: "version-control",
							syncStatus: "OVERRIDDEN",
						},
						{
							...practice,
							slug: "actionable-issues",
							name: "Create actionable issues",
							areaSlug: undefined,
							artifactType: "ISSUE",
							status: "RETIRED",
						},
					]}
					search={search}
					onSearchChange={setSearch}
					onStatusChange={vi.fn()}
				/>
			);
		}

		await renderWithRouter(<Catalog />, "/admin/catalog");

		const groups = screen.getAllByRole("heading", { level: 2 });
		expect(groups.map((heading) => heading.textContent)).toEqual([
			"Version control",
			"Communication",
		]);
		expect(screen.queryByText("Create actionable issues")).toBeNull();
		expect(screen.getByText("Hephaestus managed")).toBeTruthy();
		expect(screen.getByText("Instance override")).toBeTruthy();

		fireEvent.change(screen.getByRole("searchbox", { name: "Search practices" }), {
			target: { value: "clear pull request" },
		});
		expect(screen.getByText(practice.name)).toBeTruthy();
		expect(screen.queryByText("Keep commits focused")).toBeNull();

		fireEvent.change(screen.getByRole("searchbox", { name: "Search practices" }), {
			target: { value: "" },
		});
		const statusFilter = screen.getByRole("combobox", { name: "Filter by status" });
		fireEvent.click(statusFilter);
		const retiredOption = screen.getByRole("option", { name: "Retired" });
		fireEvent.mouseMove(retiredOption);
		fireEvent.click(retiredOption);
		expect(screen.getByText("Create actionable issues")).toBeTruthy();
		expect(screen.queryByText(practice.name)).toBeNull();
	});
});

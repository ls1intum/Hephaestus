import { Link } from "@tanstack/react-router";
import { fireEvent, screen, within } from "@testing-library/react";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";

import type { CatalogPractice } from "@/api/types.gen";
import type { PracticeDefinitionValue } from "@/components/admin/practice-editor/PracticeDefinitionForm";
import { buttonVariants } from "@/components/ui/button";
import { mockPracticeDefinitionOptions } from "@/mocks/fixtures/practice";
import { renderWithRouter } from "@/test/router-harness";

import { CatalogPracticeForm } from "./CatalogPracticeForm";
import { catalogPractice, catalogPracticeDefinition } from "./fixtures";

/** A cancel that navigates, which is what `useUnsavedChanges` guards. */
const cancel = (
	<Link to="/admin/catalog" className={buttonVariants({ variant: "outline" })}>
		Cancel
	</Link>
);

async function renderForm(
	practice: CatalogPractice,
	props: { conflict?: boolean; releaseReview?: ReactNode } = {},
) {
	const onSubmit = vi.fn<(value: PracticeDefinitionValue) => void>();
	const onUseHephaestusVersion = vi.fn<() => void>();
	await renderWithRouter(
		<CatalogPracticeForm
			mode="edit"
			cancel={cancel}
			groups={[{ slug: "communication", name: "Communication" }]}
			definitionOptions={mockPracticeDefinitionOptions}
			practice={practice}
			isPending={false}
			onSubmit={onSubmit}
			onUseHephaestusVersion={onUseHephaestusVersion}
			{...props}
		/>,
		"/admin/catalog",
	);
	return { onSubmit, onUseHephaestusVersion };
}

describe("CatalogPracticeForm", () => {
	it("blocks saving after an edit conflict", async () => {
		await renderForm(catalogPractice(), { conflict: true });

		await screen.findByText("This practice changed while you were editing");
		expect(screen.getByRole<HTMLButtonElement>("button", { name: "Save changes" }).disabled).toBe(
			true,
		);
	});

	it("restores the Hephaestus default only after confirmation", async () => {
		const { onUseHephaestusVersion } = await renderForm(
			catalogPractice({
				state: "EDITED_HERE",
				shipped: catalogPracticeDefinition({ criteria: "The Hephaestus criteria" }),
			}),
		);

		fireEvent.click(await screen.findByRole("button", { name: "Restore Hephaestus default" }));
		expect(onUseHephaestusVersion).not.toHaveBeenCalled();
		const dialog = await screen.findByRole("alertdialog");
		fireEvent.click(within(dialog).getByRole("button", { name: "Restore Hephaestus default" }));

		expect(onUseHephaestusVersion).toHaveBeenCalledOnce();
	});

	it("shows the release review in place of the default panel while an update waits", async () => {
		await renderForm(catalogPractice({ state: "UPDATE_WAITING", changeKind: "REVIEW" }), {
			releaseReview: <p>Release review</p>,
		});

		await screen.findByText("Release review");
		expect(screen.queryByRole("button", { name: "Review Hephaestus update" })).toBeNull();
	});
});

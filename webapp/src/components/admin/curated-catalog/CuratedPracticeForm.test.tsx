import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { renderWithRouter } from "@/test/router-harness";
import { CuratedPracticeForm } from "./CuratedPracticeForm";

vi.mock("@/components/shared/CodeEditor", () => ({
	CodeEditor: () => <div data-testid="code-editor" />,
}));

describe("CuratedPracticeForm", () => {
	it("associates validation messages with invalid fields", async () => {
		await renderWithRouter(
			<CuratedPracticeForm mode="create" areas={[]} isPending={false} onSubmit={vi.fn()} />,
			"/admin/catalog/new",
		);

		fireEvent.click(screen.getByRole("button", { name: "Create practice" }));
		const name = screen.getByRole("textbox", { name: /Name/ });
		expect(name.getAttribute("aria-invalid")).toBe("true");
		expect(name.getAttribute("aria-describedby")).toBe("practice-name-error");
		expect(
			screen.getByRole("textbox", { name: /Evaluation criteria/ }).getAttribute("aria-describedby"),
		).toBe("practice-criteria-description practice-criteria-error");
		expect(screen.getByText("Select at least one trigger event")).toBeTruthy();
	});

	it("asks before discarding an edited draft", async () => {
		await renderWithRouter(
			<CuratedPracticeForm mode="create" areas={[]} isPending={false} onSubmit={vi.fn()} />,
			"/admin/catalog/new",
		);

		fireEvent.change(screen.getByRole("textbox", { name: /Name/ }), {
			target: { value: "A new practice" },
		});
		fireEvent.click(screen.getByRole("link", { name: "Cancel" }));

		expect(
			await screen.findByRole("alertdialog", { name: "Discard unsaved changes?" }),
		).toBeTruthy();
		fireEvent.click(screen.getByRole("button", { name: "Keep editing" }));
		await waitFor(() => expect(screen.queryByRole("alertdialog")).toBeNull());
		expect(screen.getByDisplayValue("A new practice")).toBeTruthy();
	});

	it("keeps the draft when the discard prompt is dismissed with Escape", async () => {
		await renderWithRouter(
			<CuratedPracticeForm mode="create" areas={[]} isPending={false} onSubmit={vi.fn()} />,
			"/admin/catalog/new",
		);

		fireEvent.change(screen.getByRole("textbox", { name: /Name/ }), {
			target: { value: "A new practice" },
		});
		fireEvent.click(screen.getByRole("link", { name: "Cancel" }));
		await screen.findByRole("alertdialog", { name: "Discard unsaved changes?" });

		fireEvent.keyDown(document, { key: "Escape" });

		await waitFor(() => expect(screen.queryByRole("alertdialog")).toBeNull());
		expect(screen.getByDisplayValue("A new practice")).toBeTruthy();
	});

	it("preserves the draft and blocks saving after an edit conflict", async () => {
		await renderWithRouter(
			<CuratedPracticeForm
				mode="edit"
				areas={[]}
				initialData={{
					slug: "clear-pr-description",
					name: "Write a clear pull request description",
					artifactType: "PULL_REQUEST",
					triggerEvents: ["PullRequestCreated"],
					criteria: "Assess whether the description explains the change.",
					status: {
						etag: "tag",
						state: "FROM_HEPHAESTUS",
						changeKind: "NONE",
						offered: true,
						retired: false,
						updatedAt: new Date("2026-07-30T12:00:00Z"),
					},
				}}
				isPending={false}
				conflict
				onContinueWithDraft={vi.fn()}
				onSubmit={vi.fn()}
			/>,
			"/admin/catalog/practices/clear-pr-description",
		);

		expect(screen.getByDisplayValue("Write a clear pull request description")).toBeTruthy();
		expect(
			(screen.getByRole("button", { name: "Save changes" }) as HTMLButtonElement).disabled,
		).toBe(true);
		expect(
			(screen.getByRole("button", { name: "Continue with my draft" }) as HTMLButtonElement)
				.disabled,
		).toBe(false);
	});

	it("shows the waiting Hephaestus definition and confirms before taking it", async () => {
		const onUseHephaestusVersion = vi.fn();
		await renderWithRouter(
			<CuratedPracticeForm
				mode="edit"
				areas={[]}
				initialData={{
					slug: "clear-pr-description",
					name: "Write a clear pull request description",
					artifactType: "PULL_REQUEST",
					triggerEvents: ["PullRequestCreated"],
					criteria: "Assess whether the description explains the change.",
					status: {
						etag: "tag",
						state: "UPDATE_WAITING",
						changeKind: "DETECTION",
						offered: true,
						retired: false,
						updatedAt: new Date("2026-07-30T12:00:00Z"),
					},
					shipped: {
						name: "Say what changed and why",
						artifactType: "PULL_REQUEST",
						triggerEvents: ["PullRequestCreated"],
						criteria: "The updated default criteria",
						whyItMatters: "So a reviewer can start from intent rather than diff archaeology.",
					},
				}}
				isPending={false}
				onUseHephaestusVersion={onUseHephaestusVersion}
				onSubmit={vi.fn()}
			/>,
			"/admin/catalog/practices/clear-pr-description",
		);

		expect(screen.getByText("Hephaestus update available")).toBeTruthy();
		expect(screen.getByText(/would change review behavior/)).toBeTruthy();

		fireEvent.click(screen.getByRole("button", { name: "Review Hephaestus update" }));
		expect(await screen.findByText("The updated default criteria")).toBeTruthy();

		fireEvent.click(screen.getByRole("button", { name: "Apply Hephaestus update" }));

		const confirmation = screen.getByRole("alertdialog", {
			name: "Apply Hephaestus update?",
		});
		expect(confirmation).toBeTruthy();
		expect(screen.getByText(/Existing workspaces remain unchanged/)).toBeTruthy();
		expect(screen.getByText(/Future Hephaestus updates apply automatically/)).toBeTruthy();
		expect(onUseHephaestusVersion).not.toHaveBeenCalled();

		fireEvent.click(within(confirmation).getByRole("button", { name: "Apply Hephaestus update" }));
		expect(onUseHephaestusVersion).toHaveBeenCalledOnce();
	});
});

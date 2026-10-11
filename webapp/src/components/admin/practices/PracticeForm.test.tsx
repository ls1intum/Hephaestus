import { Link } from "@tanstack/react-router";
import { fireEvent, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import type { Practice, UpdatePracticeRequest } from "@/api/types.gen";
import {
	mockAuthorDeclaredEvidenceValidation,
	mockPracticeDefinitionOptions,
	mockPullRequestReviewFields,
	mockPullRequestPolicy,
} from "@/mocks/fixtures/practice";
import { renderWithRouter } from "@/test/router-harness";

import type { PracticeReviewFields } from "@/components/admin/practice-editor/review-settings";
import { PracticeForm } from "./PracticeForm";

const gate = {
	skipReason: "the change has no Swift code",
	anyOf: [{ changedPathMatches: ["**/*.swift"] }],
};

function practice(reviewFields: PracticeReviewFields, guidance: Partial<Practice> = {}): Practice {
	return {
		id: 1,
		slug: "review-swift",
		name: "Review Swift",
		...reviewFields,
		criteria: "Check the work carefully.",
		deliveryBehavior: { summaryOnly: false },
		automatedReviewPolicy: mockPullRequestPolicy,
		automatedReviewValidation: mockAuthorDeclaredEvidenceValidation,
		autonomy: { effective: "AUTOMATIC", inherited: false, source: "PRACTICE" },
		artifactKind: "scm.pull_request",
		createdAt: new Date("2026-01-01"),
		updatedAt: new Date("2026-01-01"),
		displayOrder: 0,
		...guidance,
	};
}

async function renderPractice(
	reviewFields: PracticeReviewFields,
	onSubmit: (slug: string, request: UpdatePracticeRequest, group: string | null) => void,
	guidance: Partial<Practice> = {},
) {
	return renderWithRouter(
		<PracticeForm
			mode="edit"
			workspaceSlug="team"
			groups={[]}
			definitionOptions={mockPracticeDefinitionOptions}
			initialData={practice(reviewFields, guidance)}
			isPending={false}
			cancel={<Link to="/">Cancel</Link>}
			onSubmit={onSubmit}
		/>,
		"/w/team/admin/practices/review-swift",
	);
}

describe("workspace practice scope", () => {
	it("saves the edited static-analysis script with the workspace practice", async () => {
		const onSubmit = vi.fn();
		await renderPractice(mockPullRequestReviewFields, onSubmit, {
			precomputeScript: "export const original = true;",
		});
		fireEvent.change(screen.getByRole("textbox", { name: "Static analysis script" }), {
			target: { value: "export const edited = true;" },
		});
		fireEvent.click(screen.getByRole("button", { name: "Save changes" }));
		await waitFor(() =>
			expect(onSubmit).toHaveBeenCalledWith(
				"review-swift",
				expect.objectContaining({ precomputeScript: "export const edited = true;" }),
				null,
			),
		);
	});

	it("sends declared feedback delivery choices from the shared editor", async () => {
		const onSubmit =
			vi.fn<(slug: string, request: UpdatePracticeRequest, group: string | null) => void>();
		await renderPractice(mockPullRequestReviewFields, onSubmit);
		const user = userEvent.setup();
		await user.click(screen.getByRole("button", { name: /Technical settings/u }));
		await user.click(screen.getByRole("switch", { name: /Show this practice in the summary/u }));
		await user.type(
			screen.getByRole("textbox", { name: "Preferred practice identifier" }),
			"preferred-practice",
		);
		await user.click(screen.getByRole("button", { name: "Save changes" }));

		expect(onSubmit).toHaveBeenCalledWith(
			"review-swift",
			expect.objectContaining({
				deliveryBehavior: { summaryOnly: true, redundantToSlug: "preferred-practice" },
			}),
			null,
		);
	});

	it("keeps an invalid preferred slug in the editor with a field error", async () => {
		const onSubmit = vi.fn();
		await renderPractice(mockPullRequestReviewFields, onSubmit);
		const user = userEvent.setup();
		await user.click(screen.getByRole("button", { name: /Technical settings/u }));
		await user.type(
			screen.getByRole("textbox", { name: "Preferred practice identifier" }),
			"Invalid Slug",
		);
		await user.click(screen.getByRole("button", { name: "Save changes" }));

		expect(onSubmit).not.toHaveBeenCalled();
		expect(
			screen
				.getByRole("textbox", { name: "Preferred practice identifier" })
				.getAttribute("aria-invalid"),
		).toBe("true");
		expect(screen.getAllByText("Use lowercase letters, numbers and single hyphens.")).toHaveLength(
			2,
		);
	});

	it.each([
		["gate", { ...mockPullRequestReviewFields, precondition: gate }],
		["reviewer", { ...mockPullRequestReviewFields, subject: "REVIEWER" as const }],
	])("keeps the %s on a name-only save", async (_label, binding) => {
		const onSubmit =
			vi.fn<(slug: string, request: UpdatePracticeRequest, group: string | null) => void>();
		await renderPractice(binding, onSubmit);
		fireEvent.change(screen.getByRole("textbox", { name: /Name/u }), {
			target: { value: "Review Swift code" },
		});
		fireEvent.click(screen.getByRole("button", { name: "Save changes" }));
		expect(onSubmit).toHaveBeenCalledWith(
			"review-swift",
			expect.objectContaining({
				...binding,
				definitionChanges: undefined,
			}),
			null,
		);
	});

	it("shows the gate and reviewer when both are set", async () => {
		await renderPractice(
			{ ...mockPullRequestReviewFields, precondition: gate, subject: "REVIEWER" },
			vi.fn(),
		);
		expect(screen.getByRole("textbox", { name: "Only review when" })).toHaveProperty(
			"value",
			JSON.stringify(gate, null, 2),
		);
		expect(
			screen.getByRole("combobox", { name: "Person this practice judges" }).textContent,
		).toContain("Reviewer");
	});

	it("marks a reviewer-to-author change as deliberate", async () => {
		const onSubmit =
			vi.fn<(slug: string, request: UpdatePracticeRequest, group: string | null) => void>();
		await renderPractice({ ...mockPullRequestReviewFields, subject: "REVIEWER" }, onSubmit);
		const user = userEvent.setup();
		await user.click(screen.getByRole("combobox", { name: "Person this practice judges" }));
		await user.click(await screen.findByRole("option", { name: "Author" }));
		await waitFor(() =>
			expect(
				screen.getByRole("combobox", { name: "Person this practice judges" }).textContent,
			).toContain("Author"),
		);
		fireEvent.click(screen.getByRole("button", { name: "Save changes" }));
		expect(onSubmit).toHaveBeenCalledWith(
			"review-swift",
			expect.objectContaining({
				definitionChanges: ["SUBJECT"],
				subject: "AUTHOR",
			}),
			null,
		);
	});

	it("marks a gate removal as deliberate", async () => {
		const onSubmit =
			vi.fn<(slug: string, request: UpdatePracticeRequest, group: string | null) => void>();
		await renderPractice({ ...mockPullRequestReviewFields, precondition: gate }, onSubmit);
		fireEvent.change(screen.getByRole("textbox", { name: "Only review when" }), {
			target: { value: "" },
		});
		fireEvent.click(screen.getByRole("button", { name: "Save changes" }));
		expect(onSubmit).toHaveBeenCalledWith(
			"review-swift",
			expect.objectContaining({
				definitionChanges: ["PRECONDITION"],
				precondition: undefined,
				clear: [
					"PRECONDITION",
					"PRACTICE_SCRIPT",
					"WHY_IT_MATTERS",
					"WHAT_GOOD_LOOKS_LIKE",
					"VISUAL",
					"GUIDE",
				],
			}),
			null,
		);
	});
});

describe("the visual and the guide of a workspace practice", () => {
	it("sends both unchanged on an unrelated save", async () => {
		const visual = {
			svg: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 320"><rect class="pv-fill-accent" width="10" height="10"/></svg>',
			alt: "One small square.",
		};
		const guide = {
			markdown: "## How to do it\n\n![The order of the split](figures/split.svg)",
			figures: { split: visual.svg },
		};
		const onSubmit = vi.fn();
		await renderPractice(mockPullRequestReviewFields, onSubmit, { visual, guide });
		fireEvent.change(screen.getByRole("textbox", { name: /Name/u }), {
			target: { value: "Review Swift code" },
		});
		fireEvent.click(screen.getByRole("button", { name: "Save changes" }));

		expect(onSubmit).toHaveBeenCalledWith(
			"review-swift",
			expect.objectContaining({ visual, guide }),
			null,
		);
	});
});

it("removes a reference figure before saving while preserving the guide's other text", async () => {
	const onSubmit = vi.fn();
	await renderPractice(mockPullRequestReviewFields, onSubmit, {
		guide: {
			markdown: "Intro.\n\n![A square][square]\n\n[square]: figures/square.svg",
			figures: {
				square:
					'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1 1"><rect width="1" height="1" /></svg>',
			},
		},
	});
	const user = userEvent.setup();
	await user.click(screen.getByRole("button", { name: "Remove figure square" }));
	await user.click(screen.getByRole("button", { name: "Save changes" }));
	expect(onSubmit).toHaveBeenCalledWith(
		"review-swift",
		expect.objectContaining({
			guide: { markdown: "Intro.\n\n[square]: figures/square.svg", figures: {} },
		}),
		null,
	);
});

const scriptSection = () => screen.queryByRole("button", { name: /^Script/u });
const keptNote = () => screen.queryByText(/Your script stays in this draft/u);

describe("a practice script across support choices", () => {
	const script = "export default definePracticeScript({});";

	async function renderScripted(
		onSubmit: (slug: string, request: UpdatePracticeRequest, group: string | null) => void,
	) {
		await renderWithRouter(
			<PracticeForm
				mode="edit"
				workspaceSlug="team"
				groups={[]}
				definitionOptions={mockPracticeDefinitionOptions}
				initialData={{ ...practice(mockPullRequestReviewFields), practiceScript: script }}
				isPending={false}
				cancel={<Link to="/">Cancel</Link>}
				onSubmit={onSubmit}
			/>,
			"/w/team/admin/practices/review-swift",
		);
	}

	it("comes back when the author chooses review again", async () => {
		const onSubmit =
			vi.fn<(slug: string, request: UpdatePracticeRequest, group: string | null) => void>();
		await renderScripted(onSubmit);
		expect(keptNote()).toBeNull();

		fireEvent.click(screen.getByRole("radio", { name: /^Guidance only/u }));
		expect(scriptSection()).toBeNull();
		expect(keptNote()).not.toBeNull();

		fireEvent.click(screen.getByRole("radio", { name: /^AI-supported mentoring/u }));
		expect(scriptSection()).not.toBeNull();
		expect(keptNote()).toBeNull();
		fireEvent.click(screen.getByRole("button", { name: "Save changes" }));

		const request = onSubmit.mock.calls[0]?.[1];
		expect(request?.practiceScript).toBe(script);
		expect(request?.clear).not.toContain("PRACTICE_SCRIPT");
	});

	it("is cleared by a save that keeps the practice without review", async () => {
		const onSubmit =
			vi.fn<(slug: string, request: UpdatePracticeRequest, group: string | null) => void>();
		await renderScripted(onSubmit);

		fireEvent.click(screen.getByRole("radio", { name: /^Guidance only/u }));
		fireEvent.click(screen.getByRole("button", { name: "Save changes" }));

		const request = onSubmit.mock.calls[0]?.[1];
		expect(request?.practiceScript).toBeUndefined();
		expect(request?.clear).toContain("PRACTICE_SCRIPT");
	});
});

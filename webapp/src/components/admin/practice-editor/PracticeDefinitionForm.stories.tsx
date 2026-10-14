import type { Decorator, Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, waitFor, within } from "storybook/test";

import { Button } from "@/components/ui/button";
import { expectNoOverflowingElement } from "@/stories/reflow";

import { mockDefinitionOptions, mockScan, practiceValueOf } from "./fixtures";
import { PracticeDefinitionForm, type PracticeDefinitionValue } from "./PracticeDefinitionForm";

const groups = [
	{ slug: "review-ready-work", name: "Review-ready work" },
	{ slug: "code-quality", name: "Code quality" },
];

type Submit = (value: PracticeDefinitionValue) => void | Promise<void>;

const submit = fn<Submit>();

/** What the last save sent. */
function lastSaved(): PracticeDefinitionValue | undefined {
	return submit.mock.lastCall?.[0];
}

/** The form scrolls its own body, as it does inside a drawer level. */
const fullHeight: Decorator = (Story) => (
	<div className="flex h-dvh flex-col">
		<Story />
	</div>
);

/**
 * The shared editor both consoles host. Each console wraps it in its own drawer level, whose stories
 * cover the wrapper; these cover the fields and what a save sends.
 */
const meta = {
	component: PracticeDefinitionForm,
	args: {
		groups,
		definitionOptions: mockDefinitionOptions,
		isPending: false,
		cancelAction: (
			<Button type="button" variant="outline">
				Cancel
			</Button>
		),
		onSubmit: submit,
	},
	argTypes: { cancelAction: { control: false } },
	parameters: { layout: "fullscreen" },
	tags: ["autodocs"],
	decorators: [fullHeight],
} satisfies Meta<typeof PracticeDefinitionForm>;

export default meta;
type Story = StoryObj<typeof meta>;

/** The args of an edit of the given practice. */
function editing(initialData = practiceValueOf()) {
	return { mode: "edit" as const, initialData };
}

/** A new practice chooses its kind once; Review on starts at the events the kind recommends. */
export const Create: Story = {
	args: { mode: "create" },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("radio", { name: /^Pull request\b/u })).toBeChecked();
		const strip = within(canvas.getByRole("group", { name: "Review on" }));
		await expect(strip.getByRole("checkbox", { name: /^Opened/u })).toBeChecked();
		await expect(strip.getByRole("checkbox", { name: /^Merged/u })).not.toBeChecked();
		await expect(canvas.getByRole("radio", { name: /^Hephaestus/u })).toBeChecked();
	},
};

/** An existing practice keeps its kind, and Review on, When, About and Applies when are its own. */
export const Edit: Story = {
	args: editing(),
	play: async ({ canvas }) => {
		await expect(canvas.queryByRole("radio", { name: /^Issue/u })).toBeNull();
		await expect(canvas.getByRole("checkbox", { name: "Ready for review" })).toBeChecked();
		await expect(canvas.getByRole("combobox", { name: /^About/u })).toHaveTextContent("Author");
		await expect(canvas.getByRole("textbox", { name: "Applies when" })).toHaveValue("");
	},
};

/** Whoever merged the work is a role a pull request offers, so it stays selectable. */
export const AboutWhoeverMergedIt: Story = {
	args: editing(),
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("combobox", { name: /^About/u }));
		const listbox = await screen.findByRole("listbox", { name: "About" });
		await expect(within(listbox).getByRole("option", { name: "The work" })).toBeVisible();
		await userEvent.click(within(listbox).getByRole("option", { name: "Whoever merged it" }));
		await expect(canvas.getByRole("combobox", { name: /^About/u })).toHaveTextContent(
			"Whoever merged it",
		);
	},
};

/** People review it: the saved reason is shown, and nothing automated is offered. */
export const ReviewedByPeople: Story = {
	args: editing(
		practiceValueOf({
			reads: { contract: "2.0.0", sources: [] },
			reviewedBy: {
				by: "PEOPLE",
				humanReason: {
					code: "DECIDED_IN_A_MEETING",
					description: "The trade-off was agreed in a meeting no review can read.",
				},
				limits: [],
			},
		}),
	),
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("radio", { name: /^People/u })).toBeChecked();
		await expect(canvas.getByRole("textbox", { name: /Why do people review it/u })).toHaveValue(
			"The trade-off was agreed in a meeting no review can read.",
		);
		await expect(canvas.queryByRole("group", { name: "Reads" })).toBeNull();
		await expect(canvas.queryByRole("button", { name: /^Script/u })).toBeNull();
	},
};

/** Choosing Nobody says what the save clears, and the save then clears it. */
export const ReviewedByNobody: Story = {
	args: editing(practiceValueOf({ script: "export default 1;", scan: mockScan })),
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("radio", { name: /^Nobody/u }));
		await expect(
			canvas.getByText(
				"Saving with Nobody clears the read sources, the practice script and the scan settings. Choose Hephaestus again before you save to keep them.",
			),
		).toBeVisible();
		await userEvent.click(canvas.getByRole("button", { name: "Save changes" }));
		await waitFor(async () => expect(submit).toHaveBeenCalledOnce());
		const definition = lastSaved()?.definition;
		await expect(definition?.reviewedBy).toEqual({ by: "NOBODY", humanReason: null, limits: [] });
		await expect(definition?.script).toBeNull();
		await expect(definition?.scan).toBeNull();
	},
};

/** No script means the built-in review, and the section says so while it is closed. */
export const BuiltInScript: Story = {
	args: editing(),
	play: async ({ canvas }) => {
		const section = canvas.getByRole("button", { name: /^Script/u });
		await expect(section).toHaveAttribute("aria-expanded", "false");
		await expect(section).toHaveTextContent(/Built-in/u);
	},
};

export const CustomScript: Story = {
	args: editing(
		practiceValueOf({
			script: "export default definePracticeScript({ async run(ctx) { return ctx.review({}); } });",
		}),
	),
	play: async ({ canvas }) => {
		const section = canvas.getByRole("button", { name: /^Script/u });
		await expect(section).toHaveAttribute("aria-expanded", "true");
		await expect(section).toHaveTextContent(/The program that reviews this practice/u);
	},
};

/**
 * Complete is Required plus a claim the review may make: that something is missing from the source.
 * Optional may be unavailable without the review saying anything about the work.
 */
export const ReadsCompleteVersusRequired: Story = {
	args: editing(
		practiceValueOf({
			reads: {
				contract: "2.0.0",
				sources: [
					{ source: "scm.pull-request.core", need: "REQUIRED" },
					{ source: "scm.review-threads", need: "COMPLETE" },
					{ source: "scm.repository.tree", need: "OPTIONAL" },
				],
			},
		}),
	),
	play: async ({ canvas }) => {
		const reads = within(canvas.getByRole("group", { name: "Reads" }));
		await expect(reads.getByText("· captured whole")).toBeVisible();
		await expect(reads.getByText("Repository files and history")).toBeVisible();
	},
};

export const GoingWell: Story = {
	args: editing(),
	play: async ({ canvas, userEvent }) => {
		const field = canvas.getByRole("textbox", { name: "Going well" });
		await expect(field).toHaveValue("You keep each change small enough to review in one sitting.");
		await userEvent.clear(field);
		await userEvent.type(field, "You keep every change to one purpose.");
		await userEvent.click(canvas.getByRole("button", { name: "Save changes" }));
		await waitFor(async () => expect(submit).toHaveBeenCalledOnce());
		await expect(lastSaved()?.definition.guidance.goingWell).toBe(
			"You keep every change to one purpose.",
		);
	},
};

export const ScanEnabled: Story = {
	args: editing(practiceValueOf({ scan: mockScan })),
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("switch", { name: "Include in scans" })).toBeChecked();
		await expect(canvas.getByRole("textbox", { name: /^Include/u })).toHaveValue(
			"**/*.ts\n**/*.tsx",
		);
	},
};

export const ScanDisabled: Story = {
	args: editing(),
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("switch", { name: "Include in scans" })).not.toBeChecked();
		await expect(canvas.queryByRole("textbox", { name: /^Include/u })).toBeNull();
	},
};

/** A refused pattern is named beside its field and in the summary, and nothing is sent. */
export const ScanInvalid: Story = {
	args: editing(practiceValueOf({ scan: mockScan })),
	play: async ({ canvas, userEvent }) => {
		const include = canvas.getByRole("textbox", { name: /^Include/u });
		await userEvent.clear(include);
		await userEvent.type(include, "../outside/**");
		await userEvent.click(canvas.getByRole("button", { name: "Save changes" }));
		await expect(include).toHaveAccessibleDescription(/A pattern cannot leave the repository/u);
		await expect(submit).not.toHaveBeenCalled();
	},
};

export const SavePending: Story = {
	args: { ...editing(), isPending: true },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("button", { name: "Saving…" })).toBeDisabled();
		await expect(canvas.getByRole("textbox", { name: /^Name/u })).toBeDisabled();
	},
};

/** A refused save keeps the draft, and leaving is a decision again. */
export const SaveFailure: Story = {
	args: {
		...editing(),
		onSubmit: fn<Submit>(async () => {
			throw new Error("Conflict");
		}),
	},
	play: async ({ canvas, userEvent }) => {
		const name = canvas.getByRole("textbox", { name: /^Name/u });
		await userEvent.clear(name);
		await userEvent.type(name, "Small, reviewable changes");
		await userEvent.click(canvas.getByRole("button", { name: "Save changes" }));
		await expect(name).toHaveValue("Small, reviewable changes");
		await expect(canvas.getByRole("button", { name: "Save changes" })).toBeEnabled();
	},
};

/** A blank create is refused with every reason, in the order the fields appear. */
export const Refused: Story = {
	args: { mode: "create" },
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: "Create practice" }));
		const summary = canvas.getByRole("alert");
		await expect(
			within(summary)
				.getAllByRole("link")
				.map((link) => link.textContent),
		).toEqual([
			"Enter a name of 3 to 128 characters.",
			"Enter what to look for.",
			"Set at least one source to Required. A review cannot run without one.",
			"Enter an identifier of 3–64 lowercase letters, numbers and single hyphens.",
		]);
		await expect(submit).not.toHaveBeenCalled();
	},
};

export const NarrowViewport: Story = {
	args: { mode: "create" },
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
	play: async ({ canvasElement }) => {
		await expectNoOverflowingElement(canvasElement);
	},
};

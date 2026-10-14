import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, userEvent, waitFor, within } from "storybook/test";

import type { GoingWellPractice } from "@/api/types.gen";
import { document, GOING_WELL, pullRequest } from "@/stories/practice-profile-story-mock-data";
import { expectNoPageOverflow } from "@/stories/reflow";
import { inStoryYear } from "@/stories/story-clock";

import { GoingWellPractices } from "./GoingWellPractices";

const [linked, unworded] = GOING_WELL;
if (!linked || !unworded) {
	throw new globalThis.Error("The fixtures carry two practices going well");
}
const [proofOf] = linked.evidence;
if (!proofOf) {
	throw new globalThis.Error("The first practice going well carries a result that shows it");
}

/** The `detail` stack a link opens, read off its address. */
const detailOf = (link: HTMLElement) =>
	new URL(String(link.getAttribute("href")), window.location.origin).searchParams.getAll("detail");

const searchOf = (link: HTMLElement, param: string) =>
	new URL(String(link.getAttribute("href")), window.location.origin).searchParams.get(param);

const meta = {
	component: GoingWellPractices,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: { practices: GOING_WELL, state: { status: "ready" } },
} satisfies Meta<typeof GoingWellPractices>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Two practices, each named by a link to its own level, with the results that show them. The
 * workspace's sentence is shown as written; the practice it left without one shows only its name
 * and its proof.
 */
export const TwoLinkedPractices: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("heading", { level: 2, name: "Going well" })).toBeVisible();
		const practice = canvas.getByRole("link", { name: linked.practiceName });
		await expect(practice.closest("h3")).not.toBeNull();
		await expect(detailOf(practice)).toStrictEqual([`practice:${linked.practiceSlug}`]);
		await expect(
			canvas.getByText("Your pull requests say when they are ready and name the issue they close."),
		).toBeVisible();

		// Each result opens its own review at that result, and names the item in the provider's own
		// noun.
		const proof = within(
			canvas.getByRole("list", { name: `Work that shows ${linked.practiceName}` }),
		).getByRole("link", { name: "Pull request #22" });
		await expect(detailOf(proof)).toStrictEqual([`review:${proofOf.reviewId}`]);
		await expect(searchOf(proof, "reviewResult")).toBe(proofOf.resultId);

		// Activating it lands there: the link is the current address.
		await userEvent.click(proof);
		await waitFor(async () => {
			await expect(proof).toHaveAttribute("aria-current", "page");
		});
	},
};

/** No sentence from the workspace: the name and the proof, and no praise put in its place. */
export const NoSentence: Story = {
	args: { practices: [unworded] },
	play: async ({ canvas }) => {
		const [entry] = canvas.getAllByRole("listitem");
		if (!entry) {
			throw new globalThis.Error("The section lists the practice");
		}
		await expect(
			within(entry)
				.getAllByRole("paragraph")
				.map((each) => each.textContent),
		).toEqual(["Met on three pieces of work since 20 August"]);
		await expect(within(entry).getByRole("link", { name: "Conversation #releases" })).toBeVisible();
	},
};

/**
 * Support the reader may not see, such as evidence withheld by consent: the practice and its
 * sentence stand, and no link points at a result that is not there.
 */
export const NoSupport: Story = {
	args: { practices: [{ ...linked, evidence: [] }] },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("link", { name: linked.practiceName })).toBeVisible();
		await expect(canvas.queryByRole("list", { name: /^Work that shows/u })).toBeNull();
	},
};

/** The server sends five results at most; "More" opens the practice on its results. */
export const FiveLinksAndMore: Story = {
	args: {
		practices: [
			{
				...linked,
				workCount: 12,
				evidence: [30, 29, 28, 27, 26].map((number) => ({
					resultId: `0b9c6f0e-0000-4000-8000-0000000010${number}`,
					reviewId: `5d1e2a3b-0000-4000-8000-0000000010${number}`,
					item: pullRequest(number),
					recordedAt: inStoryYear(`09-${String(number - 20).padStart(2, "0")}T10:00`),
				})),
				hasMore: true,
			},
		],
	},
	play: async ({ canvas }) => {
		const proof = canvas.getByRole("list", { name: `Work that shows ${linked.practiceName}` });
		await expect(within(proof).getAllByRole("link", { name: /^Pull request #/u })).toHaveLength(5);
		await expect(canvas.getByText("Met on 12 pieces of work since 12 August")).toBeVisible();

		const more = within(proof).getByRole("link", {
			name: `More results for ${linked.practiceName}`,
		});
		await expect(detailOf(more)).toStrictEqual([`practice:${linked.practiceSlug}`]);
		await expect(searchOf(more, "practiceTab")).toBe("results");
		await userEvent.click(more);
		await waitFor(async () => {
			await expect(more).toHaveAttribute("aria-current", "page");
		});
	},
};

/** Long document titles wrap inside the column on a phone instead of widening the page. */
export const LongProviderLabels: Story = {
	parameters: {
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320] },
	},
	args: {
		practices: [
			{
				...linked,
				practiceSlug: "write-decisions-down",
				practiceName: "Write the decision and its alternatives down where the team can find them",
				evidence: [
					document(
						1,
						"Queue retry policy for the notification pipeline and its dead-letter handling",
					),
					document(
						2,
						"Postmortem: the release train that skipped the canary stage for three weeks",
					),
				].map((item, index) => ({
					resultId: `0b9c6f0e-0000-4000-8000-00000000400${index}`,
					reviewId: `5d1e2a3b-0000-4000-8000-00000000400${index}`,
					item,
					recordedAt: inStoryYear("09-05T10:00"),
				})),
			} satisfies GoingWellPractice,
		],
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("link", { name: /^Document Queue retry policy/u })).toBeVisible();
		await expectNoPageOverflow();
	},
};

/** Nothing going well to name yet: a sentence says what appears here, never "Going well" praise. */
export const Empty: Story = {
	args: { practices: [] },
	play: async ({ canvas }) => {
		await expect(
			canvas.getByText(
				"Practices that your work keeps meeting appear here, with the work that shows it.",
			),
		).toBeVisible();
		await expect(canvas.queryByRole("link")).toBeNull();
	},
};

/** A positive claim waits for its data: skeletons, not an empty "Going well". */
export const Loading: Story = {
	args: { state: { status: "loading" } },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Loading what is going well…")).toBeVisible();
		await expect(canvas.queryByRole("list")).toBeNull();
		await expect(canvas.queryByText(/appear here/u)).toBeNull();
	},
};

export const Error: Story = {
	args: {
		state: {
			status: "error",
			error: new globalThis.Error("The overview is unavailable right now"),
			onRetry: fn(),
		},
	},
	play: async ({ args: { state }, canvas }) => {
		await expect(canvas.getByText("We could not load what is going well")).toBeVisible();
		await userEvent.click(canvas.getByRole("button", { name: "Retry" }));
		await expect(state.status === "error" && state.onRetry).toHaveBeenCalledOnce();
	},
};

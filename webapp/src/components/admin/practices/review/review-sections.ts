import { z } from "zod";

/**
 * The three questions "how does review behave here" splits into.
 *
 * <p>One page, because they were three, and an admin answering "why did this not get reviewed" had
 * to hold all three in their head while the sidebar presented them as unrelated destinations. They
 * are still three sections, because they are genuinely three decisions — how far the system may go,
 * when and where it may act, and what to do about work that predates any of it.
 *
 * <p>The ids are in the URL, so they are part of the contract with anyone's bookmarks and with the
 * redirects from the three pages this replaced. Renaming one is a breaking change to a link.
 */
export const REVIEW_SECTIONS = [
	{
		id: "how-much",
		label: "How much",
		description:
			"How far Hephaestus may go on its own — once for the workspace, or as an exception for an area or a single practice.",
	},
	{
		id: "when-and-where",
		label: "When and where",
		description:
			"What starts a review, how often, and which repositories and branches are in scope.",
	},
	{
		id: "past-work",
		label: "Past work",
		description:
			"Measure work that existed before practice reviews were switched on. This is a one-off campaign you price and confirm.",
	},
] as const;

export type ReviewSectionId = (typeof REVIEW_SECTIONS)[number]["id"];

export const DEFAULT_REVIEW_SECTION: ReviewSectionId = "how-much";

/**
 * `catch` rather than a hard failure: a stale bookmark naming a section that no longer exists should
 * open the page, not a router error. Same for the overrides filter, which the autonomy screen owns
 * and which travels here from the URL it used to live at.
 */
export const reviewSearchSchema = z.object({
	section: z
		.enum(REVIEW_SECTIONS.map((section) => section.id) as [ReviewSectionId, ...ReviewSectionId[]])
		.optional()
		.catch(undefined),
	overrides: z.boolean().optional().catch(undefined),
});

export type ReviewSearch = z.infer<typeof reviewSearchSchema>;

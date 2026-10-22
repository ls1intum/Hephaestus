import type { Meta, StoryObj } from "@storybook/react-vite";
import { Suspense, use } from "react";
import { expect } from "storybook/test";

import { LevelLead } from "@/components/practice-profile/practice-profile-blocks";
import { hasText } from "@/lib/text";
import {
	bundledPracticeGroupNames,
	loadBundledPracticeGroups,
} from "@/stories/practice-guidance-story-mock-data";

import { PracticeFigure } from "./PracticeFigure";
import { PracticeGuideMarkdown } from "./PracticeGuideMarkdown";

const ALL_GROUPS = "all";

interface BundledPracticesArgs {
	/** The slug of the practice group to show, or every group. */
	group: string;
	/** Show each practice's guide under its picture, or only the lead, the picture and the caption. */
	guides: boolean;
}

// Started once, when Storybook first opens this file; the files load in many small chunks.
const groupsLoading = loadBundledPracticeGroups();

type BundledPracticeGroups = Awaited<typeof groupsLoading>;

function shownGroups(groups: BundledPracticeGroups, group: string) {
	return group === ALL_GROUPS ? groups : groups.filter((candidate) => candidate.slug === group);
}

/** Every shown practice's picture, by the words it is named with. */
function shownVisualAlts(groups: BundledPracticeGroups, group: string) {
	return shownGroups(groups, group).flatMap(({ practices }) =>
		practices.flatMap(({ guidance }) => (guidance.visual ? [guidance.visual.alt] : [])),
	);
}

function BundledPractices({ group, guides }: BundledPracticesArgs) {
	const groups = use(groupsLoading);
	return (
		<div className="flex flex-col gap-16">
			{shownGroups(groups, group).map(({ slug, name, practices }) => {
				const guided = practices.filter(({ guidance }) => guidance.visual ?? guidance.guide);
				const unguided = practices.filter(({ guidance }) => !(guidance.visual ?? guidance.guide));
				return (
					<section key={slug} aria-label={name} className="flex flex-col gap-10">
						<h2 className="text-xl font-semibold">{name}</h2>
						{guided.map(({ slug: practiceSlug, name: practiceName, practice, guidance }) => (
							<article key={practiceSlug} className="flex flex-col gap-4">
								<h3 className="text-lg font-semibold">{practiceName}</h3>
								{/* The introduction's lead and figure without its region, which a page may hold once. */}
								<div className="flex max-w-2xl flex-col gap-4">
									{hasText(practice.whyItMatters) && <LevelLead>{practice.whyItMatters}</LevelLead>}
									{guidance.visual && (
										<PracticeFigure visual={guidance.visual} caption={practice.whatGoodLooksLike} />
									)}
								</div>
								{guides &&
									(guidance.guide ? (
										<div className="border-t pt-4">
											<PracticeGuideMarkdown guide={guidance.guide} />
										</div>
									) : (
										<p className="text-sm text-muted-foreground">No guide yet.</p>
									))}
							</article>
						))}
						{unguided.length > 0 && (
							<div className="flex flex-col gap-2">
								<h3 className="text-sm font-medium text-muted-foreground">
									No picture or guide yet
								</h3>
								<ul className="list-disc ps-5 text-sm">
									{unguided.map(({ slug: practiceSlug, name: practiceName }) => (
										<li key={practiceSlug}>{practiceName}</li>
									))}
								</ul>
							</div>
						)}
					</section>
				);
			})}
		</div>
	);
}

/**
 * Every practice the bundled catalog ships, with its lead and picture as a developer sees them and
 * its guide under it. The words, pictures and guides are read from the server's own files, so a new
 * practice appears here without a change. Pick a group to step through one at a time, and turn the
 * guides off to compare only the pictures. A practice with no picture and no guide is listed by
 * name under its group, so the gaps stay visible.
 */
const meta = {
	loaders: [
		async () => {
			await groupsLoading;
			return {};
		},
	],
	render: (args) => (
		<Suspense>
			<BundledPractices {...args} />
		</Suspense>
	),
	parameters: { layout: "padded" },
	args: { group: ALL_GROUPS, guides: true },
	argTypes: {
		group: {
			control: {
				type: "select",
				labels: {
					[ALL_GROUPS]: "All groups",
					...Object.fromEntries(bundledPracticeGroupNames.map(({ slug, name }) => [slug, name])),
				},
			},
			options: [ALL_GROUPS, ...bundledPracticeGroupNames.map(({ slug }) => slug)],
		},
	},
	// Every picture is named by its alt text: a picture that renders unnamed is a gap in the content.
	play: async ({ canvas, args }) => {
		for (const alt of shownVisualAlts(await groupsLoading, args.group)) {
			await expect(await canvas.findByRole("img", { name: alt })).toBeVisible();
		}
		// The guides' figures too, named by the description their Markdown gives them.
		for (const picture of canvas.queryAllByRole("img")) {
			await expect(picture).toHaveAccessibleName();
		}
	},
} satisfies Meta<BundledPracticesArgs>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const Dark: Story = { globals: { theme: "dark" } };

/** About 340px of text column: the shapes, not the labels, must carry each picture here. */
export const Phone: Story = { parameters: { viewport: { defaultViewport: "mobile" } } };

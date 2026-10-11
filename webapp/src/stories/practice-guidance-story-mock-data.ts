/**
 * The guidance the bundled catalog ships, read from the server's own files so the stories show what
 * a developer sees. The folders are read by pattern, so a new practice's picture and guide appear
 * without a change here. Beside them, a picture that brings its own colors.
 */
import type { PracticeGuidance, PracticeStanding } from "@/api/types.gen";

import bundledCatalog from "@bundled-practices";

const GUIDED_SLUG = "scope-one-reviewable-change";

/** A catalog practice as its schema allows it: the visual and the guide are optional. */
interface BundledPractice {
	slug: string;
	name: string;
	whyItMatters?: string;
	whatGoodLooksLike?: string;
	visual?: { file: string; alt: string };
	guide?: string;
}

/** Guidance files keyed by their path from the guidance folder: `<slug>/guide.md`. */
type GuidanceFiles = Map<string, string>;

function guidanceFiles(files: [string, string][]): GuidanceFiles {
	return new Map(
		files.map(([path, text]) => [path.replace(/^.*?practices\/guidance\//u, ""), text]),
	);
}

/** The catalog names a file by its resource path: `practices/guidance/<slug>/visual.svg`. */
function guidanceFile(files: GuidanceFiles, resourcePath: string) {
	const text = files.get(resourcePath.replace(/^practices\/guidance\//u, ""));
	if (text === undefined) {
		throw new Error(`The bundled catalog names ${resourcePath}, which does not exist`);
	}
	return text;
}

/** Each figure in the practice's `figures/` folder, by the name the guide shows it with. */
function figuresOf(files: GuidanceFiles, slug: string) {
	const folder = `${slug}/figures/`;
	return Object.fromEntries(
		[...files]
			.filter(([path]) => path.startsWith(folder) && path.endsWith(".svg"))
			.map(([path, svg]) => [path.slice(folder.length, -".svg".length), svg]),
	);
}

/** One practice's words as its standing holds them and its guidance as the endpoint returns it. */
function guidedPractice(files: GuidanceFiles, practice: BundledPractice) {
	return {
		slug: practice.slug,
		name: practice.name,
		practice: {
			whyItMatters: practice.whyItMatters,
			whatGoodLooksLike: practice.whatGoodLooksLike,
		} satisfies Pick<PracticeStanding, "whyItMatters" | "whatGoodLooksLike">,
		guidance: {
			practiceSlug: practice.slug,
			visual:
				practice.visual === undefined
					? undefined
					: { svg: guidanceFile(files, practice.visual.file), alt: practice.visual.alt },
			guide:
				practice.guide === undefined
					? undefined
					: {
							markdown: guidanceFile(files, practice.guide),
							figures: figuresOf(files, practice.slug),
						},
		} satisfies PracticeGuidance,
	};
}

/** Every bundled practice group, in catalog order, by slug and name. */
export const bundledPracticeGroupNames = bundledCatalog.groups.map(({ slug, name }) => ({
	slug,
	name,
}));

/**
 * Every bundled practice group with each practice's words and guidance. The files load on demand,
 * each in its own chunk, so only the story that shows every practice pays for them.
 */
export async function loadBundledPracticeGroups() {
	const loaders = import.meta.glob<string>("@bundled-practice-guidance/**/*.{md,svg}", {
		query: "?raw",
		import: "default",
	});
	const files = guidanceFiles(
		await Promise.all(
			Object.entries(loaders).map(async ([path, load]): Promise<[string, string]> => [
				path,
				await load(),
			]),
		),
	);
	return bundledCatalog.groups.map((group) => ({
		slug: group.slug,
		name: group.name,
		practices: group.practices.map((practice: BundledPractice) => guidedPractice(files, practice)),
	}));
}

function pilotPractice() {
	const files = guidanceFiles(
		Object.entries(
			import.meta.glob<string>(
				"@bundled-practice-guidance/scope-one-reviewable-change/**/*.{md,svg}",
				{
					query: "?raw",
					import: "default",
					eager: true,
				},
			),
		),
	);
	for (const group of bundledCatalog.groups) {
		for (const candidate of group.practices) {
			if (candidate.slug !== GUIDED_SLUG) {
				continue;
			}
			const { practice, guidance } = guidedPractice(files, candidate);
			const { visual, guide } = guidance;
			const { whyItMatters, whatGoodLooksLike } = practice;
			const splitOrder = guide?.figures["split-order"];
			if (
				whyItMatters !== undefined &&
				whatGoodLooksLike !== undefined &&
				visual !== undefined &&
				guide !== undefined &&
				splitOrder !== undefined
			) {
				const figures = { "split-order": splitOrder };
				return {
					practice: { whyItMatters, whatGoodLooksLike },
					guidance: { practiceSlug: GUIDED_SLUG, visual, guide: { ...guide, figures } },
				};
			}
		}
	}
	throw new Error(`The bundled catalog has no words, visual, guide and figure for ${GUIDED_SLUG}`);
}

const pilot = pilotPractice();

/** The words of "Keep the change reviewable", as its standing carries them. */
export const bundledPractice = pilot.practice;

/** Its picture, guide and figure, as the guidance endpoint returns them. */
export const bundledGuidance = pilot.guidance;

/**
 * A picture an admin drew with its own colors and no `pv-*` class: dark ink that the dark theme
 * would swallow without the light ground under it.
 */
export const unthemedVisual = {
	svg: `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 200">
  <rect x="24" y="40" width="176" height="120" rx="12" fill="#f1f5f9" stroke="#334155" stroke-width="2"/>
  <text x="40" y="108" font-size="20" font-weight="600" fill="#0f172a">Before</text>
  <path d="M212 100h200" stroke="#334155" stroke-width="3" fill="none"/>
  <path d="M424 100l-14-9v18z" fill="#334155"/>
  <rect x="440" y="40" width="176" height="120" rx="12" fill="#dcfce7" stroke="#166534" stroke-width="2"/>
  <text x="456" y="108" font-size="20" font-weight="600" fill="#14532d">After</text>
</svg>`,
	alt: "A box labelled Before with an arrow to a green box labelled After.",
};

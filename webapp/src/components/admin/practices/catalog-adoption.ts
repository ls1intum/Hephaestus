import { z } from "zod";

import type {
	CatalogAdoptionGroup,
	CatalogGroupAdoptionPreview,
	CatalogGroupPracticeAction,
	CatalogPracticePreview,
	CatalogPracticeSummary,
	GroupDefinition,
	PracticeDefinition,
	ReviewedBy,
} from "@/api/types.gen";

/**
 * The adoption payloads, with the fields the server always sends marked present. The generated
 * types leave every field optional, so each payload is checked once where it is read; a payload
 * that misses a field fails its query instead of rendering a practice with no name.
 */
const present = <T>() => z.custom<NonNullable<T>>((value) => value !== undefined && value !== null);

const adoptionGroup = z.object({
	disposition: present<CatalogAdoptionGroup["disposition"]>(),
	slug: z.string().optional(),
	definition: present<GroupDefinition>().optional(),
}) satisfies z.ZodType<CatalogAdoptionGroup>;

const practiceAdoptionPreview = z.object({
	slug: z.string(),
	kind: z.string(),
	definition: present<PracticeDefinition>(),
	availability: present<CatalogPracticePreview["availability"]>(),
	group: adoptionGroup,
	initialFeedbackMode: present<CatalogPracticePreview["initialFeedbackMode"]>(),
	offeredDigest: z.string(),
	etag: z.string(),
}) satisfies z.ZodType<CatalogPracticePreview>;

const groupAdoptionPreview = z.object({
	slug: z.string(),
	definition: present<GroupDefinition>(),
	disposition: present<CatalogGroupAdoptionPreview["disposition"]>(),
	practices: z.array(practiceAdoptionPreview),
	actions: z.array(present<CatalogGroupPracticeAction>()),
	etag: z.string(),
}) satisfies z.ZodType<CatalogGroupAdoptionPreview>;

const adoptablePractice = z.object({
	slug: z.string(),
	name: z.string(),
	kind: z.string(),
	whyItMatters: z.string().optional(),
	groupSlug: z.string().optional(),
	groupName: z.string().optional(),
	availability: present<CatalogPracticeSummary["availability"]>(),
	reviewedBy: present<ReviewedBy>(),
}) satisfies z.ZodType<CatalogPracticeSummary>;

export type PracticeAdoptionPreview = z.infer<typeof practiceAdoptionPreview>;
export type GroupAdoptionPreview = z.infer<typeof groupAdoptionPreview>;
export type AdoptablePractice = z.infer<typeof adoptablePractice>;

export const parsePracticeAdoptionPreview = (data: CatalogPracticePreview) =>
	practiceAdoptionPreview.parse(data);
export const parseGroupAdoptionPreview = (data: CatalogGroupAdoptionPreview) =>
	groupAdoptionPreview.parse(data);
export const parseAdoptablePractices = (data: CatalogPracticeSummary[]) =>
	z.array(adoptablePractice).parse(data);

import { z } from "zod";

/**
 * Document kind enumeration.
 * Shared between routes and services to avoid circular dependencies.
 */
export const DocumentKindEnum = z.enum(["text"]);

export type DocumentKind = z.infer<typeof DocumentKindEnum>;

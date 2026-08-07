import { z } from "zod";

/**
 * The list's URL state.
 *
 * `kind` holds an artifact kind but is deliberately *not* named `artifactKind`: TanStack Router
 * merges every route's search schema into one global type, and the admin review surfaces narrow
 * `artifactKind` to the three kinds this build knows. Reusing that name with a wider type would
 * widen it for them too and break their `search={(previous) => previous}` links.
 *
 * The value stays a free string rather than an enum, because the server derives kinds from
 * whichever integrations are registered — narrowing here would silently drop a link to a kind that
 * shipped after this bundle did. An unknown value costs one 400 that the page reports out loud; a
 * narrowed one would cost the reader a page that quietly ignored their filter.
 */
export const traceSearchSchema = z.object({
	page: z.coerce.number().int().min(0).optional().catch(undefined),
	kind: z.string().min(1).max(120).optional().catch(undefined),
});

export type TraceSearch = z.infer<typeof traceSearchSchema>;

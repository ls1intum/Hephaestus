import { z } from "zod";

/**
 * The list's URL state.
 *
 * `kind` is deliberately *not* named `artifactKind`: TanStack Router merges every route's search
 * schema into one global type, and the admin review surfaces narrow `artifactKind` to the kinds
 * this build knows — reusing the name with a wider type would widen it for them and break their
 * `search={(previous) => previous}` links.
 *
 * It stays a free string rather than an enum because the server derives kinds from whichever
 * integrations are registered: an unknown value costs one 400 the page reports out loud, a narrowed
 * one would quietly ignore the reader's filter.
 */
export const traceSearchSchema = z.object({
	page: z.coerce.number().int().min(0).optional().catch(undefined),
	kind: z.string().min(1).max(120).optional().catch(undefined),
});

export type TraceSearch = z.infer<typeof traceSearchSchema>;

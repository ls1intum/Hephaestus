/**
 * Tag used to mark routes for export to the application-server.
 *
 * Routes with this tag will have `x-hephaestus: { export: true }` added
 * during OpenAPI spec generation, and their referenced schemas will be
 * automatically tagged for export as well.
 *
 * Usage in route definition:
 * ```ts
 * export const myRoute = createRoute({
 *   tags: [...EXPORTED_TAG],
 *   // ...
 * });
 * ```
 */
export const EXPORTED_TAG = ["exported"] as const;

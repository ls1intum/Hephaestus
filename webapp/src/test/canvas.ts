import type { BoundFunctions, queries } from "@testing-library/dom";

/**
 * The object `within(canvasElement)` returns, for a play function that passes it around.
 *
 * Spell it this way rather than `ReturnType<typeof within>`: `within` is generic over its query set,
 * and `ReturnType` cannot supply the type argument, so it collapses to `any` — which silently turns
 * off checking for every query made through the value, typos included.
 */
export type Canvas = BoundFunctions<typeof queries>;

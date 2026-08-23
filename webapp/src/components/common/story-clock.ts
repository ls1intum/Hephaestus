/**
 * The one clock reading behind every story's relative timestamps.
 *
 * A story that shows "4 minutes ago" or a live back-off needs an instant near the moment it renders,
 * so a hard-coded literal is not an option: it would drift into "8 months ago" as the calendar moves
 * and would put every "expires in …" branch permanently in the past. Reading the clock once per
 * module load instead of once per story keeps two timestamps in the same story consistent with each
 * other, keeps a rendered phrase identical on every run, and leaves the story tree with a single
 * place that knows what time it is.
 *
 * Stories only. Component code takes the time from `useNow`.
 */
// oxlint-disable-next-line no-restricted-properties, hephaestus/no-nondeterministic-render -- The single clock reading the story tree is built on, held here precisely so no story reads a moving clock itself; see the note above for why a literal instant cannot replace it.
export const STORY_NOW = Date.now();

const MINUTE_MS = 60_000;
const HOUR_MS = 60 * MINUTE_MS;
const DAY_MS = 24 * HOUR_MS;

/** An instant `minutes` before the story clock — a timestamp that has already happened. */
export const minutesBefore = (minutes: number): Date => new Date(STORY_NOW - minutes * MINUTE_MS);

/** An instant `minutes` after the story clock — a deadline still ahead of the rendered component. */
export const minutesAfter = (minutes: number): Date => new Date(STORY_NOW + minutes * MINUTE_MS);

/** An instant `hours` before the story clock. */
export const hoursBefore = (hours: number): Date => new Date(STORY_NOW - hours * HOUR_MS);

/** An instant `days` before the story clock. */
export const daysBefore = (days: number): Date => new Date(STORY_NOW - days * DAY_MS);

/** An instant `days` after the story clock. */
export const daysAfter = (days: number): Date => new Date(STORY_NOW + days * DAY_MS);

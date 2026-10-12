import { hasText } from "@/lib/text";

/** Word breaks in a name or login: spaces, and the hyphens, underscores and dots logins use. */
const WORD_BREAK = /[\s\-_.]+/u;

/**
 * Initials from a user's name, else their login: the first letters of the first and last words, or
 * the first two letters of a single word. Only words that start with a letter count, so a number
 * ("Synthetic developer 05", "synthetic-developer-01") never becomes an initial.
 */
export function getInitials(name?: string | null, login?: string | null): string {
	for (const value of [name, login]) {
		if (hasText(value)) {
			const initials = initialsOf(value);
			if (initials !== undefined) {
				return initials;
			}
		}
	}
	const fallback = [name, login].find(hasText);
	return fallback === undefined ? "?" : fallback.trim().slice(0, 2).toUpperCase();
}

function initialsOf(value: string): string | undefined {
	const words = value
		.trim()
		.split(WORD_BREAK)
		.filter((word) => /^\p{L}/u.test(word));
	const first = words.at(0);
	const last = words.at(-1);
	if (first === undefined || last === undefined) {
		return undefined;
	}
	if (words.length >= 2) {
		return (first.charAt(0) + last.charAt(0)).toUpperCase();
	}
	return (first.match(/\p{L}/gu) ?? []).slice(0, 2).join("").toUpperCase();
}

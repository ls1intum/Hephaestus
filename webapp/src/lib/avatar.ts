/**
 * Generates initials from a user's name or login.
 * Uses the first letter of the first and last name parts.
 * Falls back to first two characters of login if name is unavailable.
 */
export function getInitials(name?: string | null, login?: string | null): string {
	if (name) {
		const parts = name.trim().split(/\s+/);
		if (parts.length >= 2) {
			return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
		}
		return name.slice(0, 2).toUpperCase();
	}
	if (login) {
		return login.slice(0, 2).toUpperCase();
	}
	return "?";
}

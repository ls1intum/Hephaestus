import { useState } from "react";

const STORAGE_PREFIX = "hephaestus:section-folded:";

/** Storage can be unavailable or full. The section then starts open and forgets the choice. */
function readFolded(key: string): boolean {
	try {
		return window.localStorage.getItem(STORAGE_PREFIX + key) === "true";
	} catch {
		return false;
	}
}

function storeFolded(key: string, folded: boolean) {
	try {
		if (folded) {
			window.localStorage.setItem(STORAGE_PREFIX + key, "true");
		} else {
			window.localStorage.removeItem(STORAGE_PREFIX + key);
		}
	} catch {
		// The choice lasts until the page closes.
	}
}

/**
 * Whether a foldable section is open, remembered in this browser per page and section. A reading
 * preference, not part of what a shared link points at. Without a key the section is always open.
 */
export function useSectionOpen(key: string | undefined): [boolean, (open: boolean) => void] {
	const [open, setOpen] = useState(() => key === undefined || !readFolded(key));
	return [
		open,
		(next) => {
			setOpen(next);
			if (key !== undefined) {
				storeFolded(key, !next);
			}
		},
	];
}

/** Forgets one stored choice. A story or test starts from it, so none inherits another's fold. */
export function forgetSectionFold(key: string) {
	try {
		window.localStorage.removeItem(STORAGE_PREFIX + key);
	} catch {
		// Nothing was stored.
	}
}

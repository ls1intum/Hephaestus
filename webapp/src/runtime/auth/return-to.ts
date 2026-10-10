import { hasText } from "@/lib/text";

// Decode nested escapes before validation, with bounded work for untrusted input.
function fullyDecode(value: string): string {
	let current = value;
	for (let i = 0; i < 5; i += 1) {
		let decoded: string;
		try {
			decoded = decodeURIComponent(current);
		} catch {
			return current;
		}
		if (decoded === current) {
			return current;
		}
		current = decoded;
	}
	return current;
}

/** Accept only local absolute paths, preserving valid percent-encoded segments. */
export function safeReturnTo(value: string | undefined): string {
	if (!hasText(value)) {
		return "/";
	}
	const decoded = fullyDecode(value);
	if (/[\s\p{Cc}]/u.test(decoded)) {
		return "/";
	}
	if (!decoded.startsWith("/") || decoded.startsWith("//")) {
		return "/";
	}
	// Browsers normalize backslashes into slashes; reject scheme and authority-like prefixes too.
	if (/^\/[\\]/u.test(decoded) || /^\/+[a-z]+:/iu.test(decoded) || decoded.startsWith("/@")) {
		return "/";
	}
	// Return the original so encoded query delimiters remain encoded.
	return value;
}

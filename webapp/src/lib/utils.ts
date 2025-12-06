import { type ClassValue, clsx } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
	return twMerge(clsx(inputs));
}

export function sanitizeText(text: string) {
	return text.replace("<has_function_call>", "");
}

const PLACEHOLDER_PREFIX = "WEB_ENV_";

export const sanitizeValue = (value?: string | boolean) => {
	if (typeof value === "boolean") {
		return value ? "true" : "false";
	}
	if (!value) {
		return "";
	}
	const trimmed = value.trim();
	if (!trimmed || trimmed.startsWith(PLACEHOLDER_PREFIX)) {
		return "";
	}
	return trimmed;
};

export const sanitizeBoolean = (value?: string | boolean) => {
	const sanitized = sanitizeValue(value);
	if (!sanitized) {
		return false;
	}
	const normalized = sanitized.toLowerCase();
	return ["true", "1", "yes", "on"].includes(normalized);
};

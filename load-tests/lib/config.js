import exec from "k6/execution";

export function required(name) {
	const value = __ENV[name];
	if (!value) exec.test.abort(`${name} is required`);
	return value;
}

export function integer(name, fallback) {
	const raw = __ENV[name] ?? String(fallback);
	if (!/^\d+$/.test(raw) || !Number.isSafeInteger(Number(raw)) || Number(raw) < 1)
		exec.test.abort(`${name} must be a positive integer`);
	return Number(raw);
}

export function baseUrl() {
	return required("BASE_URL").replace(/\/$/, "");
}

export function apiBaseUrl() {
	return `${baseUrl()}/api`;
}

export function authHeaders() {
	return {
		Authorization: `Bearer ${required("AUTH_TOKEN")}`,
		"Content-Type": "application/json",
	};
}

export function jsonField(response, field) {
	try {
		return response.json(field);
	} catch {
		return undefined;
	}
}

export const sharedThresholds = {
	http_req_failed: ["rate<0.01"],
};

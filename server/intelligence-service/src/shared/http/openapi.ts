import fs from "node:fs";
import { Scalar } from "@scalar/hono-api-reference";
import type { AppOpenAPI } from "./types";

function getWebappVersion(): string {
	try {
		const packageJsonUrl = new URL("../../../../../webapp/package.json", import.meta.url);
		const raw = fs.readFileSync(packageJsonUrl, "utf-8");
		const parsed: unknown = JSON.parse(raw);
		if (
			parsed &&
			typeof parsed === "object" &&
			"version" in parsed &&
			typeof (parsed as { version?: unknown }).version === "string"
		) {
			return (parsed as { version: string }).version;
		}
	} catch {
		// ignore and fall back
	}
	return "0.0.0";
}

export const openAPIConfig = {
	openapi: "3.1.0",
	info: {
		version: getWebappVersion(),
		title: "Hephaestus Intelligence Service API",
	},
};

export default function configureOpenAPI(app: AppOpenAPI) {
	app.doc("/docs", openAPIConfig);

	app.get(
		"/reference",
		Scalar({
			url: "/docs",
			theme: "default",
			layout: "modern",
			defaultHttpClient: {
				targetKey: "js",
				clientKey: "fetch",
			},
		}),
	);
}

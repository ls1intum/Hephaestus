import { Scalar } from "@scalar/hono-api-reference";
import packageJSON from "../../../../package.json" with { type: "json" };
import type { AppOpenAPI } from "./types";

// Derive a version string safely, falling back for local dev or when unset
const VERSION: string =
	(packageJSON as { version?: string }).version ??
	process.env.npm_package_version ??
	"0.0.0-dev";

export const openAPIConfig = {
	openapi: "3.1.0",
	info: {
		version: VERSION,
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

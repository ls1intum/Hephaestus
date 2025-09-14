import { Scalar } from "@scalar/hono-api-reference";
import packageJSON from "../../../../webapp/package.json" with { type: "json" };
import type { AppOpenAPI } from "./types";

export const openAPIConfig = {
	openapi: "3.1.0",
	info: {
		version: packageJSON.version,
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

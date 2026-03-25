import { Scalar } from "@scalar/hono-api-reference";
import type { AppOpenAPI } from "./types";

export const openAPIConfig = {
	openapi: "3.1.0",
	info: {
		version: process.env.APP_VERSION || "0.0.0-development",
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

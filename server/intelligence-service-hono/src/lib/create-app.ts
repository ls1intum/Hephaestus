import { OpenAPIHono } from "@hono/zod-openapi";
import type { Schema } from "hono";
import { cors } from "hono/cors";
import { requestId } from "hono/request-id";
import { notFound, onError, serveEmojiFavicon } from "stoker/middlewares";
import { defaultHook } from "stoker/openapi";

import { pinoLogger } from "@/middlewares/pino-logger";

import type { AppBindings, AppOpenAPI } from "./types";

export function createRouter() {
	return new OpenAPIHono<AppBindings>({
		strict: false,
		defaultHook,
	});
}

export default function createApp() {
	const app = createRouter();
	app.use("/*", cors());
	const isTest =
		process.env.VITEST === "true" || process.env.NODE_ENV === "test";
	const chain = app.use(requestId()).use(serveEmojiFavicon("📝"));
	if (!isTest) {
		chain.use(pinoLogger());
	}

	app.notFound(notFound);
	app.onError(onError);
	return app;
}

export function createTestApp<S extends Schema>(router: AppOpenAPI<S>) {
	process.env.VITEST = "true";
	return createApp().route("/", router);
}

import { OpenAPIHono } from "@hono/zod-openapi";
import type { Schema } from "hono";
import { requestId } from "hono/request-id";
import { notFound, onError, serveEmojiFavicon } from "stoker/middlewares";
import { defaultHook } from "stoker/openapi";

import { pinoLogger } from "./logger";
import type { AppBindings, AppOpenAPI } from "./types";
import { workspaceContext } from "./workspace-context";

export function createRouter() {
	return new OpenAPIHono<AppBindings>({
		strict: false,
		defaultHook,
	});
}

export default function createApp() {
	const app = createRouter();
	const isTest = process.env.VITEST === "true" || process.env.NODE_ENV === "test";

	// Middleware order matters:
	// 1. requestId() - generates request ID for tracing
	// 2. pinoLogger() - logs requests with requestId (must come after requestId)
	// 3. Other middlewares
	app.use(requestId());

	if (!isTest) {
		app.use(pinoLogger());
	}

	app.use(serveEmojiFavicon("📝"));
	app.use(workspaceContext());

	app.notFound(notFound);
	app.onError(onError);
	return app;
}

export function createTestApp<S extends Schema>(router: AppOpenAPI<S>) {
	process.env.VITEST = "true";
	return createApp().route("/", router);
}

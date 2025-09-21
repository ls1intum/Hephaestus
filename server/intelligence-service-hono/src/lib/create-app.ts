import { OpenAPIHono } from "@hono/zod-openapi";
import type { Schema } from "hono";
import { requestId } from "hono/request-id";
import { notFound, onError, serveEmojiFavicon } from "stoker/middlewares";
import { defaultHook } from "stoker/openapi";
import { cors } from 'hono/cors'

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
	app.use('/*', cors())
	app.use(requestId()).use(serveEmojiFavicon("📝")).use(pinoLogger());

	app.notFound(notFound);
	app.onError(onError);
	return app;
}

export function createTestApp<S extends Schema>(router: AppOpenAPI<S>) {
	return createApp().route("/", router);
}

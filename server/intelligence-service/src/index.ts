import "./instrumentation"; // must be first import to initialize OTEL
import { serve } from "@hono/node-server";
import pino from "pino";
import app from "./app";
import env from "./env";

const logger = pino({ level: env.LOG_LEVEL });
const port = env.PORT;
logger.info(`Server is running on http://localhost:${port}`);

serve({
	fetch: app.fetch,
	port,
});

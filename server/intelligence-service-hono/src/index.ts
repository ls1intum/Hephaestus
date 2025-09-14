import "./instrumentation"; // must be first import to initialize OTEL
import { serve } from "@hono/node-server";
import app from "./app";
import env from "./env";

const port = env.PORT;
console.log(`Server is running on port http://localhost:${port}`);

serve({
	fetch: app.fetch,
	port,
});

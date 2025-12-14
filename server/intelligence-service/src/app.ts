import detector from "@/detector";
import { registerMentorRoutes } from "@/mentor/register";
import createApp from "@/shared/http/hono";
import configureOpenAPI from "@/shared/http/openapi";

/**
 * Application assembly
 *
 * This is where all features are composed into the final app.
 * Each feature registers its own routes.
 */
const app = createApp();

// Configure OpenAPI documentation
configureOpenAPI(app);

// Health check (inline - too simple for a separate module)
app.get("/health", (c) => c.json({ status: "OK" as const }));

// Root redirect
app.get("/", (c) => c.redirect("/docs"));

// Feature routes
app.route("/detector", detector);
registerMentorRoutes(app);

export default app;

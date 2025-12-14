import configureOpenAPI from "@/lib/configure-open-api";
import createApp from "@/lib/create-app";
import detector from "@/routes/detector/detector.index";
import health from "@/routes/health.route";
import index from "@/routes/index.route";
import mentor from "@/routes/mentor/index";

const app = createApp();

configureOpenAPI(app);

const routes = [index, health, mentor, detector] as const;

routes.forEach((route) => {
	app.route("/", route);
});

export type AppType = (typeof routes)[number];

export default app;

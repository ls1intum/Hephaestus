import configureOpenAPI from "@/lib/configure-open-api";
import createApp from "@/lib/create-app";
import detector from "@/routes/detector/detector.index";
import health from "@/routes/health.route";
import index from "@/routes/index.route";
import poem from "@/routes/poem/poem.index";
import tasks from "@/routes/tasks/tasks.index";

const app = createApp();

configureOpenAPI(app);

const routes = [index, health, poem, tasks, detector] as const;

routes.forEach((route) => {
	app.route("/", route);
});

export type AppType = (typeof routes)[number];

export default app;

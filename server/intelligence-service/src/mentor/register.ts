import type { AppOpenAPI } from "@/shared/http/types";
import chatRoutes from "./chat";
import documentsRoutes from "./documents";
import threadsRoutes from "./threads";
import voteRoutes from "./vote";

export function registerMentorRoutes(app: AppOpenAPI) {
	return app
		.route("/mentor", chatRoutes)
		.route("/mentor/threads", threadsRoutes)
		.route("/mentor/documents", documentsRoutes)
		.route("/mentor/messages", voteRoutes);
}

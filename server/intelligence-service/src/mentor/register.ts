import type { AppOpenAPI } from "@/shared/http/types";
import chatRoutes from "./chat";
import documentsRoutes from "./documents";
import threadsRoutes from "./threads";
import voteRoutes from "./vote";

export function registerMentorRoutes(app: AppOpenAPI) {
	// Route order matters! More specific routes must be registered first.
	// /mentor/threads/grouped must match before /mentor/threads/{threadId}
	return app
		.route("/mentor/threads", threadsRoutes)
		.route("/mentor/documents", documentsRoutes)
		.route("/mentor/messages", voteRoutes)
		.route("/mentor", chatRoutes);
}

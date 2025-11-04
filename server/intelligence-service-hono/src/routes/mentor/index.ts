import { createRouter } from "@/lib/create-app";
import type { AppOpenAPI } from "@/lib/types";

import threads from "./threads";
import chat from "./chat";
import documents from "./documents";
import vote from "./vote";

const mentor: AppOpenAPI = createRouter().basePath("/mentor");

mentor.route("/", threads);
mentor.route("/", chat);
mentor.route("/", documents);
mentor.route("/", vote);

export default mentor;

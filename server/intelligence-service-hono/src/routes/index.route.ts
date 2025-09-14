import * as HttpStatusCodes from "stoker/http-status-codes";
import { createRouter } from "@/lib/create-app";

const router = createRouter().get("/", (c) => {
	return c.redirect("/reference", HttpStatusCodes.TEMPORARY_REDIRECT);
});

export default router;

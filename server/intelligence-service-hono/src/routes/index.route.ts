import { createRoute } from "@hono/zod-openapi";
import * as HttpStatusCodes from "stoker/http-status-codes";
import { jsonContent } from "stoker/openapi/helpers";
import { createMessageObjectSchema } from "stoker/openapi/schemas";

import { createRouter } from "@/lib/create-app";

const router = createRouter().get("/",
	(c) => {
		return c.redirect("/reference", HttpStatusCodes.TEMPORARY_REDIRECT);
	},
);


export default router;

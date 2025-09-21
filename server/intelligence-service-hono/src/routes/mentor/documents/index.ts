import { createRouter } from "@/lib/create-app";
import type { AppOpenAPI, AppRouteHandler } from "@/lib/types";
import {
	createDocumentHandler,
	deleteAfterHandler,
	deleteDocumentHandler,
	getDocumentHandler,
	getVersionHandler,
	listDocumentsHandler,
	listVersionsHandler,
	updateDocumentHandler,
} from "./documents.handlers";
import {
	createDocumentRoute,
	deleteAfterRoute,
	deleteDocumentRoute,
	getDocumentRoute,
	getVersionRoute,
	listDocumentsRoute,
	listVersionsRoute,
	updateDocumentRoute,
} from "./documents.routes";

const router: AppOpenAPI = createRouter()
	.openapi(
		createDocumentRoute,
		createDocumentHandler as AppRouteHandler<typeof createDocumentRoute>,
	)
	.openapi(
		getDocumentRoute,
		getDocumentHandler as AppRouteHandler<typeof getDocumentRoute>,
	)
	.openapi(
		updateDocumentRoute,
		updateDocumentHandler as AppRouteHandler<typeof updateDocumentRoute>,
	)
	.openapi(
		deleteDocumentRoute,
		deleteDocumentHandler as AppRouteHandler<typeof deleteDocumentRoute>,
	)
	.openapi(
		listDocumentsRoute,
		listDocumentsHandler as AppRouteHandler<typeof listDocumentsRoute>,
	)
	.openapi(
		listVersionsRoute,
		listVersionsHandler as AppRouteHandler<typeof listVersionsRoute>,
	)
	.openapi(
		getVersionRoute,
		getVersionHandler as AppRouteHandler<typeof getVersionRoute>,
	)
	.openapi(
		deleteAfterRoute,
		deleteAfterHandler as AppRouteHandler<typeof deleteAfterRoute>,
	);

export type DocumentsRoutes = typeof router;
export default router;

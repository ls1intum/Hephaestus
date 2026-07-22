import type {
	CreateLlmModelRequest,
	UpdateLlmModelPriceRequest,
	UpdateLlmModelRequest,
	UpdateLlmModelSharingRequest,
} from "@/api/types.gen";

export interface AdminLlmModelSaveBody {
	metadata: CreateLlmModelRequest | UpdateLlmModelRequest;
	price: UpdateLlmModelPriceRequest;
	sharing: UpdateLlmModelSharingRequest;
}

interface ModelSaveOperations {
	create: (connectionId: number, body: CreateLlmModelRequest) => Promise<{ id: number }>;
	updateMetadata: (id: number, body: UpdateLlmModelRequest) => Promise<unknown>;
	updatePrice: (id: number, body: UpdateLlmModelPriceRequest) => Promise<unknown>;
	updateSharing: (id: number, body: UpdateLlmModelSharingRequest) => Promise<unknown>;
}

interface SaveAdminLlmModelOptions {
	connectionId: number;
	editing: { id: number; enabled: boolean } | null;
	body: AdminLlmModelSaveBody;
	operations: ModelSaveOperations;
}

export class AdminLlmModelSaveError extends Error {
	readonly modelId?: number;

	constructor(error: unknown, modelId?: number) {
		super(error instanceof Error ? error.message : "Could not save the model", { cause: error });
		this.name = "AdminLlmModelSaveError";
		this.modelId = modelId;
	}
}

/**
 * Sequences the catalog's dedicated endpoints without ever exposing a partially configured model.
 * Newly created models stay inactive until pricing and sharing are saved. An active model being
 * revoked is disabled before its other properties change.
 */
export async function saveAdminLlmModelSafely({
	connectionId,
	editing,
	body,
	operations,
}: SaveAdminLlmModelOptions): Promise<void> {
	let modelId: number | undefined;
	const shouldEnable = body.metadata.enabled === true;

	try {
		if (!editing) {
			const created = await operations.create(connectionId, {
				...(body.metadata as CreateLlmModelRequest),
				enabled: false,
			});
			modelId = created.id;
		} else {
			modelId = editing.id;
			if (editing.enabled) {
				await operations.updateMetadata(
					editing.id,
					shouldEnable ? { enabled: false } : (body.metadata as UpdateLlmModelRequest),
				);
			}
		}

		await operations.updatePrice(modelId, body.price);
		await operations.updateSharing(modelId, body.sharing);

		if (!editing && shouldEnable) {
			await operations.updateMetadata(modelId, { enabled: true });
		} else if (editing && (!editing.enabled || shouldEnable)) {
			await operations.updateMetadata(editing.id, body.metadata as UpdateLlmModelRequest);
		}
	} catch (error) {
		if (modelId == null) throw error;
		throw new AdminLlmModelSaveError(error, modelId);
	}
}

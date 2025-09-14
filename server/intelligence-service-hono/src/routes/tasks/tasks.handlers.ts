import * as HttpStatusCodes from "stoker/http-status-codes";
import * as HttpStatusPhrases from "stoker/http-status-phrases";

import type { AppRouteHandler } from "@/lib/types";
import { ZOD_ERROR_CODES, ZOD_ERROR_MESSAGES } from "@/lib/constants";
import { type Task } from "./tasks.schemas";

import type {
	CreateRoute,
	GetOneRoute,
	ListRoute,
	PatchRoute,
	RemoveRoute,
} from "./tasks.routes";

// In-memory task store
let seq = 1;
const store: Task[] = [];

export const list: AppRouteHandler<ListRoute> = async (c) => {
  return c.json(store);
};

export const create: AppRouteHandler<CreateRoute> = async (c) => {
	const body = c.req.valid("json");
	const newTask: Task = { id: seq++, name: body.name, done: body.done ?? false };
	store.push(newTask);
	return c.json(newTask, HttpStatusCodes.OK);
};

export const getOne: AppRouteHandler<GetOneRoute> = async (c) => {
  const { id } = c.req.valid("param");
  const task = store.find((t) => t.id === id);

	if (!task) {
		return c.json(
			{
				message: HttpStatusPhrases.NOT_FOUND,
			},
			HttpStatusCodes.NOT_FOUND,
		);
	}

		return c.json(task, HttpStatusCodes.OK);
};

export const patch: AppRouteHandler<PatchRoute> = async (c) => {
  const { id } = c.req.valid("param");
  const updates = c.req.valid("json");

	if (Object.keys(updates).length === 0) {
		return c.json(
			{
				success: false,
				error: {
					issues: [
						{
							code: ZOD_ERROR_CODES.INVALID_UPDATES,
							path: [],
							message: ZOD_ERROR_MESSAGES.NO_UPDATES,
						},
					],
					name: "ZodError",
				},
			},
			HttpStatusCodes.UNPROCESSABLE_ENTITY,
		);
	}

  const idx = store.findIndex((t) => t.id === id);
  if (idx === -1) {
		return c.json(
			{
				message: HttpStatusPhrases.NOT_FOUND,
			},
			HttpStatusCodes.NOT_FOUND,
		);
	}
  const updated = { ...store[idx], ...updates } satisfies Task;
  store[idx] = updated;
  return c.json(updated, HttpStatusCodes.OK);
};

export const remove: AppRouteHandler<RemoveRoute> = async (c) => {
  const { id } = c.req.valid("param");
  const before = store.length;
  const idx = store.findIndex((t) => t.id === id);
  if (idx !== -1) store.splice(idx, 1);
  const rowsAffected = idx !== -1 ? 1 : 0;

	if (rowsAffected === 0) {
		return c.json(
			{
				message: HttpStatusPhrases.NOT_FOUND,
			},
			HttpStatusCodes.NOT_FOUND,
		);
	}

	return c.body(null, HttpStatusCodes.NO_CONTENT);
};

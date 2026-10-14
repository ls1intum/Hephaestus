import { act, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse, type PathParams } from "msw";
import { assert, expect, it, vi } from "vitest";

import type { ChatMessage } from "@/lib/types";
import { currentUser } from "@/mocks/fixtures/auth";
import { workspaceOnboarding } from "@/mocks/fixtures/onboarding";
import { ownMembership, workspaceListItem } from "@/mocks/fixtures/workspaces";
import { server } from "@/mocks/server";
import { currentUserQueryOptions } from "@/runtime/auth/guard";
import { renderRouteAtWithRouter, ROUTE_RENDER_WAIT } from "@/test/router-harness";

vi.setConfig({ testTimeout: 30_000 });

const answerId = "3c634344-99c8-43d5-989a-f1d6913b7537";
const savedThreadId = "18a4740d-c558-4d49-8fe6-118c76bd723b";

interface Posted {
	id: string;
	message: ChatMessage;
	trigger: string;
}

function mockMentor() {
	server.use(
		http.get("*/workspaces", () =>
			HttpResponse.json([workspaceListItem("acme"), workspaceListItem("other")]),
		),
		http.get("*/user/features", () => HttpResponse.json({})),
		http.get("*/workspaces/:workspaceSlug/members/me", () =>
			HttpResponse.json(ownMembership()),
		),
		http.get("*/workspaces/:workspaceSlug/onboarding/me", () =>
			HttpResponse.json({
				...workspaceOnboarding(),
				aiChoice: "IN_HOUSE_ONLY",
				aiOptions: [
					{ choice: "IN_HOUSE_ONLY", mentorReady: true, practiceReviewsReady: true, models: [] },
				],
			}),
		),
		http.get("*/workspaces/:workspaceSlug/mentor/threads", () => HttpResponse.json([])),
	);
}

const STREAM_HEADERS = {
	"Content-Type": "text/event-stream",
	"x-vercel-ai-ui-message-stream": "v1",
};

/** A reply the test writes chunk by chunk, so it is still open while the reader moves around. */
function heldReply() {
	const encoder = new TextEncoder();
	const { readable, writable } = new TransformStream<Uint8Array, Uint8Array>();
	const writer = writable.getWriter();
	return {
		response: () => new HttpResponse(readable, { headers: STREAM_HEADERS }),
		send: (...chunks: object[]) => {
			for (const chunk of chunks) {
				void writer.write(encoder.encode(`data: ${JSON.stringify(chunk)}\n\n`));
			}
		},
		close: () => {
			void writer.write(encoder.encode("data: [DONE]\n\n"));
			void writer.close();
		},
	};
}

function finishedReply(text: string) {
	const chunks = [
		{ type: "start", messageId: "c3f0a1d2-5e6b-4c7d-8e9f-0a1b2c3d4e5f" },
		{ type: "text-start", id: "t" },
		{ type: "text-delta", id: "t", delta: text },
		{ type: "text-end", id: "t" },
		{ type: "finish" },
	];
	return new HttpResponse(
		[...chunks.map((chunk) => `data: ${JSON.stringify(chunk)}\n\n`), "data: [DONE]\n\n"].join(""),
		{ headers: STREAM_HEADERS },
	);
}

/** What the server holds while a reply streams: the prompt, and nothing of the reply yet. */
function storedPrompt(posted: Posted[]) {
	return http.get("*/workspaces/acme/mentor/threads/:threadId", ({ params }) => {
		const prompt = posted.find((entry) => entry.id === params.threadId);
		if (prompt === undefined) {
			return HttpResponse.json(
				{
					id: savedThreadId,
					createdAt: "2026-09-30T00:06:21.622Z",
					messages: [
						{
							id: "95d58e22-2c05-4e43-9e91-242f28b43089",
							role: "user",
							parts: [{ type: "text", text: "An older question" }],
						},
					],
					votes: {},
				},
				{ status: params.threadId === savedThreadId ? 200 : 404 },
			);
		}
		return HttpResponse.json({
			id: prompt.id,
			createdAt: "2026-09-30T00:06:21.622Z",
			messages: [prompt.message],
			votes: {},
		});
	});
}

/** Asks in the panel and waits until the reply has started streaming there. */
async function askInPanel(reply: ReturnType<typeof heldReply>, posted: Posted[]) {
	const user = userEvent.setup();
	await user.click(
		await screen.findByRole("button", { name: "Open Heph, AI mentor" }, ROUTE_RENDER_WAIT),
	);
	await user.type(await screen.findByRole("textbox", { name: "Message" }), "Plan issue 12{Enter}");
	await waitFor(() => expect(posted).toHaveLength(1));
	reply.send(
		{ type: "start", messageId: answerId },
		{ type: "text-start", id: "t" },
		{ type: "text-delta", id: "t", delta: "First step: " },
	);
	await screen.findByText(/First step:/u);
	const [first] = posted;
	assert.isDefined(first);
	return first;
}

async function openFullChat(
	router: ReturnType<typeof renderRouteAtWithRouter>["router"],
	id: string,
) {
	await userEvent.click(screen.getByRole("button", { name: "Open full chat" }));
	await waitFor(() => expect(router.state.location.pathname).toBe(`/w/acme/mentor/${id}`));
	await screen.findByRole("heading", { name: "Conversation with Heph" }, ROUTE_RENDER_WAIT);
}

it("keeps the panel's live answer when the full chat opens, and finishes it there", async () => {
	mockMentor();
	const reply = heldReply();
	const posted: Posted[] = [];
	server.use(
		http.post<PathParams, Posted>("*/workspaces/acme/mentor/chat", async ({ request }) => {
			posted.push(await request.json());
			return reply.response();
		}),
		storedPrompt(posted),
	);
	const { router } = renderRouteAtWithRouter("/w/acme/teams");
	const prompt = await askInPanel(reply, posted);

	await openFullChat(router, prompt.id);

	await screen.findByText(/First step:/u);
	screen.getByRole("button", { name: "Stop generating" });
	reply.send(
		{ type: "text-delta", id: "t", delta: "split the migration." },
		{ type: "text-end", id: "t" },
		{ type: "finish" },
	);
	reply.close();
	await screen.findByText(/First step: split the migration\./u);
	await waitFor(() => expect(screen.queryByRole("button", { name: "Stop generating" })).toBeNull());
	expect(posted).toHaveLength(1);
});

it("stops the taken-over answer from the full chat, keeping what arrived", async () => {
	mockMentor();
	const reply = heldReply();
	const posted: Posted[] = [];
	let aborted = false;
	server.use(
		http.post<PathParams, Posted>("*/workspaces/acme/mentor/chat", async ({ request }) => {
			request.signal.addEventListener("abort", () => {
				aborted = true;
			});
			posted.push(await request.json());
			return reply.response();
		}),
		storedPrompt(posted),
	);
	const { router } = renderRouteAtWithRouter("/w/acme/teams");
	const prompt = await askInPanel(reply, posted);
	await openFullChat(router, prompt.id);

	await userEvent.click(await screen.findByRole("button", { name: "Stop generating" }));

	await waitFor(() => expect(aborted).toBe(true));
	await waitFor(() => expect(screen.queryByRole("button", { name: "Stop generating" })).toBeNull());
	screen.getByText(/First step:/u);
	expect(posted).toHaveLength(1);
});

it("says Heph is busy on the full chat when the taken-over turn is refused, and retries the same conversation", async () => {
	mockMentor();
	const reply = heldReply();
	const posted: Posted[] = [];
	server.use(
		http.post<PathParams, Posted>(
			"*/workspaces/acme/mentor/chat",
			async ({ request }) => {
				posted.push(await request.json());
				return reply.response();
			},
			{ once: true },
		),
		http.post<PathParams, Posted>("*/workspaces/acme/mentor/chat", async ({ request }) => {
			posted.push(await request.json());
			return finishedReply("The worker is available again.");
		}),
		storedPrompt(posted),
	);
	const { router } = renderRouteAtWithRouter("/w/acme/teams");
	const prompt = await askInPanel(reply, posted);
	await openFullChat(router, prompt.id);

	// `UIMessageChunk.DataMentorStatus.of("busy", null)` on the server, after the page took over.
	reply.send(
		{
			type: "data-mentor-status",
			id: "mentor-status",
			data: { state: "busy", reason: null },
			transient: true,
		},
		{ type: "error", errorText: "Heph is busy. Try again." },
	);
	reply.close();
	await screen.findByText("Heph is busy", { exact: true });
	await userEvent.click(screen.getByRole("button", { name: "Retry" }));

	await screen.findByText("The worker is available again.");
	expect(posted).toHaveLength(2);
	expect(posted[1]).toMatchObject({ id: prompt.id, trigger: "regenerate-message" });
});

it("keeps a saved conversation apart from the live one, and the live one ahead of its saved copy", async () => {
	mockMentor();
	const reply = heldReply();
	const posted: Posted[] = [];
	server.use(
		http.post<PathParams, Posted>("*/workspaces/acme/mentor/chat", async ({ request }) => {
			posted.push(await request.json());
			return reply.response();
		}),
		storedPrompt(posted),
	);
	const { router } = renderRouteAtWithRouter("/w/acme/teams");
	const prompt = await askInPanel(reply, posted);
	await openFullChat(router, prompt.id);

	await act(async () => {
		await router.navigate({
			to: "/w/$workspaceSlug/mentor/$threadId",
			params: { workspaceSlug: "acme", threadId: savedThreadId },
		});
	});
	await screen.findByText("An older question", undefined, ROUTE_RENDER_WAIT);
	expect(screen.queryByText(/First step:/u)).toBeNull();
	expect(screen.queryByText("Plan issue 12")).toBeNull();

	reply.send(
		{ type: "text-delta", id: "t", delta: "split the migration." },
		{ type: "text-end", id: "t" },
		{ type: "finish" },
	);
	reply.close();
	await act(async () => {
		await router.navigate({
			to: "/w/$workspaceSlug/mentor/$threadId",
			params: { workspaceSlug: "acme", threadId: prompt.id },
		});
	});
	// The saved copy holds only the prompt; the conversation shown is the one that finished streaming.
	await screen.findByText(/First step: split the migration\./u, undefined, ROUTE_RENDER_WAIT);
	expect(screen.queryByText("An older question")).toBeNull();
	expect(posted).toHaveLength(1);
});

it("stops a saved conversation's answer when the reader leaves its page", async () => {
	mockMentor();
	const reply = heldReply();
	const posted: Posted[] = [];
	let aborted = false;
	server.use(
		http.post<PathParams, Posted>("*/workspaces/acme/mentor/chat", async ({ request }) => {
			request.signal.addEventListener("abort", () => {
				aborted = true;
			});
			posted.push(await request.json());
			return reply.response();
		}),
		storedPrompt(posted),
	);
	const user = userEvent.setup();
	const { router } = renderRouteAtWithRouter(`/w/acme/mentor/${savedThreadId}`);
	await screen.findByText("An older question", undefined, ROUTE_RENDER_WAIT);
	await user.type(await screen.findByRole("textbox", { name: "Message" }), "Plan issue 12{Enter}");
	await waitFor(() => expect(posted).toHaveLength(1));
	expect(posted[0]?.id).toBe(savedThreadId);
	reply.send(
		{ type: "start", messageId: answerId },
		{ type: "text-start", id: "t" },
		{ type: "text-delta", id: "t", delta: "First step: " },
	);
	await screen.findByText(/First step:/u);
	screen.getByRole("button", { name: "Stop generating" });

	await act(async () => {
		await router.navigate({ to: "/w/$workspaceSlug/teams", params: { workspaceSlug: "acme" } });
	});

	await waitFor(() => expect(aborted).toBe(true));
	expect(posted).toHaveLength(1);
});

it("stops a live answer when the workspace changes, and starts the other workspace empty", async () => {
	mockMentor();
	const reply = heldReply();
	const posted: Posted[] = [];
	let aborted = false;
	server.use(
		http.post<PathParams, Posted>("*/workspaces/acme/mentor/chat", async ({ request }) => {
			request.signal.addEventListener("abort", () => {
				aborted = true;
			});
			posted.push(await request.json());
			return reply.response();
		}),
		storedPrompt(posted),
	);
	const { router } = renderRouteAtWithRouter("/w/acme/teams");
	await askInPanel(reply, posted);

	await act(async () => {
		await router.navigate({ to: "/w/$workspaceSlug/teams", params: { workspaceSlug: "other" } });
	});

	await waitFor(() => expect(aborted).toBe(true));
	await userEvent.click(
		await screen.findByRole("button", { name: "Open Heph, AI mentor" }, ROUTE_RENDER_WAIT),
	);
	await screen.findByRole("textbox", { name: "Message" });
	expect(screen.queryByText(/First step:/u)).toBeNull();
	expect(screen.queryByText("Plan issue 12")).toBeNull();
});

it("starts another account with the same username on a new, empty conversation", async () => {
	mockMentor();
	const reply = heldReply();
	const posted: Posted[] = [];
	let aborted = false;
	server.use(
		http.get("*/user", () => HttpResponse.json(currentUser)),
		http.post<PathParams, Posted>(
			"*/workspaces/acme/mentor/chat",
			async ({ request }) => {
				posted.push(await request.json());
				return finishedReply("The first answer.");
			},
			{ once: true },
		),
		http.post<PathParams, Posted>(
			"*/workspaces/acme/mentor/chat",
			async ({ request }) => {
				request.signal.addEventListener("abort", () => {
					aborted = true;
				});
				posted.push(await request.json());
				return reply.response();
			},
			{ once: true },
		),
		http.post<PathParams, Posted>("*/workspaces/acme/mentor/chat", async ({ request }) => {
			posted.push(await request.json());
			return new HttpResponse(null, { status: 503 });
		}),
		http.post("*/workspaces/acme/mentor/threads/:threadId/messages/:messageId/vote", () =>
			HttpResponse.json({}),
		),
	);
	const user = userEvent.setup();
	const { queryClient } = renderRouteAtWithRouter("/w/acme/teams");
	await user.click(
		await screen.findByRole("button", { name: "Open Heph, AI mentor" }, ROUTE_RENDER_WAIT),
	);
	await user.type(await screen.findByRole("textbox", { name: "Message" }), "First question{Enter}");
	await screen.findByText("The first answer.");
	await user.click(screen.getByRole("button", { name: "Good response" }));
	await user.type(screen.getByRole("textbox", { name: "Message" }), "Plan issue 12{Enter}");
	await waitFor(() => expect(posted).toHaveLength(2));
	reply.send(
		{ type: "start", messageId: answerId },
		{ type: "text-start", id: "t" },
		{ type: "text-delta", id: "t", delta: "First step: " },
	);
	await screen.findByText(/First step:/u);

	// Another account of the same provider login: the username matches, the account does not.
	await act(async () => {
		queryClient.setQueryData(currentUserQueryOptions().queryKey, { ...currentUser, id: 43 });
	});

	await waitFor(() => expect(aborted).toBe(true));
	await waitFor(() => expect(screen.queryByText("The first answer.")).toBeNull());
	expect(screen.queryByText(/First step:/u)).toBeNull();
	expect(screen.queryByText("First question")).toBeNull();
	expect(screen.queryByRole("button", { name: "Good response" })).toBeNull();
	await user.type(screen.getByRole("textbox", { name: "Message" }), "My own question{Enter}");
	await waitFor(() => expect(posted).toHaveLength(3));
	expect(posted[2]?.id).not.toBe(posted[0]?.id);
	expect(posted[2]?.message.parts).toStrictEqual([{ type: "text", text: "My own question" }]);
});

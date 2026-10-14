import { QueryClient, QueryClientProvider, useQuery } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import type { ReactNode } from "react";
import { expect, it } from "vitest";
import { currentDefinition } from "@/components/admin/practices/practice-definition-fields";

import { getPracticeGuidanceOptions } from "@/api/@tanstack/react-query.gen";
import type { PracticeGuidanceDto } from "@/api/types.gen";
import { mockPractice } from "@/components/admin/practices/fixtures";
import { server } from "@/mocks/server";
import { deferred } from "@/test/async";

import { usePracticeEditor } from "./use-practice-editor";

const { slug } = mockPractice;
const definition = currentDefinition(mockPractice);
const updateRequest = { definition, expectedVersionId: mockPractice.currentVersion.id };
const options = getPracticeGuidanceOptions({ path: { workspaceSlug: "acme", practiceSlug: slug } });
const guidance = (markdown: string | null): PracticeGuidanceDto => ({
	practiceSlug: slug,
	visual: null,
	guide: markdown === null ? null : { markdown, figures: {} },
});
const earlier = guidance("Earlier guide");
const updated = guidance("Updated guide");

function reader(client: QueryClient) {
	return renderHook(() => ({ editor: usePracticeEditor("acme"), guidance: useQuery(options) }), {
		wrapper: ({ children }: { children: ReactNode }) => (
			<QueryClientProvider client={client}>{children}</QueryClientProvider>
		),
	});
}

const practiceResponse = () => HttpResponse.json(mockPractice);

it("replaces the whole definition against the version it was opened on", async () => {
	const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
	let body: unknown;
	server.use(
		http.patch("*/workspaces/acme/practices/:practiceSlug", async ({ request }) => {
			body = await request.json();
			return practiceResponse();
		}),
		http.get("*/workspaces/acme/practices/:practiceSlug/guidance", () =>
			HttpResponse.json(updated),
		),
	);
	const { result } = reader(client);
	await act(async () => {
		await result.current.editor.update(mockPractice, updateRequest, mockPractice.groupSlug);
	});
	expect(body).toStrictEqual(updateRequest);
});

it("moves the practice to another group in a separate call after the new version", async () => {
	const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
	const calls: string[] = [];
	let groupBody: unknown;
	server.use(
		http.patch("*/workspaces/acme/practices/:practiceSlug", () => {
			calls.push("definition");
			return practiceResponse();
		}),
		http.put("*/workspaces/acme/practices/:practiceSlug/group", async ({ request }) => {
			calls.push("group");
			groupBody = await request.json();
			return HttpResponse.json({ ...mockPractice, groupSlug: null });
		}),
		http.get("*/workspaces/acme/practices/:practiceSlug/guidance", () =>
			HttpResponse.json(updated),
		),
	);
	const { result } = reader(client);
	let saved: { groupSlug: string | null } | undefined;
	await act(async () => {
		saved = await result.current.editor.update(mockPractice, updateRequest, null);
	});
	expect(calls).toStrictEqual(["definition", "group"]);
	expect(groupBody).toStrictEqual({ groupSlug: null });
	expect(saved?.groupSlug).toBeNull();
});

it("refreshes cached guidance after an edit without changing another workspace", async () => {
	const client = new QueryClient({
		defaultOptions: { queries: { retry: false, staleTime: 30_000 } },
	});
	client.setQueryData(options.queryKey, earlier);
	const otherOptions = getPracticeGuidanceOptions({
		path: { workspaceSlug: "other", practiceSlug: slug },
	});
	client.setQueryData(otherOptions.queryKey, earlier);
	server.use(
		http.patch("*/workspaces/acme/practices/:practiceSlug", practiceResponse),
		http.get("*/workspaces/acme/practices/:practiceSlug/guidance", () =>
			HttpResponse.json(updated),
		),
	);
	const { result } = reader(client);
	expect(result.current.guidance.data).toStrictEqual(earlier);
	await act(async () => {
		await result.current.editor.update(mockPractice, updateRequest, mockPractice.groupSlug);
	});
	await waitFor(() => expect(result.current.guidance.data).toStrictEqual(updated));
	expect(client.getQueryData(otherOptions.queryKey)).toStrictEqual(earlier);
});

/** An old first read, followed by the current value after the write. */
function heldFirstGuidance(first: Promise<void>) {
	let reads = 0;
	return {
		reads: () => reads,
		handler: http.get("*/workspaces/acme/practices/:practiceSlug/guidance", async () => {
			reads += 1;
			if (reads === 1) {
				await first;
				return HttpResponse.json(earlier);
			}
			return HttpResponse.json(updated);
		}),
	};
}

it("replaces an in-flight first guidance read after saving and prevents its old response from returning", async () => {
	const first = deferred();
	const source = heldFirstGuidance(first.promise);
	server.use(
		http.patch("*/workspaces/acme/practices/:practiceSlug", practiceResponse),
		source.handler,
	);
	const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
	const { result } = reader(client);
	await waitFor(() => expect(source.reads()).toBe(1));
	await act(async () => {
		await result.current.editor.update(mockPractice, updateRequest, mockPractice.groupSlug);
	});
	await waitFor(() => expect(result.current.guidance.data).toStrictEqual(updated));
	await act(async () => {
		first.resolve();
		await first.promise;
	});
	expect(result.current.guidance.data).toStrictEqual(updated);
	expect(source.reads()).toBe(2);
});

it("refreshes a previously cached absent guide after creating the practice", async () => {
	const client = new QueryClient({
		defaultOptions: { queries: { retry: false, staleTime: 30_000 } },
	});
	client.setQueryData(options.queryKey, guidance(null));
	server.use(
		http.post("*/workspaces/acme/practices", practiceResponse),
		http.get("*/workspaces/acme/practices/:practiceSlug/guidance", () =>
			HttpResponse.json(updated),
		),
	);
	const { result } = reader(client);
	await act(async () => {
		await result.current.editor.create({
			slug,
			kind: mockPractice.kind,
			definition,
			groupSlug: mockPractice.groupSlug,
			position: null,
		});
	});
	await waitFor(() => expect(result.current.guidance.data).toStrictEqual(updated));
});

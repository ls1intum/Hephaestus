import { QueryClient, QueryClientProvider, useQuery } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import type { ReactNode } from "react";
import { expect, it } from "vitest";

import { getPracticeGuidanceOptions } from "@/api/@tanstack/react-query.gen";
import type { PracticeGuidance } from "@/api/types.gen";
import { mockPractice } from "@/components/admin/practices/fixtures";
import { server } from "@/mocks/server";
import { deferred } from "@/test/async";

import { usePracticeEditor } from "./use-practice-editor";

const { slug } = mockPractice;
const options = getPracticeGuidanceOptions({ path: { workspaceSlug: "acme", practiceSlug: slug } });
const earlier: PracticeGuidance = {
	practiceSlug: slug,
	guide: { markdown: "Earlier guide", figures: {} },
};
const updated: PracticeGuidance = {
	practiceSlug: slug,
	guide: { markdown: "Updated guide", figures: {} },
};

function reader(client: QueryClient) {
	return renderHook(() => ({ editor: usePracticeEditor("acme"), guidance: useQuery(options) }), {
		wrapper: ({ children }: { children: ReactNode }) => (
			<QueryClientProvider client={client}>{children}</QueryClientProvider>
		),
	});
}

it("refreshes cached guidance after an edit and a clear without changing another workspace", async () => {
	const client = new QueryClient({
		defaultOptions: { queries: { retry: false, staleTime: 30_000 } },
	});
	client.setQueryData(options.queryKey, earlier);
	const otherOptions = getPracticeGuidanceOptions({
		path: { workspaceSlug: "other", practiceSlug: slug },
	});
	client.setQueryData(otherOptions.queryKey, earlier);
	server.use(
		http.patch("*/workspaces/acme/practices/:practiceSlug", () => HttpResponse.json(mockPractice)),
		http.get("*/workspaces/acme/practices/:practiceSlug/guidance", () =>
			HttpResponse.json(updated),
		),
	);
	const { result } = reader(client);
	expect(result.current.guidance.data).toStrictEqual(earlier);
	await act(async () => {
		await result.current.editor.update(slug, { name: mockPractice.name }, null);
	});
	await waitFor(() => expect(result.current.guidance.data).toStrictEqual(updated));
	expect(client.getQueryData(otherOptions.queryKey)).toStrictEqual(earlier);
	server.use(
		http.get("*/workspaces/acme/practices/:practiceSlug/guidance", () =>
			HttpResponse.json({ practiceSlug: slug }),
		),
	);
	await act(async () => {
		await result.current.editor.update(slug, { clear: ["GUIDE"] }, null);
	});
	await waitFor(() => expect(result.current.guidance.data).toStrictEqual({ practiceSlug: slug }));
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
		http.patch("*/workspaces/acme/practices/:practiceSlug", () => HttpResponse.json(mockPractice)),
		source.handler,
	);
	const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
	const { result } = reader(client);
	await waitFor(() => expect(source.reads()).toBe(1));
	await act(async () => {
		await result.current.editor.update(slug, { name: mockPractice.name }, null);
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
	client.setQueryData(options.queryKey, { practiceSlug: slug });
	server.use(
		http.post("*/workspaces/acme/practices", () => HttpResponse.json(mockPractice)),
		http.get("*/workspaces/acme/practices/:practiceSlug/guidance", () =>
			HttpResponse.json(updated),
		),
	);
	const { result } = reader(client);
	await act(async () => {
		await result.current.editor.create({ ...mockPractice }, null);
	});
	await waitFor(() => expect(result.current.guidance.data).toStrictEqual(updated));
});

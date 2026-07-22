import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import {
	adminGetLlmSettingsQueryKey,
	adminListLlmConnectionsQueryKey,
	adminListLlmModelsQueryKey,
	adminListWorkspacesQueryKey,
} from "@/api/@tanstack/react-query.gen";
import { Route } from "./admin.llm";

const AdminLlmPage = Route.options.component;

describe("AdminLlmPage", () => {
	it("renders before a connection or provider probe has been selected", async () => {
		const queryClient = new QueryClient();
		queryClient.setQueryData(adminListLlmConnectionsQueryKey(), []);
		queryClient.setQueryData(adminListLlmModelsQueryKey(), []);
		queryClient.setQueryData(adminListWorkspacesQueryKey(), []);
		queryClient.setQueryData(adminGetLlmSettingsQueryKey(), {
			allowWorkspaceConnections: true,
			allowedEgressHosts: "",
			defaultUnpricedPolicy: "WARN",
		});

		if (!AdminLlmPage) throw new Error("Admin LLM route must have a component");
		await (AdminLlmPage as typeof AdminLlmPage & { preload: () => Promise<unknown> }).preload();
		render(
			<QueryClientProvider client={queryClient}>
				<AdminLlmPage />
			</QueryClientProvider>,
		);

		expect(screen.getByRole("heading", { name: "AI models" })).toBeTruthy();
	});
});

import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { LlmConnection } from "@/api/types.gen";
import { AdminLlmConnectionFormDialog } from "./AdminLlmConnectionFormDialog";

const connection: LlmConnection = {
	id: 1,
	slug: "custom",
	displayName: "Custom endpoint",
	apiProtocol: "openai-completions",
	authMode: "BEARER",
	baseUrl: "https://llm.example.test/v1",
	enabled: true,
	hasApiKey: true,
	apiKeyLast4: "ab12",
	createdAt: new Date("2026-07-01T00:00:00Z"),
};

function renderDialog(overrides: Partial<Parameters<typeof AdminLlmConnectionFormDialog>[0]> = {}) {
	const props = {
		open: true,
		onOpenChange: vi.fn(),
		editing: null,
		isSubmitting: false,
		onCreate: vi.fn(),
		onUpdate: vi.fn(),
		onProbe: vi.fn(),
		onProbeSaved: vi.fn(),
		isProbing: false,
		...overrides,
	};
	render(<AdminLlmConnectionFormDialog {...props} />);
	return props;
}

describe("AdminLlmConnectionFormDialog", () => {
	it("offers the three OpenAI-compatible create-time presets", () => {
		renderDialog();
		const active = screen.getByRole("switch", { name: "Active" }) as HTMLButtonElement;
		expect(active.getAttribute("aria-checked")).toBe("false");
		expect(active.hasAttribute("data-disabled")).toBe(true);
		expect(screen.queryByLabelText("Slug")).toBeNull();
		fireEvent.click(screen.getByRole("combobox", { name: "Endpoint preset" }));
		expect(screen.queryByRole("option", { name: "Anthropic" })).toBeNull();
		expect(screen.getByRole("option", { name: "OpenAI" })).toBeTruthy();
		expect(screen.getByRole("option", { name: "Other OpenAI-compatible endpoint" })).toBeTruthy();
		expect(screen.getByRole("option", { name: "Azure OpenAI v1" })).toBeTruthy();
	});

	it("keeps routing immutable and tests the saved connection with its stored credential", () => {
		const onUpdate = vi.fn();
		const onProbe = vi.fn();
		const onProbeSaved = vi.fn();
		renderDialog({ editing: connection, onUpdate, onProbe, onProbeSaved });
		expect((screen.getByLabelText("Base URL") as HTMLInputElement).disabled).toBe(true);
		expect(screen.queryByRole("combobox", { name: "Endpoint preset" })).toBeNull();
		fireEvent.click(screen.getByRole("button", { name: "Test saved connection" }));
		expect(onProbeSaved).toHaveBeenCalledWith(connection.id, expect.any(Object));
		expect(onProbe).not.toHaveBeenCalled();
		fireEvent.click(screen.getByRole("button", { name: "Save changes" }));
		const update = onUpdate.mock.calls[0]?.[1];
		expect(update).toEqual({ displayName: "Custom endpoint", enabled: true });
	});
});

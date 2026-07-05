// Behavioral tests for the Outline integration admin card. The component is presentational:
// connect/disconnect are mocked callbacks, so we assert on the delegated intent and the two
// safeguards — connect is gated on a valid https URL + token, and disconnect is behind a confirm
// AlertDialog. jest-dom matchers and user-event are NOT set up in this repo's vitest, so assertions
// use plain DOM (`.disabled`, `queryByRole`, `.value`) and `fireEvent`.

import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { OutlineIntegrationCard } from "./OutlineIntegrationCard";

function connectButton(): HTMLButtonElement {
	return screen.getByRole("button", { name: /connect outline/i }) as HTMLButtonElement;
}

describe("OutlineIntegrationCard", () => {
	it("keeps connect disabled until a valid URL and a token are entered", () => {
		render(<OutlineIntegrationCard connected={false} onConnect={vi.fn()} onDisconnect={vi.fn()} />);

		// Default https URL is prefilled, but the token is empty.
		expect(connectButton().disabled).toBe(true);

		fireEvent.change(screen.getByLabelText(/api token/i), { target: { value: "ol_api_secret" } });
		expect(connectButton().disabled).toBe(false);
	});

	it("delegates the entered credentials and allow-list to onConnect", () => {
		const onConnect = vi.fn();
		render(
			<OutlineIntegrationCard connected={false} onConnect={onConnect} onDisconnect={vi.fn()} />,
		);

		fireEvent.change(screen.getByLabelText(/server url/i), {
			target: { value: "https://wiki.example.com/" },
		});
		fireEvent.change(screen.getByLabelText(/api token/i), {
			target: { value: "  ol_api_secret  " },
		});
		fireEvent.change(screen.getByLabelText(/collection allow-list/i), {
			target: { value: "Engineering\nArchitecture Decisions" },
		});
		fireEvent.click(connectButton());

		expect(onConnect).toHaveBeenCalledWith({
			serverUrl: "https://wiki.example.com/",
			token: "ol_api_secret",
			collectionAllowList: "Engineering\nArchitecture Decisions",
		});
	});

	it("blocks connect and shows an error for a non-https server URL", () => {
		render(<OutlineIntegrationCard connected={false} onConnect={vi.fn()} onDisconnect={vi.fn()} />);

		fireEvent.change(screen.getByLabelText(/server url/i), {
			target: { value: "http://internal" },
		});
		fireEvent.change(screen.getByLabelText(/api token/i), { target: { value: "ol_api_secret" } });

		expect(screen.getByText(/enter an https:\/\/ url/i)).toBeTruthy();
		expect(connectButton().disabled).toBe(true);
	});

	it("shows the connected instance and confirms before disconnecting", () => {
		const onDisconnect = vi.fn();
		render(
			<OutlineIntegrationCard
				connected
				connectionLabel="Acme Wiki"
				onConnect={vi.fn()}
				onDisconnect={onDisconnect}
			/>,
		);

		expect(screen.getByText(/acme wiki/i)).toBeTruthy();

		fireEvent.click(screen.getByRole("button", { name: /disconnect outline/i }));
		const dialog = screen.getByRole("alertdialog", { name: /disconnect outline\?/i });
		expect(onDisconnect).not.toHaveBeenCalled();

		fireEvent.click(within(dialog).getByRole("button", { name: /^disconnect$/i }));
		expect(onDisconnect).toHaveBeenCalledTimes(1);
	});
});

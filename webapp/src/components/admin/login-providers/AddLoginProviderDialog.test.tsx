import { fireEvent, render } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { Dialog } from "@/components/ui/dialog";
import { AddLoginProviderDialog } from "./AddLoginProviderDialog";

// The dialog is a presentation component (no router/query), so it renders cheaply inside the
// shadcn <Dialog> wrapper it expects. We assert the client-side validation gate: submitting
// with empty/invalid required fields must NOT fire onSubmit, and a valid HTTPS issuer must.
function renderDialog(onSubmit = vi.fn()) {
	const result = render(
		<Dialog open>
			<AddLoginProviderDialog isSubmitting={false} onSubmit={onSubmit} />
		</Dialog>,
	);
	return { ...result, onSubmit };
}

function inputBySuffix(root: ParentNode, suffix: string): HTMLInputElement {
	const input = Array.from(root.querySelectorAll<HTMLInputElement>("input")).find((el) =>
		el.id.endsWith(suffix),
	);
	if (!input) throw new Error(`No input ending in ${suffix}`);
	return input;
}

function setField(root: ParentNode, suffix: string, value: string) {
	fireEvent.change(inputBySuffix(root, suffix), { target: { value } });
}

function submit(root: ParentNode) {
	const form = root.querySelector("form");
	if (!form) throw new Error("No form");
	fireEvent.submit(form);
}

describe("AddLoginProviderDialog", () => {
	it("blocks submission and shows field errors when required fields are empty", () => {
		const { baseElement, onSubmit } = renderDialog();

		submit(baseElement);

		expect(onSubmit).not.toHaveBeenCalled();
		expect(baseElement.querySelector('[aria-invalid="true"]')).not.toBeNull();
	});

	it("rejects a non-HTTPS issuer URL", () => {
		const { baseElement, onSubmit } = renderDialog();

		setField(baseElement, "display-name", "Acme");
		setField(baseElement, "issuer-url", "http://git.example.com");
		setField(baseElement, "client-id", "cid");
		setField(baseElement, "client-secret", "secret");
		submit(baseElement);

		expect(onSubmit).not.toHaveBeenCalled();
	});

	it("submits a normalized payload when all fields are valid", () => {
		const { baseElement, onSubmit } = renderDialog();

		setField(baseElement, "display-name", "  Acme GHE  ");
		setField(baseElement, "issuer-url", " https://git.example.com ");
		setField(baseElement, "client-id", " cid ");
		setField(baseElement, "client-secret", "shh");
		submit(baseElement);

		expect(onSubmit).toHaveBeenCalledTimes(1);
		expect(onSubmit).toHaveBeenCalledWith({
			kind: "OIDC_LOGIN_GITHUB",
			userInput: {
				issuer_url: "https://git.example.com",
				client_id: "cid",
				client_secret: "shh",
				display_name: "Acme GHE",
			},
		});
	});
});

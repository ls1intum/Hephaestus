import { type FormEvent, useState } from "react";
import type { AdminWorkspaceLlmUsage } from "@/api/types.gen";
import { Button } from "@/components/ui/button";
import {
	Dialog,
	DialogClose,
	DialogContent,
	DialogDescription,
	DialogFooter,
	DialogHeader,
	DialogTitle,
} from "@/components/ui/dialog";
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Spinner } from "@/components/ui/spinner";

export interface SetBudgetDialogProps {
	/** The workspace whose cap is being edited; `null` keeps the dialog closed. */
	workspace: AdminWorkspaceLlmUsage | null;
	isPending: boolean;
	onOpenChange: (open: boolean) => void;
	/** `null` removes the cap; a number (USD, >= 0, 2 decimals) sets it. */
	onSubmit: (monthlyLlmBudgetUsd: number | null) => void;
}

/**
 * Small instance-admin dialog to set or remove a workspace's monthly LLM budget cap (USD).
 * A cap of 0 pauses AI work immediately; removing the cap leaves the workspace uncapped.
 */
export function SetBudgetDialog({
	workspace,
	isPending,
	onOpenChange,
	onSubmit,
}: SetBudgetDialogProps) {
	return (
		<Dialog open={workspace !== null} onOpenChange={onOpenChange}>
			{workspace !== null && (
				// Keyed so the input state resets whenever a different workspace is edited.
				<SetBudgetDialogContent
					key={workspace.workspaceId}
					workspace={workspace}
					isPending={isPending}
					onSubmit={onSubmit}
				/>
			)}
		</Dialog>
	);
}

interface SetBudgetDialogContentProps {
	workspace: AdminWorkspaceLlmUsage;
	isPending: boolean;
	onSubmit: (monthlyLlmBudgetUsd: number | null) => void;
}

function SetBudgetDialogContent({ workspace, isPending, onSubmit }: SetBudgetDialogContentProps) {
	const [value, setValue] = useState(
		workspace.monthlyBudgetUsd != null ? String(workspace.monthlyBudgetUsd) : "",
	);
	// Withheld until the first submit so the field isn't red before anything was attempted.
	const [showError, setShowError] = useState(false);

	const parsed = Number.parseFloat(value);
	const isEmpty = value.trim() === "";
	// At most two decimals: a cap is an amount of money, and the server column is NUMERIC(10,2).
	const hasCentPrecision = /^\d*(\.\d{0,2})?$/.test(value.trim());
	const isValid = !isEmpty && Number.isFinite(parsed) && parsed >= 0 && hasCentPrecision;
	const errorMessage = isEmpty
		? "Enter a budget amount, or remove the cap entirely."
		: !Number.isFinite(parsed) || parsed < 0
			? "Enter an amount of $0 or more."
			: "Use at most two decimal places.";
	const isInvalid = showError && !isValid;

	const handleSubmit = (event: FormEvent) => {
		event.preventDefault();
		if (!isValid) {
			// The submit button stays enabled precisely so this reveals *why* the value is rejected.
			setShowError(true);
			return;
		}
		onSubmit(parsed);
	};

	return (
		<DialogContent>
			{/* noValidate: this form validates itself so every rejection surfaces through `FieldError`.
			    Left to the browser, `min`/`step` would silently block submit with a native bubble and the
			    field's own explanation would never render. */}
			<form onSubmit={handleSubmit} className="contents" noValidate>
				<DialogHeader>
					<DialogTitle>Set monthly AI budget</DialogTitle>
					<DialogDescription>
						Cap for <strong>{workspace.displayName}</strong> ({workspace.workspaceSlug}). When the
						month's spend reaches the cap, practice detection and mentor turns pause until next
						month. A cap of $0 pauses immediately.
					</DialogDescription>
				</DialogHeader>
				<FieldGroup>
					<Field data-invalid={isInvalid}>
						<FieldLabel htmlFor="set-budget-usd">Monthly budget (USD)</FieldLabel>
						<Input
							id="set-budget-usd"
							type="number"
							inputMode="decimal"
							min={0}
							step={0.01}
							placeholder="e.g. 25.00"
							value={value}
							aria-invalid={isInvalid}
							onChange={(event) => {
								setValue(event.target.value);
								setShowError(false);
							}}
							disabled={isPending}
							autoFocus
						/>
						<FieldDescription>
							Spend above this amount pauses AI work until the next UTC month.
						</FieldDescription>
						{isInvalid && <FieldError>{errorMessage}</FieldError>}
					</Field>
				</FieldGroup>
				<DialogFooter>
					{workspace.monthlyBudgetUsd != null && (
						<Button
							type="button"
							variant="destructive-outline"
							className="sm:mr-auto"
							disabled={isPending}
							onClick={() => onSubmit(null)}
						>
							Remove cap
						</Button>
					)}
					<DialogClose render={<Button type="button" variant="outline" disabled={isPending} />}>
						Cancel
					</DialogClose>
					<Button type="submit" disabled={isPending}>
						{isPending ? <Spinner className="size-4" /> : null}
						Save cap
					</Button>
				</DialogFooter>
			</form>
		</DialogContent>
	);
}

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
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
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

	const parsed = Number.parseFloat(value);
	const isValid = value.trim() !== "" && Number.isFinite(parsed) && parsed >= 0;

	const handleSubmit = (event: FormEvent) => {
		event.preventDefault();
		if (!isValid) {
			return;
		}
		// Sub-cent values are already rejected by the input's native `step={0.01}` validation.
		onSubmit(parsed);
	};

	return (
		<DialogContent>
			<form onSubmit={handleSubmit} className="contents">
				<DialogHeader>
					<DialogTitle>Set monthly AI budget</DialogTitle>
					<DialogDescription>
						Cap for <strong>{workspace.displayName}</strong> ({workspace.workspaceSlug}). When the
						month's spend reaches the cap, practice detection and mentor turns pause until next
						month. A cap of $0 pauses immediately.
					</DialogDescription>
				</DialogHeader>
				<div className="grid gap-2">
					<Label htmlFor="set-budget-usd">Monthly budget (USD)</Label>
					<Input
						id="set-budget-usd"
						type="number"
						inputMode="decimal"
						min={0}
						step={0.01}
						placeholder="e.g. 25.00"
						value={value}
						onChange={(event) => setValue(event.target.value)}
						disabled={isPending}
						autoFocus
					/>
				</div>
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
					<Button type="submit" disabled={isPending || !isValid}>
						{isPending ? <Spinner className="size-4" /> : null}
						Save cap
					</Button>
				</DialogFooter>
			</form>
		</DialogContent>
	);
}

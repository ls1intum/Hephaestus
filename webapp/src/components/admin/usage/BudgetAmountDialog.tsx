import { type FormEvent, type ReactNode, useId, useState } from "react";
import { Button } from "@/components/ui/button";
import {
	Dialog,
	DialogBody,
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
import { type Fx, FxApprox, fxCapHint } from "./fx";

export interface BudgetAmountDialogProps {
	open: boolean;
	title: string;
	description: ReactNode;
	fieldLabel: string;
	/** The cap in force today, in USD; `null`/`undefined` means uncapped (no "Remove cap"). */
	currentValueUsd?: number | null;
	isPending: boolean;
	/** Rendered as this field's error, not a toast: the value that was rejected is still on screen. */
	serverError?: string | null;
	submitLabel?: string;
	removeLabel?: string;
	fx?: Fx;
	/**
	 * Whether the surface behind this dialog is showing the current month. A cap is not month-scoped,
	 * so only the current month's rate can be quoted "at today's rate" under the amount — see
	 * {@link fxCapHint}. Defaults to `false`: a caller that has not thought about it gets no estimate
	 * rather than a possibly frozen one.
	 */
	isCurrentMonth?: boolean;
	onOpenChange: (open: boolean) => void;
	/** `null` removes the cap; a number (USD, >= 0, at most 2 decimals) sets it. */
	onSubmit: (valueUsd: number | null) => void;
}

/**
 * The money-cap editor shared by `SetBudgetDialog` and `SetOwnProviderBudgetDialog`. Both edit the
 * same value under the same rules — USD, >= 0, cent precision, `null` removes, `0` pauses
 * immediately — so the wrappers supply only the copy.
 */
export function BudgetAmountDialog({
	open,
	isPending,
	onOpenChange,
	...contentProps
}: BudgetAmountDialogProps) {
	return (
		<Dialog open={open} onOpenChange={onOpenChange}>
			{/* Keyed on the value the form seeds from, the way the five sibling LLM dialogs key on
			    `editing` — so the input can never carry the previous subject's amount, and a caller
			    cannot forget to say so. */}
			{open && (
				<BudgetAmountDialogContent
					key={contentProps.currentValueUsd ?? "none"}
					isPending={isPending}
					{...contentProps}
				/>
			)}
		</Dialog>
	);
}

type BudgetAmountDialogContentProps = Omit<BudgetAmountDialogProps, "open" | "onOpenChange">;

function BudgetAmountDialogContent({
	title,
	description,
	fieldLabel,
	currentValueUsd,
	isPending,
	serverError,
	submitLabel = "Save cap",
	removeLabel = "Remove cap",
	fx,
	isCurrentMonth = false,
	onSubmit,
}: BudgetAmountDialogContentProps) {
	const fieldId = useId();
	const fxHintId = useId();
	const errorId = useId();
	const [value, setValue] = useState(currentValueUsd != null ? String(currentValueUsd) : "");
	// Withheld until the first submit so the field isn't red before anything was attempted.
	const [showError, setShowError] = useState(false);
	// The server error stays until the amount is edited, so it reads as "this value was rejected"
	// rather than as ambient noise.
	const [dismissedServerError, setDismissedServerError] = useState<string | null>(null);

	const parsed = Number.parseFloat(value);
	const isEmpty = value.trim() === "";
	// At most two decimals: a cap is an amount of money, and the server column is NUMERIC(10,2).
	const hasCentPrecision = /^\d*(\.\d{0,2})?$/.test(value.trim());
	const isValid = !isEmpty && Number.isFinite(parsed) && parsed >= 0 && hasCentPrecision;
	const canRemove = currentValueUsd != null;
	const localError = isEmpty
		? // Names the button actually on screen — and only when it is on screen: the remove button is
			// not rendered for a subject that has no cap yet, and an error pointing at a control that
			// isn't there is worse than one that doesn't.
			canRemove
			? `Enter an amount, or use ${removeLabel}.`
			: "Enter an amount."
		: !Number.isFinite(parsed) || parsed < 0
			? "Enter an amount of $0 or more."
			: "Use at most two decimal places.";
	const liveServerError =
		serverError != null && serverError !== dismissedServerError ? serverError : null;
	const errorMessage = showError && !isValid ? localError : liveServerError;
	const isInvalid = errorMessage != null;
	const fxHint = fxCapHint(isValid ? parsed : null, fx, isCurrentMonth);

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
			{/* noValidate: left to the browser, `min`/`step` block submit with a native bubble and the
			    field's own `FieldError` explanation never renders. */}
			<form onSubmit={handleSubmit} className="contents" noValidate>
				<DialogHeader>
					<DialogTitle>{title}</DialogTitle>
					<DialogDescription>{description}</DialogDescription>
				</DialogHeader>
				{/* Short in portrait, but header plus three stacked footer buttons already exceed a phone in
				    landscape (~320 px tall), where scrolling the field keeps "Save cap" on screen. */}
				<DialogBody className="py-1">
					<FieldGroup>
						<Field data-invalid={isInvalid}>
							<FieldLabel htmlFor={fieldId}>{fieldLabel}</FieldLabel>
							<Input
								id={fieldId}
								type="number"
								inputMode="decimal"
								min={0}
								step={0.01}
								placeholder="e.g. 25.00"
								value={value}
								aria-invalid={isInvalid}
								// Described-by, not a live region: the latter would interrupt typing once per digit.
								// Both the estimate and the rejection reason, so tabbing back to a field marked
								// invalid announces *why* rather than just "invalid" (WCAG SC 3.3.1).
								aria-describedby={
									[fxHint != null ? fxHintId : null, isInvalid ? errorId : null]
										.filter(Boolean)
										.join(" ") || undefined
								}
								onChange={(event) => {
									setValue(event.target.value);
									setShowError(false);
									setDismissedServerError(serverError ?? null);
								}}
								disabled={isPending}
								autoFocus
							/>
							{fxHint != null && (
								<FieldDescription id={fxHintId}>
									<FxApprox conversion={fxHint.conversion} />
									{fxHint.tail}
								</FieldDescription>
							)}
							{errorMessage != null && <FieldError id={errorId}>{errorMessage}</FieldError>}
						</Field>
					</FieldGroup>
				</DialogBody>
				<DialogFooter>
					{canRemove && (
						<Button
							type="button"
							variant="destructive-outline"
							className="sm:mr-auto"
							disabled={isPending}
							onClick={() => onSubmit(null)}
						>
							{removeLabel}
						</Button>
					)}
					<DialogClose render={<Button type="button" variant="outline" disabled={isPending} />}>
						Cancel
					</DialogClose>
					<Button type="submit" disabled={isPending}>
						{isPending ? <Spinner className="size-4" /> : null}
						{submitLabel}
					</Button>
				</DialogFooter>
			</form>
		</DialogContent>
	);
}

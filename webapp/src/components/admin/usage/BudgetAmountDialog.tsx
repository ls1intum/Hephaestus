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
	/**
	 * Changing this remounts the form, so the input never carries a stale amount from the
	 * previously edited subject (e.g. another workspace).
	 */
	resetKey?: string | number;
	title: string;
	description: ReactNode;
	fieldLabel: string;
	fieldDescription: ReactNode;
	/** The cap in force today, in USD; `null`/`undefined` means uncapped (no "Remove cap"). */
	currentValueUsd?: number | null;
	isPending: boolean;
	/**
	 * A server-side rejection (RFC 9457 `detail`). Rendered as this field's error rather than a
	 * toast: the value that was rejected is on screen, and a toast would evaporate before it can
	 * be compared against it.
	 */
	serverError?: string | null;
	submitLabel?: string;
	removeLabel?: string;
	/**
	 * Display-only conversion for the amount being typed. Absent — the default for any instance
	 * without a display currency — means no hint at all, exactly as before.
	 */
	fx?: Fx;
	onOpenChange: (open: boolean) => void;
	/** `null` removes the cap; a number (USD, >= 0, at most 2 decimals) sets it. */
	onSubmit: (valueUsd: number | null) => void;
}

/**
 * The money-cap editor shared by both budget dialogs — instance-admin (`SetBudgetDialog`, the
 * shared-model budget) and workspace-admin (`SetByoBudgetDialog`, the workspace's provider cap).
 * Both edit the same kind of value under the same rules (USD, >= 0, cent
 * precision, `null` removes, `0` pauses immediately), so the validation, the currency input, the
 * "Remove cap" affordance, the pending state, and server-error rendering live here once; the
 * wrappers supply only the copy.
 */
export function BudgetAmountDialog({
	open,
	resetKey,
	isPending,
	onOpenChange,
	...contentProps
}: BudgetAmountDialogProps) {
	return (
		<Dialog open={open} onOpenChange={onOpenChange}>
			{open && (
				<BudgetAmountDialogContent
					key={resetKey ?? "budget-amount"}
					isPending={isPending}
					{...contentProps}
				/>
			)}
		</Dialog>
	);
}

type BudgetAmountDialogContentProps = Omit<
	BudgetAmountDialogProps,
	"open" | "resetKey" | "onOpenChange"
>;

function BudgetAmountDialogContent({
	title,
	description,
	fieldLabel,
	fieldDescription,
	currentValueUsd,
	isPending,
	serverError,
	submitLabel = "Save cap",
	removeLabel = "Remove cap",
	fx,
	onSubmit,
}: BudgetAmountDialogContentProps) {
	const fieldId = useId();
	const fxHintId = useId();
	const [value, setValue] = useState(currentValueUsd != null ? String(currentValueUsd) : "");
	// Withheld until the first submit so the field isn't red before anything was attempted.
	const [showError, setShowError] = useState(false);
	// The server error stays on screen until the amount is edited — clearing it on the next
	// keystroke is what makes it read as "this value was rejected" rather than ambient noise.
	const [dismissedServerError, setDismissedServerError] = useState<string | null>(null);

	const parsed = Number.parseFloat(value);
	const isEmpty = value.trim() === "";
	// At most two decimals: a cap is an amount of money, and the server column is NUMERIC(10,2).
	const hasCentPrecision = /^\d*(\.\d{0,2})?$/.test(value.trim());
	const isValid = !isEmpty && Number.isFinite(parsed) && parsed >= 0 && hasCentPrecision;
	const localError = isEmpty
		? "Enter an amount, or use Remove cap."
		: !Number.isFinite(parsed) || parsed < 0
			? "Enter an amount of $0 or more."
			: "Use at most two decimal places.";
	const liveServerError =
		serverError != null && serverError !== dismissedServerError ? serverError : null;
	const errorMessage = showError && !isValid ? localError : liveServerError;
	const isInvalid = errorMessage != null;
	// What the amount on screen is worth in the display currency, recomputed as they type. Absent
	// while the field is empty, unparseable or $0 — there is nothing useful to estimate there.
	const fxHint = fxCapHint(isValid ? parsed : null, fx);

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
					<DialogTitle>{title}</DialogTitle>
					<DialogDescription>{description}</DialogDescription>
				</DialogHeader>
				{/* Short in portrait, but the three stacked footer buttons plus the header already exceed a
				    phone in landscape (~320 px tall). Scrolling the field rather than the whole popup keeps
				    "Save cap" on screen there. */}
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
								// Read on focus rather than announced on every keystroke: a live region here
								// would interrupt typing with a new estimate per digit.
								aria-describedby={fxHint != null ? fxHintId : undefined}
								onChange={(event) => {
									setValue(event.target.value);
									setShowError(false);
									setDismissedServerError(serverError ?? null);
								}}
								disabled={isPending}
								autoFocus
							/>
							<FieldDescription>{fieldDescription}</FieldDescription>
							{fxHint != null && (
								<FieldDescription id={fxHintId}>
									<FxApprox conversion={fxHint.conversion} />
									{fxHint.tail}
								</FieldDescription>
							)}
							{errorMessage != null && <FieldError>{errorMessage}</FieldError>}
						</Field>
					</FieldGroup>
				</DialogBody>
				<DialogFooter>
					{currentValueUsd != null && (
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

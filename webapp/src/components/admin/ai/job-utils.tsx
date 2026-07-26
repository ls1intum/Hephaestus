import type { AgentJob } from "@/api/types.gen";

export type JobStatus = AgentJob["status"];
export type DeliveryStatus = NonNullable<AgentJob["deliveryStatus"]>;

export const STATUS_LABELS: Record<JobStatus, string> = {
	QUEUED: "Queued",
	RUNNING: "Running",
	COMPLETED: "Completed",
	FAILED: "Failed",
	TIMED_OUT: "Timed out",
	CANCELLED: "Cancelled",
};

export const DELIVERY_STATUS_LABELS: Record<DeliveryStatus, string> = {
	PENDING: "Pending",
	DELIVERED: "Delivered",
	FAILED: "Failed",
};

export function statusBadgeVariant(
	status: JobStatus,
): "default" | "secondary" | "destructive" | "outline" {
	switch (status) {
		case "COMPLETED":
			return "default";
		case "RUNNING":
		case "QUEUED":
			return "secondary";
		case "FAILED":
		case "TIMED_OUT":
			return "destructive";
		case "CANCELLED":
			return "outline";
	}
}

export function deliveryBadgeVariant(
	status: DeliveryStatus,
): "default" | "secondary" | "destructive" {
	switch (status) {
		case "DELIVERED":
			return "default";
		case "PENDING":
			return "secondary";
		case "FAILED":
			return "destructive";
	}
}

export function isCancellable(status: JobStatus): boolean {
	return status === "QUEUED" || status === "RUNNING";
}

export function isDeliveryRetryable(job: Pick<AgentJob, "status" | "deliveryStatus">): boolean {
	return job.status === "COMPLETED" && job.deliveryStatus === "FAILED";
}

export function formatTokens(value: number | undefined): string {
	if (value == null) return "—";
	return value.toLocaleString();
}

/**
 * Money rendering for every AI surface.
 *
 * The amounts arriving here are exact decimals on the server (`NUMERIC`, `BigDecimal`) and JSON
 * numbers on the wire, declared `format: decimal` — see the "Money and exact decimals" section of
 * the API description. JavaScript has no decimal type, so they land as binary64; that is lossless
 * for everything this API produces, with the guarantee stated and tested server-side
 * (`MoneyWirePrecisionTest`).
 *
 * The rule that margin buys is narrow: **format these, do not do sums with them.** Totals, remaining
 * budget and cap verdicts are computed exactly on the server and shipped as their own fields.
 * Re-deriving one by adding up rows here trades an exact number for an approximate one and can only
 * disagree with the figure printed above it.
 */
const USD = new Intl.NumberFormat("en-US", {
	style: "currency",
	currency: "USD",
	minimumFractionDigits: 2,
	maximumFractionDigits: 2,
});

const USD_WHOLE = new Intl.NumberFormat("en-US", {
	style: "currency",
	currency: "USD",
	minimumFractionDigits: 0,
	maximumFractionDigits: 0,
});

/**
 * An amount of money that was spent.
 *
 * <p>Three cases, because reading spend at a glance is the whole job of these tables:
 * nothing spent reads as `$0` — not `$0.000`, which looks like a broken decimal; an amount too
 * small to show in cents reads as `<$0.01`, which is honest about being nonzero where rounding to
 * `$0.00` would claim the opposite; everything else is plain cents.
 */
export function formatCostUsd(value: number | undefined): string {
	if (value == null) return "—";
	if (value === 0) return "$0";
	if (value > 0 && value < 0.005) return "<$0.01";
	return USD.format(value);
}

/**
 * A cap someone typed, rendered the way they typed it: `$50`, not `$50.00`. Cents appear only when
 * the cap actually has them, so a round number stays scannable next to the spend it bounds.
 */
export function formatCapUsd(value: number | undefined): string {
	if (value == null) return "—";
	// Cents are all-or-nothing: "$49.50", never "$49.5". Intl's maximumFractionDigits would happily
	// emit a single decimal, which reads as a typo in a column of money.
	return Number.isInteger(value) ? USD_WHOLE.format(value) : USD.format(value);
}

const USD_RATE = new Intl.NumberFormat("en-US", {
	style: "currency",
	currency: "USD",
	minimumFractionDigits: 2,
	maximumFractionDigits: 4,
});

/**
 * A published price, not an amount spent. Rates carry up to four decimals ($0.075 / 1M tokens is a
 * real price) and are never floored to `<$0.01` — a rate the admin verifies against their provider's
 * price list has to render as the number they will read there.
 *
 * Only for prices and per-unit rates. Anything that was actually spent uses {@link formatCostUsd},
 * whose `$0` / `<$0.01` bounds are the #1368 glossary's wording for spend.
 */
export function formatRateUsd(value: number | undefined): string {
	return value == null ? "—" : USD_RATE.format(value);
}

/**
 * A money figure in a right-aligned column, padded so its decimal point lands where every other
 * row's does.
 *
 * `tabular-nums` equalises glyph *width*; it does nothing about a missing `.00`, so `$0`, `<$0.01`
 * and `$4.50` right-align with their decimal points three different distances from the edge. The
 * copy for `$0` and `<$0.01` is fixed by the glossary, so the fix is layout: reserve the width of
 * the cents that string doesn't have. `visibility: hidden` keeps the space (unlike `display: none`)
 * and `aria-hidden` keeps it out of the accessible name.
 *
 * Headlines are not columns — don't wrap them.
 */
export function MoneyCell({ children }: { children: string }) {
	return (
		<>
			{children}
			{!children.includes(".") && (
				<span className="invisible" aria-hidden>
					.00
				</span>
			)}
		</>
	);
}

/**
 * The model a job ran on, read from its frozen submit-time snapshot. Populated from submission
 * onward, unlike the runner-reported `llmModel`, which only exists once the job has actually run.
 */
export function modelLabel(job: Pick<AgentJob, "model">): string {
	return job.model ?? "—";
}

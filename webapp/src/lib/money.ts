/**
 * USD rendering for every surface that shows money.
 *
 * This lives in `lib/` because `lib/` is where the rules the whole app shares live, and money is one
 * of them: `lib/llm-pricing.ts` composes a price label out of these, and a `lib/` module reaching up
 * into `components/` for a formatter inverts the dependency the directory split exists to state.
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

const USD_RATE = new Intl.NumberFormat("en-US", {
	style: "currency",
	currency: "USD",
	minimumFractionDigits: 2,
	maximumFractionDigits: 4,
});

/**
 * An amount of money that was spent.
 *
 * Three cases, because reading spend at a glance is the whole job of these tables: nothing spent
 * reads as `$0` — not `$0.00`, which buries the difference between "none" and "almost none"; an
 * amount too small to show in cents reads as `<$0.01`, which is honest about being nonzero where
 * rounding to `$0.00` would claim the opposite; everything else is plain cents.
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

/**
 * A published price, not an amount spent. Rates carry up to four decimals ($0.075 / 1M tokens is a
 * real price) and are never floored to `<$0.01` — a rate the admin verifies against their provider's
 * price list has to render as the number they will read there.
 *
 * Only for prices and per-unit rates. Anything that was actually spent uses {@link formatCostUsd},
 * whose `$0` / `<$0.01` bounds are the wording for spend (`docs/contributor/llm-cost-vocabulary.md`, rule 5).
 */
export function formatRateUsd(value: number | undefined): string {
	return value == null ? "—" : USD_RATE.format(value);
}

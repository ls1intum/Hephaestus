import type { FxRateInfo } from "@/api/types.gen";
import { cn } from "@/lib/utils";

/**
 * Display-only currency conversion for the AI-usage surfaces.
 *
 * USD is the real number everywhere: spend is metered, capped and enforced in USD, and the server
 * only ever sends USD amounts. When the instance has opted into a display currency the response
 * carries an {@link FxRateInfo}, and every USD figure may show a *secondary* estimate beside it —
 * `$4.50 (≈ €3.96)`. The ordering is the message: the dollar leads, the estimate follows, and the
 * `≈` marks it as approximate at every occurrence.
 *
 * **Where the estimate goes depends on how the figure is read:**
 * - *Headline or prose* — inline parenthetical ({@link FxAmount} and friends): `$4.50 (≈ €3.96)`.
 * - *Any table cell* — a second muted line under the figure ({@link FxLine}, {@link FxSpendLine}).
 *   A column is scanned down its right edge, and a variable-width suffix shifts every row's USD
 *   figure by a different amount, which is exactly the alignment that makes money scannable.
 * - *Form field* — a block hint under the input ({@link fxCapHint}), read on focus.
 * - *A meter's `aria-valuetext`* — USD only. A meter answers "how far along", and saying the same
 *   proportion twice in two currencies is a regression for anyone listening to it.
 *
 * `≈` is reserved for currency conversion on these surfaces. A figure that is approximate for some
 * other reason (an average, a projection) says so in its label, so the glyph keeps one meaning.
 *
 * `fx` absent is the default for every instance that never opted in, and is also what the server
 * sends when the stored rate is stale or missing. It is not an error state: every formatter here
 * returns the plain USD string it would have returned before this feature existed, and every
 * component renders `null`. No footnote, no empty slot, no layout shift.
 */

/** No conversion below this: `formatCostUsd` renders it as the bound `<$0.01`, not as a figure. */
const SUB_CENT_USD = 0.005;

/** Cents for spend — the same precision the USD figure it sits beside is shown at. */
const SPEND_DIGITS = 2;
/**
 * Whole units for a cap. `€43.95` beside a round `$50` claims a precision the estimate does not
 * have; `€44` says "about this much", which is all a converted cap can honestly say.
 */
const CAP_DIGITS = 0;
/** The rate itself, in the one place it is quoted: `1 USD ≈ €0.879`. */
const RATE_DIGITS = 3;

/** The conversion block a response may carry, in every shape a caller might hold it in. */
export type Fx = FxRateInfo | null | undefined;

type CurrencyDisplay = "symbol" | "code" | "name";

/**
 * `Intl.NumberFormat` construction is expensive and these run per table cell, so formatters are
 * built once per (currency, precision, display). A `null` entry records a currency code the
 * platform rejects — cached too, so a bad code costs one throw rather than one per render.
 */
const formatters = new Map<string, Intl.NumberFormat | null>();

/**
 * Deliberately `en-US`, not the viewer's locale: `de-DE` renders `3,96 €`, and a comma decimal
 * inside a parenthetical hanging off `$4.50` puts two number grammars in one line. Full i18n is a
 * separate effort — until then one grammar, consistently.
 */
function formatter(
	currencyCode: string,
	digits: number,
	currencyDisplay: CurrencyDisplay,
): Intl.NumberFormat | null {
	const key = `${currencyCode}|${digits}|${currencyDisplay}`;
	const cached = formatters.get(key);
	if (cached !== undefined) {
		return cached;
	}
	let built: Intl.NumberFormat | null;
	try {
		built = new Intl.NumberFormat("en-US", {
			style: "currency",
			currency: currencyCode,
			currencyDisplay,
			minimumFractionDigits: digits,
			maximumFractionDigits: digits,
		});
	} catch {
		// An unknown ISO code throws. This is presentation, not accounting — fall back to USD alone
		// rather than taking the page down over a display preference.
		built = null;
	}
	formatters.set(key, built);
	return built;
}

const usesIsoCode = new Map<string, boolean>();

/**
 * Which currency notation this code renders in.
 *
 * A `$` in the secondary amount would be read as dollars — which is exactly what the primary
 * amount right next to it is. Any currency whose `en-US` symbol contains a dollar sign (CAD, AUD
 * and friends, rendered `CA$`/`A$` by some ICU builds and a bare `$` by others) therefore shows its
 * ISO code instead: `$50 (≈ CAD 44)`. Currencies with their own glyph — €, £, ¥ — keep it.
 */
function displayFor(currencyCode: string): CurrencyDisplay {
	let ambiguous = usesIsoCode.get(currencyCode);
	if (ambiguous === undefined) {
		const symbol =
			formatter(currencyCode, 2, "symbol")
				?.formatToParts(1)
				.find((part) => part.type === "currency")?.value ?? "";
		ambiguous = currencyCode !== "USD" && symbol.includes("$");
		usesIsoCode.set(currencyCode, ambiguous);
	}
	return ambiguous ? "code" : "symbol";
}

/**
 * One converted amount, written and spoken.
 *
 * Both forms come out of `Intl` — the spoken one from `currencyDisplay: "name"`, so "euros" /
 * "Japanese yen" / "pounds sterling" are the platform's words rather than an English table we would
 * have to keep per currency.
 */
function amountIn(
	usd: number | null | undefined,
	fx: Fx,
	digits: number,
): { written: string; spoken: string } | null {
	if (fx == null || usd == null || !Number.isFinite(usd) || usd < SUB_CENT_USD) {
		return null;
	}
	if (!Number.isFinite(fx.ratePerUsd) || fx.ratePerUsd <= 0) {
		return null;
	}
	const value = usd * fx.ratePerUsd;
	const written = formatter(fx.currencyCode, digits, displayFor(fx.currencyCode))?.format(value);
	const spoken = formatter(fx.currencyCode, digits, "name")?.format(value);
	return written != null && spoken != null ? { written, spoken } : null;
}

/** A secondary amount, ready to render: what it looks like and what it should sound like. */
export interface FxConversion {
	/** As it renders, without surrounding punctuation: `≈ €3.96`. */
	text: string;
	/** As it is announced: `approximately 3.96 euros`. */
	label: string;
}

function conversionOf(amount: { written: string; spoken: string } | null): FxConversion | null {
	return amount == null
		? null
		: { text: `≈ ${amount.written}`, label: `approximately ${amount.spoken}` };
}

/**
 * The estimate beside a spend figure, or `null` when there must not be one.
 *
 * Nothing spent converts to nothing worth reading — `$0 (≈ €0)` is noise — and `<$0.01` is a bound
 * rather than an amount, so a converted bound would be false precision. Both stay USD-only.
 */
export function spendConversion(usd: number | null | undefined, fx: Fx): FxConversion | null {
	return conversionOf(amountIn(usd, fx, SPEND_DIGITS));
}

/** The estimate beside a cap, rounded to whole units. */
export function capConversion(usd: number | null | undefined, fx: Fx): FxConversion | null {
	return conversionOf(amountIn(usd, fx, CAP_DIGITS));
}

/**
 * The estimate beside an "X of Y" line: `≈ €38.59 of €44`.
 *
 * All or nothing — if either side has no conversion (a $0 spend, an absent cap) the line stays USD
 * only, because half a parenthetical reads as a mistake. The page footnote covers those lines.
 */
export function spendOfCapConversion(
	spendUsd: number | null | undefined,
	capUsd: number | null | undefined,
	fx: Fx,
): FxConversion | null {
	const spend = amountIn(spendUsd, fx, SPEND_DIGITS);
	const cap = amountIn(capUsd, fx, CAP_DIGITS);
	if (spend == null || cap == null) {
		return null;
	}
	return {
		text: `≈ ${spend.written} of ${cap.written}`,
		label: `approximately ${spend.spoken} of ${cap.spoken}`,
	};
}

/**
 * The day the rate was published, e.g. `Jul 24, 2026`. UTC, like every other date on these pages,
 * and `en-US` for the same reason the amounts are.
 */
function formatRateDate(value: FxRateInfo["rateDate"]): string {
	// The generated client types this as `Date`, but the response transformers aren't wired into the
	// SDK calls, so at runtime it arrives as an ISO string — coerce defensively, as `usageUtils` does.
	const date = value instanceof Date ? value : new Date(value);
	return date.toLocaleDateString("en-US", {
		month: "short",
		day: "numeric",
		year: "numeric",
		timeZone: "UTC",
	});
}

interface DisclosureParts {
	/** `EUR amounts are estimates at the ECB reference rate for Jul 24, 2026` */
	lead: string;
	/** `1 USD ≈ €0.879`, or `null` for a closed month, whose sentence quotes no live rate. */
	rate: FxConversion | null;
	/** The sentence that closes the caption, punctuation included. */
	tail: string;
}

function disclosureParts(fx: Fx, isCurrentMonth: boolean): DisclosureParts | null {
	if (fx == null) {
		return null;
	}
	const lead = `${fx.currencyCode} amounts are estimates at the ECB reference rate for ${formatRateDate(fx.rateDate)}`;
	if (!isCurrentMonth) {
		// A closed month resolves to a rate dated inside it, so its figures are frozen. Saying so is
		// the whole point of the variant: nobody should wonder why last month moved overnight.
		return {
			lead,
			rate: null,
			tail: ". The last rate published that month, so past figures don't change.",
		};
	}
	const written = formatter(fx.currencyCode, RATE_DIGITS, displayFor(fx.currencyCode))?.format(
		fx.ratePerUsd,
	);
	const spoken = formatter(fx.currencyCode, RATE_DIGITS, "name")?.format(fx.ratePerUsd);
	if (written == null || spoken == null) {
		return null;
	}
	return {
		lead,
		rate: {
			text: `1 USD ≈ ${written}`,
			// "USD" is the fixed base of this whole feature, so its English name is spelled out here
			// rather than derived; only the display currency varies, and that word comes from `Intl`.
			label: `1 US dollar is approximately ${spoken}`,
		},
		tail: ". Spend is metered and enforced in USD.",
	};
}

/** The page footnote as plain text — the same sentence {@link FxDisclosure} renders. */
export function fxDisclosureText(fx: Fx, isCurrentMonth: boolean): string | null {
	const parts = disclosureParts(fx, isCurrentMonth);
	if (parts == null) {
		return null;
	}
	return parts.rate == null
		? `${parts.lead}${parts.tail}`
		: `${parts.lead} (${parts.rate.text})${parts.tail}`;
}

/** The live hint under a cap field: `≈ €44` plus the sentence that qualifies it. */
export interface FxCapHint {
	conversion: FxConversion;
	/** The rest of the sentence, leading space included. */
	tail: string;
}

/**
 * What the amount someone is typing into a cap field is worth, at today's rate — `null` while
 * there is nothing meaningful to convert (empty, unparseable, or zero), so the hint simply is not
 * there rather than flickering a `≈ €0`.
 */
export function fxCapHint(valueUsd: number | null | undefined, fx: Fx): FxCapHint | null {
	const conversion = capConversion(valueUsd, fx);
	if (conversion == null || fx == null) {
		return null;
	}
	return {
		conversion,
		tail: " at today's rate.",
	};
}

export interface FxApproxProps {
	conversion: FxConversion;
	className?: string;
}

/**
 * A converted amount, spoken as words.
 *
 * `role="img"` is load-bearing: `aria-label` on a bare `<span>` has no `generic` role to attach to
 * and is dropped by conforming assistive tech, and left to read the text itself a screen reader
 * announces "≈" as "tilde operator" or skips it — either way the "estimate" the glyph carries is
 * lost. `role="img"` is the same treatment this codebase already gives symbol-only content, and it
 * replaces the node's text with the label outright.
 */
export function FxApprox({ conversion, className }: FxApproxProps) {
	return (
		<span role="img" aria-label={conversion.label} className={cn("whitespace-nowrap", className)}>
			{conversion.text}
		</span>
	);
}

export interface FxAmountProps {
	/** `null` renders nothing at all — no wrapper, no space, no punctuation. */
	conversion: FxConversion | null;
	className?: string;
}

/** The parenthetical that trails a USD figure: ` (≈ €3.96)`, separating space included. */
export function FxAmount({ conversion, className }: FxAmountProps) {
	if (conversion == null) {
		return null;
	}
	return (
		<>
			{" "}
			<span role="img" aria-label={conversion.label} className={cn("whitespace-nowrap", className)}>
				({conversion.text})
			</span>
		</>
	);
}

export interface FxSpendProps {
	usd: number | null | undefined;
	fx: Fx;
	className?: string;
}

/** ` (≈ €44)` after a cap. */
export function FxCap({ usd, fx, className }: FxSpendProps) {
	return <FxAmount conversion={capConversion(usd, fx)} className={className} />;
}

/**
 * The estimate *under* a figure rather than beside it — the table-cell form.
 *
 * Muted and small, because the USD figure above it is the amount and this is a reading aid. Renders
 * nothing at all when there is no conversion, so a column of `$0` rows keeps its single-line height.
 */
export function FxLine({ conversion, className }: FxAmountProps) {
	if (conversion == null) {
		return null;
	}
	return (
		<div className={cn("text-xs font-normal text-muted-foreground tabular-nums", className)}>
			<FxApprox conversion={conversion} />
		</div>
	);
}

/** `≈ €3.96` on its own line under a spend figure — the form every money cell uses. */
export function FxSpendLine({ usd, fx, className }: FxSpendProps) {
	return <FxLine conversion={spendConversion(usd, fx)} className={className} />;
}

export interface FxDisclosureProps {
	fx: Fx;
	/** A closed month's rate is frozen inside it, which changes what the caption promises. */
	isCurrentMonth: boolean;
	className?: string;
}

/**
 * The one place a page explains its converted amounts.
 *
 * Once per page, under the figures it covers — never per number, where it would drown the numbers
 * it is meant to qualify. Renders nothing when there is no conversion on the page.
 */
export function FxDisclosure({ fx, isCurrentMonth, className }: FxDisclosureProps) {
	const parts = disclosureParts(fx, isCurrentMonth);
	if (parts == null) {
		return null;
	}
	return (
		<p className={cn("text-sm text-muted-foreground", className)}>
			{parts.lead}
			{parts.rate != null && (
				<>
					{" ("}
					<FxApprox conversion={parts.rate} />
					{")"}
				</>
			)}
			{parts.tail}
		</p>
	);
}

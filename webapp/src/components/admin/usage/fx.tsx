import type { FxRateInfo } from "@/api/types.gen";
import { asDate } from "@/lib/dates";
import { cn } from "@/lib/utils";

/**
 * Display-only currency conversion for the AI-usage surfaces.
 *
 * USD stays authoritative — spend is metered, capped and enforced in USD, and the server only sends
 * USD. A converted figure is a secondary estimate beside it: `$4.50 (≈ €3.96)`. Absent `fx` is the
 * norm rather than an error: every formatter returns the plain USD string and every component
 * renders `null`, so an instance that never opted in shows no footnote and no empty slot.
 *
 * Table cells take the estimate on a second line, not as an inline suffix: a variable-width suffix
 * shifts each row's USD figure differently and breaks the right-edge alignment a money column is
 * scanned by. A meter's `aria-valuetext` stays USD-only — it answers "how far along", and saying
 * the same proportion twice is a regression for anyone listening to it.
 */

/** Below this, `formatCostUsd` renders the bound `<$0.01`, which is not a figure to convert. */
const SUB_CENT_USD = 0.005;

const SPEND_DIGITS = 2;
/** Whole units: `€43.95` beside a round `$50` claims a precision the estimate does not have. */
const CAP_DIGITS = 0;
const RATE_DIGITS = 3;

export type Fx = FxRateInfo | null | undefined;

type CurrencyDisplay = "symbol" | "code" | "name";

/** Keyed by (currency, digits, display); a `null` entry caches a code the platform rejected. */
const formatters = new Map<string, Intl.NumberFormat | null>();

/**
 * `en-US` deliberately, not the viewer's locale: `de-DE` renders `3,96 €`, and a comma decimal in a
 * parenthetical hanging off `$4.50` puts two number grammars on one line.
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
 * A dollar-like symbol beside the primary USD figure would read as dollars, so any currency whose
 * `en-US` symbol contains `$` (CAD, AUD — `CA$` on some ICU builds, a bare `$` on others) shows its
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
 * The spoken form comes from `currencyDisplay: "name"`, so currency words are `Intl`'s, not ours.
 *
 * An amount below one unit of the requested precision converts to nothing at all. `SUB_CENT_USD`
 * bounds the *USD* side only, and at {@link CAP_DIGITS} that leaves the estimate free to be wrong by
 * most of itself: a $0.40 cap is €0.35, which whole units render as `≈ €0` — free, which it is not —
 * and $0.60 is €0.53, rendered `≈ €1`, nearly double. Neither is a rounding wobble a reader can
 * discount, so below the first whole unit there is no figure rather than a misleading one. One
 * guard, in the one place both precisions pass through.
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
	if (value < 10 ** -digits) {
		return null;
	}
	const written = formatter(fx.currencyCode, digits, displayFor(fx.currencyCode))?.format(value);
	const spoken = formatter(fx.currencyCode, digits, "name")?.format(value);
	return written != null && spoken != null ? { written, spoken } : null;
}

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

/** `$0` and the `<$0.01` bound stay USD-only: a converted bound would be false precision. */
export function spendConversion(usd: number | null | undefined, fx: Fx): FxConversion | null {
	return conversionOf(amountIn(usd, fx, SPEND_DIGITS));
}

export function capConversion(usd: number | null | undefined, fx: Fx): FxConversion | null {
	return conversionOf(amountIn(usd, fx, CAP_DIGITS));
}

/**
 * `≈ €38.59 of €44` — all or nothing. If either side has no conversion the line stays USD-only,
 * because half a parenthetical reads as a mistake.
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

/** UTC, like every other date on these pages, and `en-US` for the same reason the amounts are. */
function formatRateDate(value: FxRateInfo["rateDate"]): string {
	const date = asDate(value);
	if (!date) return "–";
	return date.toLocaleDateString("en-US", {
		month: "short",
		day: "numeric",
		year: "numeric",
		timeZone: "UTC",
	});
}

/**
 * Who published the rate, spelled out for the disclosure.
 *
 * Keyed by the server's `source`, so the sentence can only ever name a publisher the payload
 * actually claims. An unknown key — a client older than the server, once a second feed exists —
 * falls back to the unattributed wording rather than naming the wrong bank.
 */
const SOURCE_NAMES: Record<FxRateInfo["source"], string> = {
	ECB: "European Central Bank",
};

interface DisclosureParts {
	/** `EUR amounts are estimates at the European Central Bank reference rate published on Jul 24, 2026` */
	lead: string;
	/** The rate itself: `1 USD ≈ €0.879`. */
	rate: FxConversion;
	tail: string;
}

function disclosureParts(fx: Fx, isCurrentMonth: boolean): DisclosureParts | null {
	if (fx == null) {
		return null;
	}
	const written = formatter(fx.currencyCode, RATE_DIGITS, displayFor(fx.currencyCode))?.format(
		fx.ratePerUsd,
	);
	const spoken = formatter(fx.currencyCode, RATE_DIGITS, "name")?.format(fx.ratePerUsd);
	if (written == null || spoken == null) {
		return null;
	}
	const publisher = SOURCE_NAMES[fx.source] as string | undefined;
	return {
		lead: `${fx.currencyCode} amounts are estimates at the ${publisher == null ? "" : `${publisher} `}reference rate published on ${formatRateDate(fx.rateDate)}`,
		rate: {
			text: `1 USD ≈ ${written}`,
			label: `1 US dollar is approximately ${spoken}`,
		},
		// A closed month resolves to a rate dated inside it, so its figures are frozen. Say so, or a
		// reader wonders why last month moved overnight — and quote the frozen rate, which is the one
		// number that makes the claim checkable.
		tail: isCurrentMonth
			? ". Spend is metered and enforced in USD."
			: ". That is the last rate published in the month shown, so these figures no longer change.",
	};
}

export interface FxCapHint {
	conversion: FxConversion;
	/** The rest of the sentence, leading space included. */
	tail: string;
}

/**
 * The live hint under a cap field. `null` while there is nothing meaningful to convert (empty,
 * unparseable, or too small to reach one whole unit), so the hint is absent rather than flickering
 * `≈ €0` as someone types.
 *
 * `isCurrentMonth` is what makes "at today's rate" true, and it is checked here rather than trusted
 * of the caller. Only the current month resolves to the latest published rate; a closed month
 * resolves to a rate frozen inside it, and the same sentence over that rate would contradict the
 * page behind the dialog, which says in so many words that those figures no longer change. Today's
 * rate is not in this payload, so there is no honest figure to put in its place — the hint is
 * withdrawn instead.
 *
 * The editors are already withheld on a closed month, but that is a fact about two components and
 * the dialog outlives it: `open` is plain React state that survives stepping the month behind it
 * (browser Back, in particular), and `isCurrentMonth` can flip under an already-open dialog when a
 * UTC month rolls over. The guard has to sit where the sentence is written.
 */
export function fxCapHint(
	valueUsd: number | null | undefined,
	fx: Fx,
	isCurrentMonth: boolean,
): FxCapHint | null {
	const conversion = capConversion(valueUsd, fx);
	if (conversion == null || fx == null || !isCurrentMonth) {
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
 * `role="img"` is load-bearing: `aria-label` on a bare `<span>` has no role to attach to and is
 * dropped by conforming assistive tech, and read literally "≈" is announced as "tilde operator" or
 * skipped — either way the "estimate" the glyph carries is lost.
 */
export function FxApprox({ conversion, className }: FxApproxProps) {
	return (
		<span role="img" aria-label={conversion.label} className={cn("whitespace-nowrap", className)}>
			{conversion.text}
		</span>
	);
}

export interface FxAmountProps {
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

/**
 * `≈ €3.96` on its own line under a spend figure — the form every money cell uses. Renders nothing
 * when there is no conversion, so a column of `$0` rows keeps its single-line height.
 */
export function FxSpendLine({ usd, fx, className }: FxSpendProps) {
	const conversion = spendConversion(usd, fx);
	if (conversion == null) {
		return null;
	}
	return (
		<div className={cn("text-xs font-normal text-muted-foreground tabular-nums", className)}>
			<FxApprox conversion={conversion} />
		</div>
	);
}

export interface FxDisclosureProps {
	fx: Fx;
	/** A closed month's rate is frozen inside it, which changes what the caption promises. */
	isCurrentMonth: boolean;
	className?: string;
}

/** Once per page, under the figures it covers — never per number, where it would drown them. */
export function FxDisclosure({ fx, isCurrentMonth, className }: FxDisclosureProps) {
	const parts = disclosureParts(fx, isCurrentMonth);
	if (parts == null) {
		return null;
	}
	return (
		<p className={cn("text-sm text-muted-foreground", className)}>
			{parts.lead}
			{" ("}
			<FxApprox conversion={parts.rate} />
			{")"}
			{parts.tail}
		</p>
	);
}

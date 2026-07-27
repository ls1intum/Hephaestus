import type { FxRateInfo } from "@/api/types.gen";
import { asDate } from "@/lib/dates";

/**
 * Display-only currency conversion for the AI-usage surfaces. USD stays authoritative — spend is
 * metered, capped and enforced in USD — so a converted figure is only ever a secondary estimate
 * beside it: `$4.50 (≈ €3.96)`. Absent `fx` is the norm, not an error: everything here degrades to
 * the plain USD string or `null`.
 */

/** Below this, `formatCostUsd` renders the bound `<$0.01`, which is not a figure to convert. */
const SUB_CENT_USD = 0.005;

const SPEND_DIGITS = 2;
/** Whole units: `€43.95` beside a round `$50` claims a precision the estimate does not have. */
const CAP_DIGITS = 0;
const RATE_DIGITS = 3;

export type Fx = FxRateInfo | null | undefined;

type CurrencyDisplay = "symbol" | "code" | "name";

/** A cached `null` is a currency code the platform rejected, so `undefined` means "not tried yet". */
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
		// An unknown ISO code throws. This is presentation, not accounting — fall back to USD alone.
		built = null;
	}
	formatters.set(key, built);
	return built;
}

const usesIsoCode = new Map<string, boolean>();

/**
 * A dollar-like symbol beside the primary USD figure would read as dollars, so any currency whose
 * symbol contains `$` (CAD, AUD) shows its ISO code instead: `$50 (≈ CAD 44)`.
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
 * Below one unit of the requested precision there is no figure rather than a misleading one: at
 * {@link CAP_DIGITS} a $0.40 cap would render `≈ €0` (free, which it is not) and $0.60 as `≈ €1`,
 * nearly double.
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

/** `≈ €38.59 of €44` — all or nothing, because half a parenthetical reads as a mistake. */
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

function formatRateDate(value: FxRateInfo["rateDate"]): string {
	const date = asDate(value);
	if (!date) return "–";
	return date.toLocaleDateString(undefined, {
		month: "short",
		day: "numeric",
		year: "numeric",
		timeZone: "UTC",
	});
}

/** Total, so adding a source to the spec fails the build until it is named here. */
const SOURCE_NAMES: Record<FxRateInfo["source"], string> = {
	ECB: "European Central Bank",
};

/**
 * A newer server can send a source this client's spec does not know, so an unnamed publisher falls
 * back to the unattributed wording rather than naming the wrong bank.
 */
function publisherOf(source: FxRateInfo["source"]): string | undefined {
	return (SOURCE_NAMES as Record<string, string | undefined>)[source];
}

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
	const publisher = publisherOf(fx.source);
	const attribution = publisher == null ? "" : `${publisher} `;
	return {
		lead: `${fx.currencyCode} amounts are estimates at the ${attribution}reference rate published on ${formatRateDate(fx.rateDate)}`,
		rate: {
			text: `1 USD ≈ ${written}`,
			label: `1 US dollar is approximately ${spoken}`,
		},
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
 * The live hint under a cap field. Only the current month resolves to the latest published rate, so
 * `isCurrentMonth` is what makes "at today's rate" true — and it is re-checked here rather than
 * trusted of the caller, because an open dialog survives the month stepping behind it (browser Back,
 * or a UTC month rollover).
 */
export function fxCapHint(
	valueUsd: number | null | undefined,
	fx: Fx,
	isCurrentMonth: boolean,
): FxCapHint | null {
	const conversion = capConversion(valueUsd, fx);
	if (conversion == null || !isCurrentMonth) {
		return null;
	}
	return {
		conversion,
		tail: " at today's rate.",
	};
}

export interface FxApproxProps {
	conversion: FxConversion;
}

/**
 * `role="img"` is load-bearing: `aria-label` on a bare `<span>` has no role to attach to and is
 * dropped, and "≈" read literally is announced as "tilde operator" or skipped.
 */
export function FxApprox({ conversion }: FxApproxProps) {
	return (
		<span role="img" aria-label={conversion.label} className="whitespace-nowrap">
			{conversion.text}
		</span>
	);
}

export interface FxAmountProps {
	conversion: FxConversion | null;
}

/** The parenthetical that trails a USD figure: ` (≈ €3.96)`, separating space included. */
export function FxAmount({ conversion }: FxAmountProps) {
	if (conversion == null) {
		return null;
	}
	return (
		<>
			{" "}
			<span role="img" aria-label={conversion.label} className="whitespace-nowrap">
				({conversion.text})
			</span>
		</>
	);
}

export interface FxSpendProps {
	usd: number | null | undefined;
	fx: Fx;
}

/**
 * `≈ €3.96` on a second line under a spend figure, never as an inline suffix: a variable-width
 * suffix shifts each row's USD figure differently and breaks a money column's right-edge alignment.
 */
export function FxSpendLine({ usd, fx }: FxSpendProps) {
	const conversion = spendConversion(usd, fx);
	if (conversion == null) {
		return null;
	}
	return (
		<div className="text-xs font-normal text-muted-foreground tabular-nums">
			<FxApprox conversion={conversion} />
		</div>
	);
}

export interface FxDisclosureProps {
	fx: Fx;
	/** A closed month's rate is frozen inside it, which changes what the caption promises. */
	isCurrentMonth: boolean;
}

/** Once per page, under the figures it covers. */
export function FxDisclosure({ fx, isCurrentMonth }: FxDisclosureProps) {
	const parts = disclosureParts(fx, isCurrentMonth);
	if (parts == null) {
		return null;
	}
	return (
		<p className="text-sm text-muted-foreground">
			{parts.lead}
			{" ("}
			<FxApprox conversion={parts.rate} />
			{")"}
			{parts.tail}
		</p>
	);
}

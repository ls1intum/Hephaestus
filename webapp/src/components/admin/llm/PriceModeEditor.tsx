import { Field, FieldDescription, FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import type { PricingMode } from "@/lib/llmPricing";

export interface PriceModeValue {
	pricingMode: PricingMode;
	per1mInputUsd?: number;
	per1mOutputUsd?: number;
	per1mCacheReadUsd?: number;
	per1mCacheWriteUsd?: number;
	per1mReasoningUsd?: number;
	note?: string;
}

export interface PriceModeEditorProps {
	/** Instance admin sets a price for everyone; a workspace admin sets it for their own connection. */
	audience: "instance" | "workspace";
	value: PriceModeValue;
	onChange: (value: PriceModeValue) => void;
	errors?: { per1mInputUsd?: string; per1mOutputUsd?: string; note?: string };
	disabled?: boolean;
	idPrefix: string;
}

/**
 * The price radio + fields shared by the instance model form and the workspace BYO model form
 * (#1368 glossary). "Free" and "No price set" relabel per audience — every workspace model would
 * otherwise read "Free", which undersells that the organization is paying for it.
 */
export function PriceModeEditor({
	audience,
	value,
	onChange,
	errors,
	disabled = false,
	idPrefix,
}: PriceModeEditorProps) {
	const set = <K extends keyof PriceModeValue>(key: K, next: PriceModeValue[K]) => {
		onChange({ ...value, [key]: next });
	};

	const freeLabel = audience === "instance" ? "Free" : "No budget cost";
	const unpricedLabel = audience === "instance" ? "No price set" : "Price not set";

	const numberField = (
		key:
			| "per1mInputUsd"
			| "per1mOutputUsd"
			| "per1mCacheReadUsd"
			| "per1mCacheWriteUsd"
			| "per1mReasoningUsd",
		label: string,
		required: boolean,
	) => {
		const id = `${idPrefix}-${key}`;
		const error =
			key === "per1mInputUsd"
				? errors?.per1mInputUsd
				: key === "per1mOutputUsd"
					? errors?.per1mOutputUsd
					: undefined;
		return (
			<Field data-invalid={Boolean(error)} key={key}>
				<FieldLabel htmlFor={id}>
					{label}
					{required && (
						<span className="text-destructive" aria-hidden="true">
							{" *"}
						</span>
					)}
				</FieldLabel>
				<Input
					id={id}
					type="number"
					min={0}
					step="0.01"
					inputMode="decimal"
					value={value[key] != null ? String(value[key]) : ""}
					disabled={disabled}
					onChange={(e) => {
						const raw = e.target.value;
						set(key, raw === "" ? undefined : Number(raw));
					}}
					aria-required={required}
					aria-invalid={Boolean(error)}
				/>
				{error && <FieldError>{error}</FieldError>}
			</Field>
		);
	};

	return (
		<Field>
			<FieldLabel>Price</FieldLabel>
			<RadioGroup
				value={value.pricingMode}
				onValueChange={(next) => {
					if (next) set("pricingMode", next as PricingMode);
				}}
				disabled={disabled}
				aria-label="Price"
			>
				<div className="flex items-center gap-2 text-sm font-normal">
					<RadioGroupItem value="PRICED" id={`${idPrefix}-mode-priced`} />
					<label htmlFor={`${idPrefix}-mode-priced`}>Price per 1M tokens</label>
				</div>
				<div className="flex items-center gap-2 text-sm font-normal">
					<RadioGroupItem value="FREE" id={`${idPrefix}-mode-free`} />
					<label htmlFor={`${idPrefix}-mode-free`}>{freeLabel}</label>
				</div>
				<div className="flex items-center gap-2 text-sm font-normal">
					<RadioGroupItem value="UNPRICED" id={`${idPrefix}-mode-unpriced`} />
					<label htmlFor={`${idPrefix}-mode-unpriced`}>{unpricedLabel}</label>
				</div>
			</RadioGroup>

			{value.pricingMode === "PRICED" && (
				<div className="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-2">
					{numberField("per1mInputUsd", "Input (USD)", true)}
					{numberField("per1mOutputUsd", "Output (USD)", true)}
					{numberField("per1mCacheReadUsd", "Cache read (USD)", false)}
					{numberField("per1mCacheWriteUsd", "Cache write (USD)", false)}
					{numberField("per1mReasoningUsd", "Reasoning (USD)", false)}
				</div>
			)}

			{value.pricingMode === "FREE" && (
				<Field data-invalid={Boolean(errors?.note)} className="mt-3">
					<FieldLabel htmlFor={`${idPrefix}-note`}>
						Note
						<span className="text-destructive" aria-hidden="true">
							{" *"}
						</span>
					</FieldLabel>
					<Input
						id={`${idPrefix}-note`}
						value={value.note ?? ""}
						disabled={disabled}
						onChange={(e) => set("note", e.target.value)}
						placeholder="e.g. self-hosted, no cost"
						aria-required="true"
						aria-invalid={Boolean(errors?.note)}
					/>
					<FieldDescription>self-hosted, no cost</FieldDescription>
					{errors?.note && <FieldError>{errors.note}</FieldError>}
				</Field>
			)}
		</Field>
	);
}

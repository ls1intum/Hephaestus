import type { AvailableLlmModel } from "@/api/types.gen";
import {
	Select,
	SelectContent,
	SelectGroup,
	SelectItem,
	SelectLabel,
	SelectTrigger,
	SelectValue,
} from "@/components/ui/select";
import { priceLabel } from "@/lib/llm-pricing";

export interface ModelSelection {
	scope: "SHARED" | "WORKSPACE";
	id: number;
}

export interface ModelPickerProps {
	id?: string;
	availableModels: AvailableLlmModel[];
	value: ModelSelection | null;
	onChange: (selection: ModelSelection) => void;
	disabled?: boolean;
	invalid?: boolean;
	"aria-describedby"?: string;
}

function encode(scope: ModelSelection["scope"], id: number): string {
	return `${scope}:${id}`;
}

function decode(value: string): ModelSelection {
	const [scope, id] = value.split(":");
	return { scope: scope as ModelSelection["scope"], id: Number(id) };
}

/**
 * An option's accessible name. It has to repeat the price that the visual row shows on its right:
 * an explicit `aria-label` replaces the item's contents wholesale, so leaving the price out would
 * cost screen-reader users the one number the choice turns on.
 */
function optionLabel(model: AvailableLlmModel): string {
	return `${model.displayName} · ${model.connectionDisplayName} · ${priceLabel(model, "workspace")}`;
}

function ModelOptions({ models }: { models: AvailableLlmModel[] }) {
	return models.map((model) => (
		<SelectItem
			key={encode(model.scope, model.id)}
			value={encode(model.scope, model.id)}
			aria-label={optionLabel(model)}
		>
			<span className="flex min-w-0 flex-1 items-center justify-between gap-2">
				<span className="min-w-0 truncate">
					{model.displayName}
					<span className="text-muted-foreground"> · {model.connectionDisplayName}</span>
				</span>
				<span className="shrink-0 text-xs text-muted-foreground">
					{priceLabel(model, "workspace")}
				</span>
			</span>
		</SelectItem>
	));
}

/**
 * Grouped by funding source. Never shows the upstream model id or the connection's endpoint — only
 * the display name and price framing.
 */
export function ModelPicker({
	id,
	availableModels,
	value,
	onChange,
	disabled = false,
	invalid = false,
	"aria-describedby": ariaDescribedBy,
}: ModelPickerProps) {
	const shared = availableModels.filter((model) => model.scope === "SHARED");
	const own = availableModels.filter((model) => model.scope === "WORKSPACE");

	return (
		<Select
			items={availableModels.map((model) => ({
				value: encode(model.scope, model.id),
				label: `${model.displayName} · ${model.connectionDisplayName}`,
			}))}
			value={value ? encode(value.scope, value.id) : null}
			onValueChange={(next) => {
				if (next) onChange(decode(next));
			}}
			disabled={disabled}
		>
			<SelectTrigger
				id={id}
				className="w-full"
				aria-invalid={invalid}
				aria-describedby={ariaDescribedBy}
			>
				<SelectValue placeholder="Select a model…" />
			</SelectTrigger>
			<SelectContent>
				{shared.length > 0 && (
					<SelectGroup>
						<SelectLabel>Shared models</SelectLabel>
						<ModelOptions models={shared} />
					</SelectGroup>
				)}
				{own.length > 0 && (
					<SelectGroup>
						<SelectLabel>Your models</SelectLabel>
						<ModelOptions models={own} />
					</SelectGroup>
				)}
			</SelectContent>
		</Select>
	);
}

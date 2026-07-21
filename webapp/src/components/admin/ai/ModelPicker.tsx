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
import { priceLabel } from "@/lib/llmPricing";

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
 * Model picker for an agent config binding (#1368) — replaces the old raw provider/base-url/API-key/
 * model-name fields. Groups by funding source per the glossary: "Shared models" (provided by the
 * organization) and "Your provider" (the workspace's own connected key). Never shows the upstream
 * model id or the owning connection's endpoint, only the display name and price framing.
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
	const shared = availableModels.filter((m) => m.scope === "SHARED");
	const own = availableModels.filter((m) => m.scope === "WORKSPACE");

	return (
		<Select
			items={availableModels.map((m) => ({ value: encode(m.scope, m.id), label: m.displayName }))}
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
						<SelectLabel>
							Shared models
							<span className="block font-normal normal-case text-muted-foreground/80">
								provided by your organization
							</span>
						</SelectLabel>
						{shared.map((model) => (
							<SelectItem key={encode("SHARED", model.id)} value={encode("SHARED", model.id)}>
								<span className="flex min-w-0 flex-1 items-center justify-between gap-2">
									<span className="truncate">{model.displayName}</span>
									<span className="shrink-0 text-xs text-muted-foreground">
										{priceLabel(model, "workspace")}
									</span>
								</span>
							</SelectItem>
						))}
					</SelectGroup>
				)}
				{own.length > 0 && (
					<SelectGroup>
						<SelectLabel>Your provider</SelectLabel>
						{own.map((model) => (
							<SelectItem key={encode("WORKSPACE", model.id)} value={encode("WORKSPACE", model.id)}>
								<span className="flex min-w-0 flex-1 items-center justify-between gap-2">
									<span className="truncate">{model.displayName}</span>
									<span className="shrink-0 text-xs text-muted-foreground">
										{priceLabel(model, "workspace")}
									</span>
								</span>
							</SelectItem>
						))}
					</SelectGroup>
				)}
			</SelectContent>
		</Select>
	);
}

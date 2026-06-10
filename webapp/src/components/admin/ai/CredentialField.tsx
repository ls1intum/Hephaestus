import { Eye, EyeOff } from "lucide-react";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Field, FieldDescription, FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";

export interface CredentialFieldProps {
	/** Whether a key is already stored server-side (never revealed to the client). */
	hasStoredKey: boolean;
	/** The in-progress (new) key value. */
	value: string;
	onChange: (value: string) => void;
	/** Request clearing the stored key (edit mode only). */
	onClear?: () => void;
	disabled?: boolean;
	error?: string;
	/** Show the required marker (the proxy needs a key, so one is required on create). */
	required?: boolean;
}

/**
 * API-key input for a model. The key is stored encrypted server-side and injected by the LLM proxy on
 * each call, so it never reaches the sandbox. The Eye toggle only reveals what the admin is typing — the
 * API never returns a stored key.
 */
export function CredentialField({
	hasStoredKey,
	value,
	onChange,
	onClear,
	disabled,
	error,
	required = false,
}: CredentialFieldProps) {
	const [revealed, setRevealed] = useState(false);

	const showClear = hasStoredKey && onClear !== undefined;

	return (
		<Field data-invalid={Boolean(error)}>
			<FieldLabel htmlFor="agent-llm-key">
				LLM API key
				{required && (
					<span className="text-destructive" aria-hidden="true">
						{" *"}
					</span>
				)}
			</FieldLabel>
			<div className="flex items-center gap-2">
				<div className="relative flex-1">
					<Input
						id="agent-llm-key"
						type={revealed ? "text" : "password"}
						value={value}
						onChange={(e) => onChange(e.target.value)}
						disabled={disabled}
						placeholder={hasStoredKey ? "••••••••••••••••" : "Enter API key"}
						autoComplete="off"
						aria-required={required}
						aria-invalid={Boolean(error)}
						aria-describedby={error ? "agent-llm-key-error" : undefined}
						className="pr-9"
					/>
					<Button
						type="button"
						variant="ghost"
						size="icon-sm"
						className="absolute top-1/2 right-1 -translate-y-1/2"
						onClick={() => setRevealed((r) => !r)}
						aria-label={revealed ? "Hide key" : "Show key"}
						disabled={disabled || value.length === 0}
					>
						{revealed ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
					</Button>
				</div>
				{showClear && (
					<Button
						type="button"
						variant="outline"
						size="sm"
						onClick={onClear}
						disabled={disabled}
						className="text-destructive"
					>
						Clear key
					</Button>
				)}
			</div>
			{hasStoredKey ? (
				<FieldDescription>Leave blank to keep the current key.</FieldDescription>
			) : (
				<FieldDescription>
					Stored encrypted; injected by the proxy, never sent to the sandbox.
				</FieldDescription>
			)}
			{error && <FieldError id="agent-llm-key-error">{error}</FieldError>}
		</Field>
	);
}

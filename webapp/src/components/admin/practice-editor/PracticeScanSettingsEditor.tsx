import type { PracticeItemTypeOption, ReadSourceOption } from "@/api/types.gen";
import { Button } from "@/components/ui/button";
import {
	Field,
	FieldContent,
	FieldDescription,
	FieldError,
	FieldGroup,
	FieldLabel,
	FieldTitle,
} from "@/components/ui/field";
import {
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
import { itemKindLabel } from "@/lib/item-kinds";

import { PracticeReadsEditor } from "./PracticeReadsEditor";
import {
	type ScanDraft,
	scanFieldId,
	type ScanPatternField,
	type ScanProblem,
} from "./scan-settings";

/**
 * Whether the practice takes part in scans, and the scan it would run. Turning scans off keeps the
 * draft, so turning them on again before a save restores what was written.
 */
export interface ScanSettings {
	enabled: boolean;
	draft: ScanDraft;
}

export interface PracticeScanSettingsEditorProps {
	itemType: PracticeItemTypeOption;
	value: ScanSettings;
	onChange: (value: ScanSettings) => void;
	/** The sources the scanned kind can read: a pull request practice scans files, which read others. */
	readSources: readonly ReadSourceOption[];
	problem?: ScanProblem;
	disabled?: boolean;
}

const PATTERN_FIELDS: {
	field: ScanPatternField;
	label: string;
	description: string;
	placeholder: string;
}[] = [
	{
		field: "include",
		label: "Include *",
		description: "The files the scan reviews. One pattern per line.",
		placeholder: "src/**/*.ts",
	},
	{
		field: "exclude",
		label: "Exclude",
		description: "Files the scan skips even when Include matches them.",
		placeholder: "**/*.gen.ts",
	},
	{
		field: "context",
		label: "Context",
		description:
			"Files the review may read around each file it reviews. They are never reviewed themselves.",
		placeholder: "**/*",
	},
];

export function PracticeScanSettingsEditor({
	itemType,
	value,
	onChange,
	readSources,
	problem,
	disabled = false,
}: PracticeScanSettingsEditorProps) {
	if (!itemType.scanSupported) {
		// A stored scan is kept as it is rather than erased behind the author's back: removing it is a
		// choice the author makes here.
		return value.enabled ? (
			<div className="space-y-2">
				<p className="text-sm text-muted-foreground">
					This kind of item no longer offers scans. The saved scan settings stay until you remove
					them.
				</p>
				<Button
					type="button"
					variant="outline"
					size="sm"
					disabled={disabled}
					onClick={() => onChange({ ...value, enabled: false })}
				>
					Remove scan settings
				</Button>
			</div>
		) : null;
	}
	const { draft } = value;
	const errorFor = (field: ScanProblem["field"]) =>
		problem?.field === field ? problem.message : undefined;
	const kindItems = itemType.scanItemKinds.map((kind) => ({
		value: kind,
		label: itemKindLabel(kind, 2),
	}));
	const setDraft = (next: Partial<ScanDraft>) =>
		onChange({ ...value, draft: { ...draft, ...next } });
	const readsError = errorFor("reads");

	return (
		<div className="space-y-5">
			<Field orientation="horizontal">
				<Switch
					id="practice-scan-enabled"
					checked={value.enabled}
					disabled={disabled}
					onCheckedChange={(enabled) => onChange({ ...value, enabled })}
					aria-describedby="practice-scan-description"
				/>
				<FieldContent>
					<FieldLabel htmlFor="practice-scan-enabled">
						<FieldTitle>Include in scans</FieldTitle>
					</FieldLabel>
					<FieldDescription id="practice-scan-description">
						A scan reviews the files of a repository against this practice. Its results are not
						attributed to any developer, so they never reach a practice page or a conversation.
					</FieldDescription>
				</FieldContent>
			</Field>

			{value.enabled && (
				<div className="space-y-5">
					<FieldGroup>
						<Field
							orientation="responsive"
							data-invalid={errorFor("itemKind") === undefined ? undefined : "true"}
						>
							<FieldLabel id="practice-scan-item-kind-label" htmlFor={scanFieldId("itemKind")}>
								Scan reviews
							</FieldLabel>
							<Select
								items={kindItems}
								value={draft.itemKind}
								disabled={disabled}
								onValueChange={(itemKind) => setDraft({ itemKind: itemKind ?? draft.itemKind })}
							>
								<SelectTrigger
									id={scanFieldId("itemKind")}
									className="w-full @md/field-group:w-56"
									aria-invalid={errorFor("itemKind") !== undefined}
								>
									<SelectValue />
								</SelectTrigger>
								<SelectContent aria-labelledby="practice-scan-item-kind-label">
									{kindItems.map((item) => (
										<SelectItem key={item.value} value={item.value}>
											{item.label}
										</SelectItem>
									))}
								</SelectContent>
							</Select>
							{errorFor("itemKind") !== undefined && (
								<FieldError id={`${scanFieldId("itemKind")}-error`}>
									{errorFor("itemKind")}
								</FieldError>
							)}
						</Field>
					</FieldGroup>

					{PATTERN_FIELDS.map(({ field, label, description, placeholder }) => {
						const id = scanFieldId(field);
						const error = errorFor(field);
						return (
							<Field key={field} data-invalid={error === undefined ? undefined : "true"}>
								<FieldLabel htmlFor={id}>{label}</FieldLabel>
								<FieldDescription id={`${id}-description`}>{description}</FieldDescription>
								<Textarea
									id={id}
									value={draft[field]}
									disabled={disabled}
									onChange={(event) => setDraft({ [field]: event.target.value })}
									placeholder={placeholder}
									className="min-h-20"
									spellCheck={false}
									aria-invalid={error !== undefined}
									aria-describedby={`${id}-description${error === undefined ? "" : ` ${id}-error`}`}
								/>
								{error !== undefined && <FieldError id={`${id}-error`}>{error}</FieldError>}
							</Field>
						);
					})}

					{readSources.length > 0 ? (
						<PracticeReadsEditor
							readSources={readSources}
							sources={draft.reads}
							legend="Scan reads"
							idPrefix="practice-scan"
							disabled={disabled}
							invalid={readsError !== undefined}
							errorId={readsError === undefined ? undefined : `${scanFieldId("reads")}-error`}
							onChange={(reads) => setDraft({ reads })}
						/>
					) : (
						<p className="text-sm text-muted-foreground">
							This instance offers no source a scan of{" "}
							{itemKindLabel(draft.itemKind, 2).toLowerCase()} can read.
						</p>
					)}
					{readsError !== undefined && (
						<FieldError id={`${scanFieldId("reads")}-error`}>{readsError}</FieldError>
					)}
				</div>
			)}
		</div>
	);
}

import { ArrowLeft, ChevronDown, ChevronRight, RotateCcw } from "lucide-react";
import { useState } from "react";
import type {
	CreatePracticeRequest,
	Practice,
	PracticeArea,
	UpdatePracticeRequest,
} from "@/api/types.gen";
import { CodeEditor } from "@/components/shared/CodeEditor";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from "@/components/ui/select";
import { Separator } from "@/components/ui/separator";
import { Spinner } from "@/components/ui/spinner";
import { Textarea } from "@/components/ui/textarea";
import {
	FOCUS_ARTIFACT_OPTIONS,
	generateSlug,
	isValidSlug,
	TRIGGER_EVENTS_BY_FOCUS,
	triggerEventsForFocus,
	type WorkArtifact,
} from "./constants";

/** Sentinel for the "not bound to any area" option (shadcn SelectItem cannot use an empty value). */
const NO_AREA = "__none__";

interface PracticeFormCreateProps {
	mode: "create";
	/** Areas offered in the binding picker. Binding is a separate mutation the container runs after create. */
	areas: PracticeArea[];
	onSubmit: (data: CreatePracticeRequest, areaSlug: string | null) => void;
	onCancel: () => void;
	isPending: boolean;
	initialData?: never;
}

interface PracticeFormEditProps {
	mode: "edit";
	initialData: Practice;
	areas: PracticeArea[];
	onSubmit: (slug: string, data: UpdatePracticeRequest, areaSlug: string | null) => void;
	onCancel: () => void;
	isPending: boolean;
}

export type PracticeFormProps = PracticeFormCreateProps | PracticeFormEditProps;

interface FormState {
	name: string;
	slug: string;
	focusArtifact: WorkArtifact;
	areaSlug: string;
	triggerEvents: string[];
	criteria: string;
	precomputeScript: string;
}

function getInitialState(mode: "create" | "edit", initialData?: Practice): FormState {
	if (mode === "edit" && initialData) {
		return {
			name: initialData.name,
			slug: initialData.slug,
			focusArtifact: initialData.artifactType,
			areaSlug: initialData.areaSlug ?? NO_AREA,
			triggerEvents: [...initialData.triggerEvents],
			criteria: initialData.criteria,
			precomputeScript: initialData.precomputeScript ?? "",
		};
	}
	return {
		name: "",
		slug: "",
		focusArtifact: "PULL_REQUEST",
		areaSlug: NO_AREA,
		triggerEvents: [],
		criteria: "",
		precomputeScript: "",
	};
}

export function PracticeForm({
	mode,
	areas,
	onSubmit,
	onCancel,
	isPending,
	initialData,
}: PracticeFormProps) {
	const [form, setForm] = useState<FormState>(() => getInitialState(mode, initialData));
	const [submitted, setSubmitted] = useState(false);
	// Precompute is optional support — the LLM does the heavy lifting — so it stays collapsed unless the
	// practice already has a script, keeping the criteria (the product) the focus of the form.
	const [showAdvanced, setShowAdvanced] = useState(() => Boolean(initialData?.precomputeScript));

	const handleNameChange = (name: string) => {
		setForm((prev) => {
			const wasManuallyEdited = mode === "create" && prev.slug !== generateSlug(prev.name);
			return {
				...prev,
				name,
				...(!wasManuallyEdited ? { slug: generateSlug(name) } : {}),
			};
		});
	};

	const slugManuallyEdited = mode === "create" && form.slug !== generateSlug(form.name);

	const handleToggleEvent = (event: string, checked: boolean) => {
		setForm((prev) => ({
			...prev,
			triggerEvents: checked
				? [...prev.triggerEvents, event]
				: prev.triggerEvents.filter((e) => e !== event),
		}));
	};

	const nameError =
		submitted && form.name.length < 3 ? "Name must be at least 3 characters" : undefined;
	const slugError =
		submitted && mode === "create" && !isValidSlug(form.slug)
			? "Slug must be 3-64 lowercase alphanumeric characters separated by hyphens"
			: undefined;
	const triggerError =
		submitted && form.triggerEvents.length === 0 ? "Select at least one trigger event" : undefined;
	const criteriaError =
		submitted && form.criteria.trim().length < 3
			? "Criteria must be at least 3 characters"
			: undefined;

	const isValid =
		form.name.length >= 3 &&
		form.criteria.trim().length >= 3 &&
		form.triggerEvents.length > 0 &&
		(mode === "edit" || isValidSlug(form.slug));

	const handleSubmit = (e: React.FormEvent) => {
		e.preventDefault();
		setSubmitted(true);
		if (!isValid) return;

		const areaSlug = form.areaSlug === NO_AREA ? null : form.areaSlug;

		if (mode === "create") {
			const data: CreatePracticeRequest = {
				name: form.name,
				slug: form.slug,
				criteria: form.criteria.trim(),
				triggerEvents: form.triggerEvents,
				artifactType: form.focusArtifact,
				...(form.precomputeScript.trim() ? { precomputeScript: form.precomputeScript.trim() } : {}),
			};
			onSubmit(data, areaSlug);
		} else {
			const data: UpdatePracticeRequest = {
				name: form.name,
				criteria: form.criteria.trim(),
				triggerEvents: form.triggerEvents,
				artifactType: form.focusArtifact,
				precomputeScript: form.precomputeScript.trim() || undefined,
			};
			onSubmit(initialData.slug, data, areaSlug);
		}
	};

	return (
		<form onSubmit={handleSubmit} className="flex flex-col h-full">
			<div className="flex-1 overflow-y-auto pb-24">
				<div className="container mx-auto max-w-3xl py-6">
					{/* Header */}
					<div className="mb-8">
						<button
							type="button"
							onClick={onCancel}
							className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground mb-4 transition-colors"
						>
							<ArrowLeft className="h-4 w-4" />
							Back to Practices
						</button>
						<h1 className="text-3xl font-bold tracking-tight">
							{mode === "create" ? "Create Practice" : `Edit: ${initialData?.name}`}
						</h1>
						<p className="text-muted-foreground mt-1">
							{mode === "create"
								? "Define a new practice for evaluating developer contributions."
								: "Update this practice definition."}
						</p>
					</div>

					<div className="space-y-8">
						{/* Section: General */}
						<section className="space-y-4">
							<div>
								<h2 className="text-lg font-semibold">General</h2>
								<p className="text-sm text-muted-foreground">
									Basic practice information and identification.
								</p>
							</div>

							<div className="grid gap-4">
								<div className="grid gap-2">
									<Label htmlFor="practice-name">Name *</Label>
									<Input
										id="practice-name"
										placeholder="e.g. PR Description Quality"
										value={form.name}
										onChange={(e) => handleNameChange(e.target.value)}
										aria-invalid={!!nameError}
										aria-describedby={nameError ? "name-error" : undefined}
									/>
									{nameError && (
										<p id="name-error" className="text-sm text-destructive">
											{nameError}
										</p>
									)}
								</div>

								<div className="grid gap-2">
									<Label htmlFor="practice-slug">Slug {mode === "create" && "*"}</Label>
									<div className="flex items-center gap-2">
										<Input
											id="practice-slug"
											placeholder="e.g. pr-description-quality"
											value={form.slug}
											onChange={(e) => setForm((prev) => ({ ...prev, slug: e.target.value }))}
											disabled={mode === "edit"}
											aria-invalid={!!slugError}
											aria-describedby={slugError ? "slug-error" : undefined}
										/>
										{slugManuallyEdited && (
											<Button
												type="button"
												variant="ghost"
												size="icon-sm"
												onClick={() =>
													setForm((prev) => ({ ...prev, slug: generateSlug(prev.name) }))
												}
												aria-label="Reset to auto-generated slug"
											>
												<RotateCcw className="h-3.5 w-3.5" />
											</Button>
										)}
									</div>
									{mode === "edit" && (
										<p className="text-xs text-muted-foreground">
											Slug cannot be changed after creation.
										</p>
									)}
									{slugError && (
										<p id="slug-error" className="text-sm text-destructive">
											{slugError}
										</p>
									)}
								</div>

								<div className="grid gap-4 sm:grid-cols-2">
									<div className="grid gap-2">
										<Label htmlFor="practice-focus">Evaluates</Label>
										<Select
											value={form.focusArtifact}
											onValueChange={(value) =>
												setForm((prev) => {
													const focusArtifact = value as WorkArtifact;
													// Drop any selected triggers that don't belong to the new focus —
													// the server rejects cross-focus combinations.
													const allowed = triggerEventsForFocus(focusArtifact);
													return {
														...prev,
														focusArtifact,
														triggerEvents: prev.triggerEvents.filter((e) => allowed.includes(e)),
													};
												})
											}
										>
											<SelectTrigger id="practice-focus">
												<SelectValue>
													{
														FOCUS_ARTIFACT_OPTIONS.find((o) => o.value === form.focusArtifact)
															?.label
													}
												</SelectValue>
											</SelectTrigger>
											<SelectContent>
												{FOCUS_ARTIFACT_OPTIONS.map((option) => (
													<SelectItem key={option.value} value={option.value}>
														{option.label}
													</SelectItem>
												))}
											</SelectContent>
										</Select>
										<p className="text-xs text-muted-foreground">
											{FOCUS_ARTIFACT_OPTIONS.find((o) => o.value === form.focusArtifact)?.hint}
										</p>
									</div>

									<div className="grid gap-2">
										<Label htmlFor="practice-area">Area</Label>
										<Select
											value={form.areaSlug}
											onValueChange={(value) =>
												setForm((prev) => ({ ...prev, areaSlug: value ?? NO_AREA }))
											}
										>
											<SelectTrigger id="practice-area">
												<SelectValue placeholder="Not assigned">
													{form.areaSlug === NO_AREA
														? undefined
														: areas.find((g) => g.slug === form.areaSlug)?.name}
												</SelectValue>
											</SelectTrigger>
											<SelectContent>
												<SelectItem value={NO_AREA}>Not assigned</SelectItem>
												{areas.map((area) => (
													<SelectItem key={area.slug} value={area.slug}>
														{area.name}
													</SelectItem>
												))}
											</SelectContent>
										</Select>
										<p className="text-xs text-muted-foreground">
											The learning objective this practice rolls up to.
										</p>
									</div>
								</div>
							</div>
						</section>

						<Separator />

						{/* Section: Trigger Events */}
						<section className="space-y-4">
							<fieldset
								className="space-y-4"
								aria-invalid={!!triggerError}
								aria-describedby={triggerError ? "trigger-error" : undefined}
							>
								<div>
									<legend className="text-lg font-semibold">Run this practice when… *</legend>
									<p className="text-sm text-muted-foreground">
										Pick the {form.focusArtifact === "ISSUE" ? "issue" : "pull request"} activity
										that should trigger an evaluation.
									</p>
								</div>
								<div className="grid grid-cols-2 gap-3">
									{TRIGGER_EVENTS_BY_FOCUS[form.focusArtifact].map((option) => (
										<Label
											key={option.value}
											htmlFor={`trigger-${option.value}`}
											className="flex items-center gap-2 text-sm font-normal cursor-pointer"
										>
											<Checkbox
												id={`trigger-${option.value}`}
												checked={form.triggerEvents.includes(option.value)}
												onCheckedChange={(checked) =>
													handleToggleEvent(option.value, checked === true)
												}
											/>
											{option.label}
										</Label>
									))}
								</div>
								{triggerError && (
									<p id="trigger-error" className="text-sm text-destructive">
										{triggerError}
									</p>
								)}
							</fieldset>
						</section>

						<Separator />

						{/* Section: Evaluation Criteria */}
						<section className="space-y-4">
							<div>
								<h2 className="text-lg font-semibold">Evaluation Criteria *</h2>
								<p className="text-sm text-muted-foreground">
									Markdown-formatted criteria used by the AI agent during code review. This is the
									single source of truth for what this practice evaluates.
								</p>
							</div>
							<Textarea
								id="practice-criteria"
								placeholder="## Practice Name&#10;&#10;Describe what to evaluate, required elements, and anti-patterns..."
								value={form.criteria}
								onChange={(e) => setForm((prev) => ({ ...prev, criteria: e.target.value }))}
								className="min-h-64 font-mono text-sm"
								aria-invalid={!!criteriaError}
								aria-describedby={criteriaError ? "criteria-error" : undefined}
							/>
							{criteriaError && (
								<p id="criteria-error" className="text-sm text-destructive">
									{criteriaError}
								</p>
							)}
						</section>

						<Separator />

						{/* Section: Advanced — precompute script (optional support; the LLM does the heavy lifting) */}
						<section className="space-y-4">
							<button
								type="button"
								onClick={() => setShowAdvanced((open) => !open)}
								className="flex items-center gap-1.5 text-lg font-semibold"
								aria-expanded={showAdvanced}
							>
								{showAdvanced ? (
									<ChevronDown className="h-4 w-4" />
								) : (
									<ChevronRight className="h-4 w-4" />
								)}
								Advanced
								<span className="text-sm font-normal text-muted-foreground">
									— precompute script (optional)
								</span>
							</button>
							{showAdvanced && (
								<>
									<p className="text-sm text-muted-foreground">
										An optional TypeScript/Bun script that runs static analysis before the AI review
										and feeds it structured hints. Most practices need none — the AI evaluates the
										criteria directly.
									</p>
									<CodeEditor
										value={form.precomputeScript}
										onChange={(val) => setForm((prev) => ({ ...prev, precomputeScript: val }))}
										language="typescript"
										className="h-[400px]"
									/>
								</>
							)}
						</section>
					</div>
				</div>
			</div>

			{/* Sticky footer */}
			<div className="sticky bottom-0 border-t bg-background px-6 py-4 z-10">
				<div className="container mx-auto max-w-3xl flex justify-between">
					<Button type="button" variant="outline" onClick={onCancel}>
						Cancel
					</Button>
					<Button type="submit" disabled={isPending}>
						{isPending ? (
							<>
								<Spinner className="mr-2 h-4 w-4" />
								{mode === "create" ? "Creating..." : "Saving..."}
							</>
						) : mode === "create" ? (
							"Create Practice"
						) : (
							"Save Changes"
						)}
					</Button>
				</div>
			</div>
		</form>
	);
}

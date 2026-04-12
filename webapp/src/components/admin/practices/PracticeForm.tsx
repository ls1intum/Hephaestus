import { ArrowLeft, RotateCcw } from "lucide-react";
import { useState } from "react";
import type { CreatePracticeRequest, Practice, UpdatePracticeRequest } from "@/api/types.gen";
import { CodeEditor } from "@/components/shared/CodeEditor";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Separator } from "@/components/ui/separator";
import { Spinner } from "@/components/ui/spinner";
import { Textarea } from "@/components/ui/textarea";
import { generateSlug, isValidSlug, TRIGGER_EVENT_OPTIONS } from "./constants";

interface PracticeFormCreateProps {
	mode: "create";
	onSubmit: (data: CreatePracticeRequest) => void;
	onCancel: () => void;
	isPending: boolean;
	initialData?: never;
}

interface PracticeFormEditProps {
	mode: "edit";
	initialData: Practice;
	onSubmit: (slug: string, data: UpdatePracticeRequest) => void;
	onCancel: () => void;
	isPending: boolean;
}

export type PracticeFormProps = PracticeFormCreateProps | PracticeFormEditProps;

interface FormState {
	name: string;
	slug: string;
	category: string;
	description: string;
	triggerEvents: string[];
	criteria: string;
	precomputeScript: string;
}

function getInitialState(mode: "create" | "edit", initialData?: Practice): FormState {
	if (mode === "edit" && initialData) {
		return {
			name: initialData.name,
			slug: initialData.slug,
			category: initialData.category ?? "",
			description: initialData.description,
			triggerEvents: [...initialData.triggerEvents],
			criteria: initialData.criteria ?? "",
			precomputeScript: initialData.precomputeScript ?? "",
		};
	}
	return {
		name: "",
		slug: "",
		category: "",
		description: "",
		triggerEvents: [],
		criteria: "",
		precomputeScript: "",
	};
}

export function PracticeForm({
	mode,
	onSubmit,
	onCancel,
	isPending,
	initialData,
}: PracticeFormProps) {
	const [form, setForm] = useState<FormState>(() => getInitialState(mode, initialData));
	const [submitted, setSubmitted] = useState(false);

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
	const descriptionError =
		submitted && form.description.length < 3
			? "Description must be at least 3 characters"
			: undefined;

	const isValid =
		form.name.length >= 3 &&
		form.description.length >= 3 &&
		form.triggerEvents.length > 0 &&
		(mode === "edit" || isValidSlug(form.slug));

	const handleSubmit = (e: React.FormEvent) => {
		e.preventDefault();
		setSubmitted(true);
		if (!isValid) return;

		if (mode === "create") {
			const data: CreatePracticeRequest = {
				name: form.name,
				slug: form.slug,
				description: form.description,
				triggerEvents: form.triggerEvents,
				...(form.category.trim() ? { category: form.category.trim() } : {}),
				...(form.criteria.trim() ? { criteria: form.criteria.trim() } : {}),
				...(form.precomputeScript.trim() ? { precomputeScript: form.precomputeScript.trim() } : {}),
			};
			onSubmit(data);
		} else {
			const data: UpdatePracticeRequest = {
				name: form.name,
				description: form.description,
				triggerEvents: form.triggerEvents,
				category: form.category.trim() || undefined,
				criteria: form.criteria.trim() || undefined,
				precomputeScript: form.precomputeScript.trim() || undefined,
			};
			onSubmit(initialData.slug, data);
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

								<div className="grid gap-2">
									<Label htmlFor="practice-category">Category</Label>
									<Input
										id="practice-category"
										placeholder="e.g. code-quality"
										value={form.category}
										onChange={(e) => setForm((prev) => ({ ...prev, category: e.target.value }))}
										maxLength={64}
									/>
								</div>

								<div className="grid gap-2">
									<Label htmlFor="practice-description">Description *</Label>
									<Textarea
										id="practice-description"
										placeholder="Describe what this practice evaluates..."
										value={form.description}
										onChange={(e) => setForm((prev) => ({ ...prev, description: e.target.value }))}
										className="min-h-20"
										aria-invalid={!!descriptionError}
										aria-describedby={descriptionError ? "description-error" : undefined}
									/>
									{descriptionError && (
										<p id="description-error" className="text-sm text-destructive">
											{descriptionError}
										</p>
									)}
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
									<legend className="text-lg font-semibold">Trigger Events *</legend>
									<p className="text-sm text-muted-foreground">
										Domain events that trigger this practice's evaluation.
									</p>
								</div>
								<div className="grid grid-cols-2 gap-3">
									{TRIGGER_EVENT_OPTIONS.map((option) => (
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
								<h2 className="text-lg font-semibold">Evaluation Criteria</h2>
								<p className="text-sm text-muted-foreground">
									Markdown-formatted criteria used by the AI agent during code review.
								</p>
							</div>
							<Textarea
								id="practice-criteria"
								placeholder="Evaluation criteria in markdown..."
								value={form.criteria}
								onChange={(e) => setForm((prev) => ({ ...prev, criteria: e.target.value }))}
								className="min-h-48 font-mono text-sm"
							/>
						</section>

						<Separator />

						{/* Section: Precompute Script */}
						<section className="space-y-4">
							<div>
								<h2 className="text-lg font-semibold">Precompute Script</h2>
								<p className="text-sm text-muted-foreground">
									TypeScript/Bun script that runs static analysis before the AI review. Produces
									structured hints from diff and file inspection.
								</p>
							</div>
							<CodeEditor
								value={form.precomputeScript}
								onChange={(val) => setForm((prev) => ({ ...prev, precomputeScript: val }))}
								language="typescript"
								className="h-[400px]"
							/>
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

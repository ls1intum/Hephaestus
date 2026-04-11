import { RotateCcw } from "lucide-react";
import { useEffect, useState } from "react";
import type { CreatePracticeRequest, Practice, UpdatePracticeRequest } from "@/api/types.gen";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { CodeEditor } from "@/components/ui/code-editor";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Separator } from "@/components/ui/separator";
import {
	Sheet,
	SheetContent,
	SheetDescription,
	SheetFooter,
	SheetHeader,
	SheetTitle,
} from "@/components/ui/sheet";
import { Spinner } from "@/components/ui/spinner";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Textarea } from "@/components/ui/textarea";
import { generateSlug, isValidSlug, TRIGGER_EVENT_OPTIONS } from "./constants";

interface PracticeFormSheetBaseProps {
	open: boolean;
	onOpenChange: (open: boolean) => void;
	isPending: boolean;
}

interface PracticeFormSheetCreateProps extends PracticeFormSheetBaseProps {
	mode: "create";
	onSubmit: (data: CreatePracticeRequest) => void;
	initialData?: never;
}

interface PracticeFormSheetEditProps extends PracticeFormSheetBaseProps {
	mode: "edit";
	onSubmit: (slug: string, data: UpdatePracticeRequest) => void;
	initialData: Practice;
}

export type PracticeFormSheetProps = PracticeFormSheetCreateProps | PracticeFormSheetEditProps;

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

export function PracticeFormSheet({
	mode,
	open,
	onOpenChange,
	onSubmit,
	isPending,
	initialData,
}: PracticeFormSheetProps) {
	const [form, setForm] = useState<FormState>(() => getInitialState(mode, initialData));
	const [submitted, setSubmitted] = useState(false);

	useEffect(() => {
		if (open) {
			setForm(getInitialState(mode, initialData));
			setSubmitted(false);
		}
	}, [open, mode, initialData]);

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

	// All validation errors gated behind `submitted` to avoid showing errors while typing
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
		<Sheet open={open} onOpenChange={onOpenChange}>
			<SheetContent side="right" className="sm:max-w-2xl w-full flex flex-col">
				<SheetHeader>
					<SheetTitle>{mode === "create" ? "Create Practice" : "Edit Practice"}</SheetTitle>
					<SheetDescription>
						{mode === "create"
							? "Define a new practice for evaluating developer contributions."
							: "Update this practice definition."}
					</SheetDescription>
				</SheetHeader>

				<form onSubmit={handleSubmit} className="flex flex-col flex-1 min-h-0">
					<Tabs defaultValue="general" className="flex flex-col flex-1 min-h-0">
						<div className="px-4">
							<TabsList>
								<TabsTrigger value="general">General</TabsTrigger>
								<TabsTrigger value="precompute">Precompute Script</TabsTrigger>
							</TabsList>
						</div>

						<TabsContent value="general" className="flex-1 overflow-y-auto min-h-0">
							<div className="grid gap-4 px-4 pb-4">
								{/* Name */}
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

								{/* Slug */}
								<div className="grid gap-2">
									<Label htmlFor="practice-slug">Slug {mode === "create" && "*"}</Label>
									<div className="flex items-center gap-2">
										<Input
											id="practice-slug"
											placeholder="e.g. pr-description-quality"
											value={form.slug}
											onChange={(e) => {
												setForm((prev) => ({ ...prev, slug: e.target.value }));
											}}
											disabled={mode === "edit"}
											aria-invalid={!!slugError}
											aria-describedby={slugError ? "slug-error" : undefined}
										/>
										{slugManuallyEdited && (
											<Button
												type="button"
												variant="ghost"
												size="icon-sm"
												onClick={() => {
													setForm((prev) => ({ ...prev, slug: generateSlug(prev.name) }));
												}}
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

								{/* Category */}
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

								{/* Description */}
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

								<Separator />

								{/* Trigger Events */}
								<fieldset
									className="grid gap-2"
									aria-invalid={!!triggerError}
									aria-describedby={triggerError ? "trigger-error" : undefined}
								>
									<legend className="text-sm font-medium leading-none mb-1">
										Trigger Events *
									</legend>
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

								{/* Criteria */}
								<div className="grid gap-2">
									<Label htmlFor="practice-criteria">Criteria</Label>
									<Textarea
										id="practice-criteria"
										placeholder="Evaluation criteria in markdown that the AI agent uses to assess this practice..."
										value={form.criteria}
										onChange={(e) => setForm((prev) => ({ ...prev, criteria: e.target.value }))}
										className="min-h-48 font-mono text-sm"
									/>
									<p className="text-xs text-muted-foreground">
										Markdown-formatted evaluation criteria used by the AI agent during code review.
									</p>
								</div>
							</div>
						</TabsContent>

						<TabsContent value="precompute" className="flex-1 flex flex-col min-h-0">
							<div className="grid gap-3 px-4 pb-4 flex-1 min-h-0">
								<div className="flex flex-col gap-2 min-h-0 flex-1">
									<Label>Precompute Script</Label>
									<p className="text-sm text-muted-foreground">
										TypeScript/Bun script that runs static analysis before the AI review. Produces
										structured hints from diff and file inspection.
									</p>
									<CodeEditor
										value={form.precomputeScript}
										onChange={(val) => setForm((prev) => ({ ...prev, precomputeScript: val }))}
										language="typescript"
										className="flex-1 min-h-[300px]"
									/>
								</div>
							</div>
						</TabsContent>
					</Tabs>

					<SheetFooter className="border-t px-4 py-3">
						<Button type="submit" disabled={isPending} className="w-full">
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
					</SheetFooter>
				</form>
			</SheetContent>
		</Sheet>
	);
}

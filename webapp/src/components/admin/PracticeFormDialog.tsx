import { Pencil } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import type { CreatePracticeRequest, Practice, UpdatePracticeRequest } from "@/api/types.gen";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
	Dialog,
	DialogContent,
	DialogDescription,
	DialogFooter,
	DialogHeader,
	DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Spinner } from "@/components/ui/spinner";
import { Textarea } from "@/components/ui/textarea";
import { generateSlug, isValidSlug, TRIGGER_EVENT_OPTIONS } from "./practice-constants";

interface PracticeFormDialogBaseProps {
	open: boolean;
	onOpenChange: (open: boolean) => void;
	isPending: boolean;
}

interface PracticeFormDialogCreateProps extends PracticeFormDialogBaseProps {
	mode: "create";
	onSubmit: (data: CreatePracticeRequest) => void;
	initialData?: never;
}

interface PracticeFormDialogEditProps extends PracticeFormDialogBaseProps {
	mode: "edit";
	onSubmit: (slug: string, data: UpdatePracticeRequest) => void;
	initialData: Practice;
}

export type PracticeFormDialogProps = PracticeFormDialogCreateProps | PracticeFormDialogEditProps;

interface FormState {
	name: string;
	slug: string;
	category: string;
	description: string;
	triggerEvents: string[];
	detectionPrompt: string;
}

function getInitialState(mode: "create" | "edit", initialData?: Practice): FormState {
	if (mode === "edit" && initialData) {
		return {
			name: initialData.name,
			slug: initialData.slug,
			category: initialData.category ?? "",
			description: initialData.description,
			triggerEvents: [...initialData.triggerEvents],
			detectionPrompt: initialData.detectionPrompt ?? "",
		};
	}
	return {
		name: "",
		slug: "",
		category: "",
		description: "",
		triggerEvents: [],
		detectionPrompt: "",
	};
}

export function PracticeFormDialog({
	mode,
	open,
	onOpenChange,
	onSubmit,
	isPending,
	initialData,
}: PracticeFormDialogProps) {
	const [form, setForm] = useState<FormState>(() => getInitialState(mode, initialData));
	const slugManuallyEdited = useRef(false);

	// Reset form when dialog opens/closes or initialData changes
	useEffect(() => {
		if (open) {
			setForm(getInitialState(mode, initialData));
			slugManuallyEdited.current = false;
		}
	}, [open, mode, initialData]);

	const handleNameChange = (name: string) => {
		setForm((prev) => ({
			...prev,
			name,
			...(mode === "create" && !slugManuallyEdited.current ? { slug: generateSlug(name) } : {}),
		}));
	};

	const handleToggleEvent = (event: string, checked: boolean) => {
		setForm((prev) => ({
			...prev,
			triggerEvents: checked
				? [...prev.triggerEvents, event]
				: prev.triggerEvents.filter((e) => e !== event),
		}));
	};

	const nameError =
		form.name.length > 0 && form.name.length < 3 ? "Name must be at least 3 characters" : undefined;
	const slugError =
		mode === "create" && form.slug.length > 0 && !isValidSlug(form.slug)
			? "Slug must be 3-64 lowercase alphanumeric characters separated by hyphens"
			: undefined;
	const triggerError =
		form.triggerEvents.length === 0 ? "Select at least one trigger event" : undefined;
	const descriptionError =
		form.description.length > 0 && form.description.length < 3
			? "Description must be at least 3 characters"
			: undefined;

	const isValid =
		form.name.length >= 3 &&
		form.description.length >= 3 &&
		form.triggerEvents.length > 0 &&
		(mode === "edit" || isValidSlug(form.slug));

	const handleSubmit = (e: React.FormEvent) => {
		e.preventDefault();
		if (!isValid) return;

		if (mode === "create") {
			const data: CreatePracticeRequest = {
				name: form.name,
				slug: form.slug,
				description: form.description,
				triggerEvents: form.triggerEvents,
				...(form.category && { category: form.category }),
				...(form.detectionPrompt && { detectionPrompt: form.detectionPrompt }),
			};
			onSubmit(data);
		} else {
			const data: UpdatePracticeRequest = {
				name: form.name,
				description: form.description,
				triggerEvents: form.triggerEvents,
				category: form.category || undefined,
				detectionPrompt: form.detectionPrompt || undefined,
			};
			onSubmit(initialData.slug, data);
		}
	};

	return (
		<Dialog open={open} onOpenChange={onOpenChange}>
			<DialogContent className="sm:max-w-lg max-h-[85vh] flex flex-col gap-0">
				<form onSubmit={handleSubmit} className="flex flex-col min-h-0 gap-0">
					<DialogHeader>
						<DialogTitle>{mode === "create" ? "Create Practice" : "Edit Practice"}</DialogTitle>
						<DialogDescription>
							{mode === "create"
								? "Define a new practice for evaluating developer contributions."
								: "Update this practice definition."}
						</DialogDescription>
					</DialogHeader>

					<div className="flex-1 overflow-y-auto min-h-0">
						<div className="grid gap-4 py-4">
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
								<Label htmlFor="practice-slug">Slug *</Label>
								<div className="flex items-center gap-2">
									<Input
										id="practice-slug"
										placeholder="e.g. pr-description-quality"
										value={form.slug}
										onChange={(e) => {
											slugManuallyEdited.current = true;
											setForm((prev) => ({ ...prev, slug: e.target.value }));
										}}
										disabled={mode === "edit"}
										aria-invalid={!!slugError}
										aria-describedby={slugError ? "slug-error" : undefined}
									/>
									{mode === "create" && slugManuallyEdited.current && (
										<Button
											type="button"
											variant="ghost"
											size="icon-sm"
											onClick={() => {
												slugManuallyEdited.current = false;
												setForm((prev) => ({ ...prev, slug: generateSlug(prev.name) }));
											}}
											aria-label="Reset to auto-generated slug"
										>
											<Pencil className="h-3.5 w-3.5" />
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

							{/* Trigger Events */}
							<fieldset
								className="grid gap-2"
								aria-invalid={!!(triggerError && form.name.length > 0)}
								aria-describedby={
									triggerError && form.name.length > 0 ? "trigger-error" : undefined
								}
							>
								<legend className="text-sm font-medium leading-none mb-1">Trigger Events *</legend>
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
								{triggerError && form.name.length > 0 && (
									<p id="trigger-error" className="text-sm text-destructive">
										{triggerError}
									</p>
								)}
							</fieldset>

							{/* Detection Prompt */}
							<div className="grid gap-2">
								<Label htmlFor="practice-detection-prompt">Detection Prompt</Label>
								<Textarea
									id="practice-detection-prompt"
									placeholder="AI prompt template for detecting this practice..."
									value={form.detectionPrompt}
									onChange={(e) =>
										setForm((prev) => ({ ...prev, detectionPrompt: e.target.value }))
									}
									className="min-h-24"
								/>
								<p className="text-xs text-muted-foreground">
									Optional. The prompt template used by the AI agent to detect this practice.
								</p>
							</div>
						</div>
					</div>

					<DialogFooter>
						<Button type="submit" disabled={!isValid || isPending}>
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
					</DialogFooter>
				</form>
			</DialogContent>
		</Dialog>
	);
}

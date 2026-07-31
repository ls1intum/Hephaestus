import { Link } from "@tanstack/react-router";
import { ArrowLeft, ChevronRight, ClipboardPenLine, ListPlus, RotateCcw } from "lucide-react";
import { useState } from "react";
import type {
	CreatePracticeRequest,
	Practice,
	PracticeArea,
	UpdatePracticeRequest,
} from "@/api/types.gen";
import { PageHeader } from "@/components/core/PageHeader";
import { PageLayout } from "@/components/core/PageLayout";
import { CodeEditor } from "@/components/shared/CodeEditor";
import { Button, buttonVariants } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
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
import { cn } from "@/lib/utils";
import {
	FOCUS_ARTIFACT_OPTIONS,
	generateSlug,
	isValidSlug,
	TRIGGER_EVENTS_BY_FOCUS,
	triggerEventsForFocus,
	type WorkArtifact,
} from "./constants";

const NO_AREA = "__none__";

interface PracticeFormCreateProps {
	mode: "create";
	workspaceSlug: string;
	areas: PracticeArea[];
	onSubmit: (data: CreatePracticeRequest, areaSlug: string | null) => void;
	isPending: boolean;
	initialData?: never;
}

interface PracticeFormEditProps {
	mode: "edit";
	workspaceSlug: string;
	initialData: Practice;
	areas: PracticeArea[];
	onSubmit: (slug: string, data: UpdatePracticeRequest, areaSlug: string | null) => void;
	isPending: boolean;
}

export type PracticeFormProps = PracticeFormCreateProps | PracticeFormEditProps;

interface PracticeFormShellProps {
	mode: "create" | "edit";
	workspaceSlug: string;
	practiceName?: string;
	children: React.ReactNode;
}

interface FormState {
	name: string;
	slug: string;
	focusArtifact: WorkArtifact;
	areaSlug: string;
	triggerEvents: string[];
	criteria: string;
	whyItMatters: string;
	whatGoodLooksLike: string;
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
			whyItMatters: initialData.whyItMatters ?? "",
			whatGoodLooksLike: initialData.whatGoodLooksLike ?? "",
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
		whyItMatters: "",
		whatGoodLooksLike: "",
		precomputeScript: "",
	};
}

export function PracticeForm({
	mode,
	workspaceSlug,
	areas,
	onSubmit,
	isPending,
	initialData,
}: PracticeFormProps) {
	const [form, setForm] = useState<FormState>(() => getInitialState(mode, initialData));
	const [submitted, setSubmitted] = useState(false);
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
		submitted && form.name.trim().length < 3 ? "Name must be at least 3 characters" : undefined;
	const slugError =
		submitted && mode === "create" && !isValidSlug(form.slug)
			? "Slug must be 3-64 lowercase alphanumeric characters separated by hyphens"
			: undefined;
	const triggerError =
		submitted && form.focusArtifact !== "CONVERSATION_THREAD" && form.triggerEvents.length === 0
			? "Select at least one trigger event"
			: undefined;
	const criteriaError =
		submitted && form.criteria.trim().length < 3
			? "Criteria must be at least 3 characters"
			: undefined;

	const isValid =
		form.name.trim().length >= 3 &&
		form.criteria.trim().length >= 3 &&
		(form.focusArtifact === "CONVERSATION_THREAD" || form.triggerEvents.length > 0) &&
		(mode === "edit" || isValidSlug(form.slug));

	const handleSubmit = (e: React.FormEvent) => {
		e.preventDefault();
		setSubmitted(true);
		if (!isValid) return;

		const name = form.name.trim();
		const areaSlug = form.areaSlug === NO_AREA ? null : form.areaSlug;

		if (mode === "create") {
			const data: CreatePracticeRequest = {
				name,
				slug: form.slug,
				criteria: form.criteria.trim(),
				triggerEvents: form.triggerEvents,
				artifactType: form.focusArtifact,
				...(form.whyItMatters.trim() ? { whyItMatters: form.whyItMatters.trim() } : {}),
				...(form.whatGoodLooksLike.trim()
					? { whatGoodLooksLike: form.whatGoodLooksLike.trim() }
					: {}),
				...(form.precomputeScript.trim() ? { precomputeScript: form.precomputeScript.trim() } : {}),
			};
			onSubmit(data, areaSlug);
		} else {
			const clear: NonNullable<UpdatePracticeRequest["clear"]> = [];
			if (!form.precomputeScript.trim()) clear.push("PRECOMPUTE_SCRIPT");
			if (!form.whyItMatters.trim()) clear.push("WHY_IT_MATTERS");
			if (!form.whatGoodLooksLike.trim()) clear.push("WHAT_GOOD_LOOKS_LIKE");
			const data: UpdatePracticeRequest = {
				name,
				criteria: form.criteria.trim(),
				triggerEvents: form.triggerEvents,
				artifactType: form.focusArtifact,
				whyItMatters: form.whyItMatters.trim() || undefined,
				whatGoodLooksLike: form.whatGoodLooksLike.trim() || undefined,
				precomputeScript: form.precomputeScript.trim() || undefined,
				clear: clear.length > 0 ? clear : undefined,
			};
			onSubmit(initialData.slug, data, areaSlug);
		}
	};

	return (
		<PracticeFormShell mode={mode} workspaceSlug={workspaceSlug} practiceName={initialData?.name}>
			<form onSubmit={handleSubmit} noValidate className="flex flex-col gap-8">
				<div className="max-w-3xl">
					<p className="mb-8 text-sm text-muted-foreground">
						Fields marked <span aria-hidden>*</span> are required.
					</p>
					<div className="space-y-8">
						<section className="space-y-4">
							<h2 className="text-lg font-semibold">General</h2>

							<div className="grid gap-4">
								<div className="grid gap-2">
									<Label htmlFor="practice-name">Name *</Label>
									<Input
										id="practice-name"
										placeholder="e.g. PR Description Quality"
										value={form.name}
										onChange={(e) => handleNameChange(e.target.value)}
										required
										minLength={3}
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
											required={mode === "create"}
											minLength={3}
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
												<RotateCcw className="size-3.5" />
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
										<Label htmlFor="practice-area">Practice area</Label>
										<Select
											value={form.areaSlug}
											onValueChange={(value) =>
												setForm((prev) => ({ ...prev, areaSlug: value ?? NO_AREA }))
											}
										>
											<SelectTrigger id="practice-area">
												<SelectValue placeholder="Unassigned">
													{form.areaSlug === NO_AREA
														? undefined
														: areas.find((g) => g.slug === form.areaSlug)?.name}
												</SelectValue>
											</SelectTrigger>
											<SelectContent>
												<SelectItem value={NO_AREA}>Unassigned</SelectItem>
												{areas.map((area) => (
													<SelectItem key={area.slug} value={area.slug}>
														{area.name}
													</SelectItem>
												))}
											</SelectContent>
										</Select>
										<p className="text-xs text-muted-foreground">
											Group this practice under an area.
										</p>
									</div>
								</div>
							</div>
						</section>

						<Separator />

						{form.focusArtifact !== "CONVERSATION_THREAD" && (
							<>
								<section className="space-y-4">
									<fieldset
										className="space-y-4"
										aria-invalid={!!triggerError}
										aria-describedby={triggerError ? "trigger-error" : undefined}
									>
										<legend className="text-lg font-semibold">Start a review when… *</legend>
										<p className="text-sm text-muted-foreground">Choose one or more events.</p>
										<div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
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
							</>
						)}

						<section className="space-y-4">
							<div className="space-y-1">
								<h2>
									<Label htmlFor="practice-criteria" className="text-lg font-semibold">
										Evaluation criteria *
									</Label>
								</h2>
								<p className="text-sm text-muted-foreground">
									Instructions Hephaestus uses to assess this practice. Supports Markdown.
								</p>
							</div>
							<Textarea
								id="practice-criteria"
								placeholder="## Practice Name&#10;&#10;Describe what to evaluate, required elements, and anti-patterns..."
								value={form.criteria}
								onChange={(e) => setForm((prev) => ({ ...prev, criteria: e.target.value }))}
								className="min-h-64 font-mono text-sm"
								required
								minLength={3}
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

						<section className="space-y-4">
							<div>
								<h2 className="text-lg font-semibold">Developer guidance</h2>
								<p className="text-sm text-muted-foreground">
									Optional guidance shown to developers. It does not affect the assessment.
								</p>
							</div>

							<div className="grid gap-2">
								<Label htmlFor="practice-why-it-matters">Why it matters</Label>
								<Textarea
									id="practice-why-it-matters"
									placeholder="Explain why this practice is worth caring about…"
									value={form.whyItMatters}
									onChange={(e) => setForm((prev) => ({ ...prev, whyItMatters: e.target.value }))}
									className="min-h-24"
								/>
							</div>

							<div className="grid gap-2">
								<Label htmlFor="practice-what-good-looks-like">What good looks like</Label>
								<Textarea
									id="practice-what-good-looks-like"
									placeholder="Describe a concrete example of doing this well…"
									value={form.whatGoodLooksLike}
									onChange={(e) =>
										setForm((prev) => ({ ...prev, whatGoodLooksLike: e.target.value }))
									}
									className="min-h-24"
								/>
							</div>
						</section>

						<Separator />

						<Collapsible open={showAdvanced} onOpenChange={setShowAdvanced}>
							<CollapsibleTrigger
								render={
									<Button
										type="button"
										variant="ghost"
										className="group -ml-3 text-lg font-semibold"
									/>
								}
							>
								<ChevronRight className="size-4 transition-transform group-aria-expanded:rotate-90" />
								Precompute script
							</CollapsibleTrigger>
							<CollapsibleContent className="mt-4 space-y-4">
								<p className="text-sm text-muted-foreground">
									Optional TypeScript that runs static analysis before a review and provides
									structured context.
								</p>
								<CodeEditor
									value={form.precomputeScript}
									onChange={(val) => setForm((prev) => ({ ...prev, precomputeScript: val }))}
									language="typescript"
									className="h-[400px]"
								/>
							</CollapsibleContent>
						</Collapsible>

						{mode === "edit" && (
							<>
								<Separator />
								<section className="space-y-4">
									<div>
										<h2 className="text-lg font-semibold">Review results</h2>
										<p className="text-sm text-muted-foreground">
											View every finding this practice produced across the workspace.
										</p>
									</div>
									<Link
										to="/w/$workspaceSlug/admin/practices/reviews/findings"
										params={{ workspaceSlug }}
										search={{ practiceSlug: [form.slug] }}
										className={cn(buttonVariants({ variant: "outline" }), "w-full sm:w-auto")}
									>
										View findings
									</Link>
								</section>
							</>
						)}
					</div>
				</div>

				<div className="max-w-3xl border-t pt-4">
					<div className="flex justify-between">
						<Link
							to="/w/$workspaceSlug/admin/practices"
							params={{ workspaceSlug }}
							search={(previous) => previous}
							className={buttonVariants({ variant: "outline" })}
						>
							Cancel
						</Link>
						<Button type="submit" disabled={isPending}>
							{isPending ? (
								<>
									<Spinner className="mr-2 size-4" />
									{mode === "create" ? "Creating…" : "Saving…"}
								</>
							) : mode === "create" ? (
								"Create practice"
							) : (
								"Save changes"
							)}
						</Button>
					</div>
				</div>
			</form>
		</PracticeFormShell>
	);
}

export function PracticeFormShell({
	mode,
	workspaceSlug,
	practiceName,
	children,
}: PracticeFormShellProps) {
	return (
		<PageLayout>
			<Link
				to="/w/$workspaceSlug/admin/practices"
				params={{ workspaceSlug }}
				search={(previous) => previous}
				className={cn(buttonVariants({ variant: "ghost", size: "sm" }), "-ml-3 w-fit")}
			>
				<ArrowLeft className="size-4" />
				Practice catalog
			</Link>
			<PageHeader
				icon={mode === "create" ? <ListPlus /> : <ClipboardPenLine />}
				title={mode === "create" ? "Create practice" : `Edit: ${practiceName ?? "practice"}`}
				description={
					mode === "create"
						? "Define what Hephaestus should look for in reviewed work."
						: "Update how this practice evaluates reviewed work."
				}
			/>
			{children}
		</PageLayout>
	);
}

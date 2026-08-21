import { Link } from "@tanstack/react-router";
import deepEqual from "fast-deep-equal";
import { ArrowLeft, ClipboardPenLine, ListPlus, RotateCcw } from "lucide-react";
import { useState } from "react";
import type { CatalogEntryStatus, CuratedAreaRequest } from "@/api/types.gen";
import { AreaVisualPicker } from "@/components/admin/practice-catalog/AreaVisualPicker";
import { generateSlug, isValidSlug } from "@/components/admin/practice-catalog/constants";
import { FormActionBar } from "@/components/common/FormActionBar";
import { type FormError, FormErrorSummary } from "@/components/common/FormErrorSummary";
import { PageHeader } from "@/components/core/PageHeader";
import { PageLayout } from "@/components/core/PageLayout";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import {
	AlertDialog,
	AlertDialogAction,
	AlertDialogCancel,
	AlertDialogContent,
	AlertDialogDescription,
	AlertDialogFooter,
	AlertDialogHeader,
	AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Button, buttonVariants } from "@/components/ui/button";
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Spinner } from "@/components/ui/spinner";
import { Textarea } from "@/components/ui/textarea";
import { useUnsavedChanges } from "@/hooks/use-unsaved-changes";
import { cn } from "@/lib/utils";
import { canUseHephaestusVersion } from "./curated-entry-state";
import { HephaestusVersionPanel } from "./HephaestusVersionPanel";

export interface CuratedAreaFormValue {
	slug: string;
	name: string;
	description?: string;
	icon?: string;
	color?: string;
}

export interface CuratedAreaFormInitialValue extends CuratedAreaFormValue {
	status: CatalogEntryStatus;
	shipped?: CuratedAreaRequest;
}

interface FormState {
	slug: string;
	name: string;
	description: string;
	icon: string | null;
	color: string | null;
}

interface CuratedAreaFormBaseProps {
	isPending: boolean;
	conflict?: boolean;
	onContinueWithDraft?: () => void;
	isResetPending?: boolean;
	isKeepPending?: boolean;
	onUseHephaestusVersion?: () => void;
	onKeepCurrentDefinition?: () => void;
	onSubmit: (value: CuratedAreaFormValue) => void;
}

export type CuratedAreaFormProps = CuratedAreaFormBaseProps &
	(
		| { mode: "create"; initialData?: never }
		| { mode: "edit"; initialData: CuratedAreaFormInitialValue }
	);

function initialState(initialData?: CuratedAreaFormValue): FormState {
	return {
		slug: initialData?.slug ?? "",
		name: initialData?.name ?? "",
		description: initialData?.description ?? "",
		icon: initialData?.icon ?? null,
		color: initialData?.color ?? null,
	};
}

export function CuratedAreaForm(props: CuratedAreaFormProps) {
	const {
		mode,
		isPending,
		conflict,
		onContinueWithDraft,
		isResetPending = false,
		isKeepPending = false,
		onUseHephaestusVersion,
		initialData,
		onKeepCurrentDefinition,
		onSubmit,
	} = props;
	const [resetOpen, setResetOpen] = useState(false);
	const [form, setForm] = useState<FormState>(() => initialState(initialData));
	const [submitted, setSubmitted] = useState(false);
	const formDisabled = isPending || isResetPending || isKeepPending;
	// `deepEqual`, not `JSON.stringify`: the latter reports a difference between two equal objects
	// whose keys happen to have been inserted in a different order, which armed the guard for edits
	// the reader never made.
	const unsavedChanges = useUnsavedChanges({
		isDirty: !deepEqual(form, initialState(initialData)),
		disabled: formDisabled,
	});

	const slugWasEdited = mode === "create" && form.slug !== generateSlug(form.name);
	const handleNameChange = (name: string) => {
		setForm((previous) => {
			const edited = mode === "create" && previous.slug !== generateSlug(previous.name);
			return { ...previous, name, ...(edited ? {} : { slug: generateSlug(name) }) };
		});
	};

	const nameError =
		submitted && form.name.trim().length < 3 ? "Name must be at least 3 characters." : undefined;
	const slugError =
		submitted && mode === "create" && !isValidSlug(form.slug)
			? "Use 3–64 lowercase letters, numbers and single hyphens."
			: undefined;
	const errorSummary: FormError[] = [
		form.name.trim().length < 3 && {
			fieldId: "area-name",
			message: "Give the area a name of at least three characters.",
		},
		mode === "create" &&
			!isValidSlug(form.slug) && {
				fieldId: "area-slug",
				message: "The identifier must be 3–64 lowercase letters, numbers and single hyphens.",
			},
	].filter((entry): entry is FormError => Boolean(entry));
	const valid = errorSummary.length === 0;
	const updateAvailable = mode === "edit" && initialData.status.state === "UPDATE_WAITING";
	const resetLabel = updateAvailable ? "Apply Hephaestus update" : "Restore Hephaestus default";

	const submit = (event: React.FormEvent) => {
		event.preventDefault();
		setSubmitted(true);
		if (!valid) {
			requestAnimationFrame(() => document.getElementById(errorSummary[0].fieldId)?.focus());
			return;
		}
		onSubmit({
			slug: form.slug,
			name: form.name.trim(),
			description: form.description.trim() || undefined,
			icon: form.icon ?? undefined,
			color: form.color ?? undefined,
		});
	};

	return (
		<PageLayout>
			<AlertDialog open={resetOpen} onOpenChange={setResetOpen}>
				<AlertDialogContent>
					<AlertDialogHeader>
						<AlertDialogTitle>{resetLabel}?</AlertDialogTitle>
						<AlertDialogDescription>
							This replaces the customization and discards unsaved changes. It does not change
							whether workspace administrators can add the area. Existing workspace copies remain
							unchanged. Future updates apply automatically until the area is customized again.
						</AlertDialogDescription>
					</AlertDialogHeader>
					<AlertDialogFooter>
						<AlertDialogCancel disabled={isResetPending}>Cancel</AlertDialogCancel>
						<AlertDialogAction
							disabled={isResetPending}
							onClick={() => {
								setResetOpen(false);
								onUseHephaestusVersion?.();
							}}
						>
							{isResetPending ? `${resetLabel}…` : resetLabel}
						</AlertDialogAction>
					</AlertDialogFooter>
				</AlertDialogContent>
			</AlertDialog>

			{unsavedChanges.dialog}
			<FormErrorSummary errors={submitted ? errorSummary : []} className="max-w-3xl" />

			<Link
				from="/admin/catalog"
				to="/admin/catalog"
				search={(previous) => previous}
				className={cn(buttonVariants({ variant: "ghost", size: "sm" }), "-ml-3 w-fit")}
			>
				<ArrowLeft className="size-4" aria-hidden />
				Practice library
			</Link>
			<PageHeader
				icon={mode === "create" ? <ListPlus /> : <ClipboardPenLine />}
				title={mode === "create" ? "Create area" : `Edit: ${initialData.name}`}
				description={
					mode === "create"
						? "Use areas to group related practices in the instance library."
						: "Saving updates the instance library. Existing workspace copies will not change."
				}
			/>

			{mode === "edit" && (
				<HephaestusVersionPanel
					status={initialData.status}
					kind="area"
					shipped={initialData.shipped}
					isResetPending={isResetPending}
					isKeepPending={isKeepPending}
					disabled={conflict ?? false}
					onUseHephaestusVersion={
						canUseHephaestusVersion(initialData.status) && onUseHephaestusVersion
							? () => setResetOpen(true)
							: undefined
					}
					onKeepCurrentDefinition={onKeepCurrentDefinition}
				/>
			)}

			{conflict && (
				<div className="max-w-3xl space-y-2">
					<Alert variant="warning">
						<RotateCcw />
						<AlertTitle>This area changed while you were editing</AlertTitle>
						<AlertDescription>
							Your draft is safe. Continue with it and save to replace the latest changes, or leave
							this page and reopen the area to see them.
						</AlertDescription>
					</Alert>
					{onContinueWithDraft && (
						<Button type="button" variant="outline" size="sm" onClick={onContinueWithDraft}>
							Continue with my draft
						</Button>
					)}
				</div>
			)}

			<form onSubmit={submit} className="flex flex-col gap-8" noValidate>
				<fieldset disabled={formDisabled} className="contents">
					<div className="max-w-3xl space-y-8">
						<p className="text-muted-foreground text-sm">
							Fields marked <span aria-hidden>*</span> are required.
						</p>

						<section className="space-y-4">
							<h2 className="font-semibold text-lg">General</h2>
							<FieldGroup className="gap-4">
								<Field data-invalid={nameError ? "true" : undefined}>
									<FieldLabel htmlFor="area-name">Name *</FieldLabel>
									<Input
										id="area-name"
										value={form.name}
										onChange={(event) => handleNameChange(event.target.value)}
										placeholder="e.g. Code review"
										required
										minLength={3}
										maxLength={128}
										aria-invalid={Boolean(nameError)}
										aria-describedby={nameError ? "area-name-error" : undefined}
									/>
									{nameError && <FieldError id="area-name-error">{nameError}</FieldError>}
								</Field>

								<Field data-invalid={slugError ? "true" : undefined}>
									<FieldLabel htmlFor="area-slug">Identifier {mode === "create" && "*"}</FieldLabel>
									<div className="flex items-center gap-2">
										<Input
											id="area-slug"
											value={form.slug}
											onChange={(event) =>
												setForm((previous) => ({ ...previous, slug: event.target.value }))
											}
											disabled={mode === "edit"}
											required={mode === "create"}
											minLength={3}
											maxLength={64}
											aria-invalid={Boolean(slugError)}
											aria-describedby={
												["area-slug-description", slugError ? "area-slug-error" : undefined]
													.filter(Boolean)
													.join(" ") || undefined
											}
										/>
										{slugWasEdited && (
											<Button
												type="button"
												variant="ghost"
												size="icon-sm"
												onClick={() =>
													setForm((previous) => ({
														...previous,
														slug: generateSlug(previous.name),
													}))
												}
												aria-label="Reset to generated identifier"
											>
												<RotateCcw className="size-3.5" aria-hidden />
											</Button>
										)}
									</div>
									<FieldDescription id="area-slug-description">
										Used in URLs and integrations. It can't be changed later.
									</FieldDescription>
									{slugError && <FieldError id="area-slug-error">{slugError}</FieldError>}
								</Field>

								<Field>
									<FieldLabel htmlFor="area-description">Description</FieldLabel>
									<Textarea
										id="area-description"
										value={form.description}
										rows={3}
										onChange={(event) =>
											setForm((previous) => ({ ...previous, description: event.target.value }))
										}
										placeholder="e.g. Reviewing a change so problems surface early"
										maxLength={500}
										aria-describedby="area-description-help"
									/>
									<FieldDescription id="area-description-help">
										What this area develops, in the words a developer would use.
									</FieldDescription>
								</Field>
							</FieldGroup>
						</section>

						<section className="space-y-4">
							<h2 className="font-semibold text-lg">Presentation</h2>
							<FieldGroup className="gap-4">
								<Field>
									<FieldLabel htmlFor="area-appearance">Appearance</FieldLabel>
									<AreaVisualPicker
										id="area-appearance"
										describedBy="area-appearance-help"
										slug={form.slug}
										name={form.name}
										icon={form.icon}
										color={form.color}
										onChange={(patch) =>
											setForm((previous) => ({
												...previous,
												...(patch.icon !== undefined ? { icon: patch.icon } : {}),
												...(patch.color !== undefined ? { color: patch.color } : {}),
											}))
										}
										disabled={formDisabled}
									/>
									<FieldDescription id="area-appearance-help">
										New workspace copies use this icon and color.
									</FieldDescription>
								</Field>
							</FieldGroup>
						</section>
					</div>
				</fieldset>

				<FormActionBar
					className="max-w-3xl"
					secondary={
						<Link
							from="/admin/catalog"
							to="/admin/catalog"
							search={(previous) => previous}
							className={buttonVariants({ variant: "outline" })}
						>
							Cancel
						</Link>
					}
				>
					<Button type="submit" disabled={formDisabled || conflict}>
						{isPending && <Spinner className="size-4" />}
						{isPending
							? mode === "create"
								? "Creating…"
								: "Saving…"
							: mode === "create"
								? "Create area"
								: "Save changes"}
					</Button>
				</FormActionBar>
			</form>
		</PageLayout>
	);
}

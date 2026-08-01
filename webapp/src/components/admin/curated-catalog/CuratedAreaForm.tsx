import { Link, useBlocker } from "@tanstack/react-router";
import { ArrowLeft, ClipboardPenLine, ListPlus, RotateCcw } from "lucide-react";
import { useState } from "react";
import type { CatalogEntryStatus, CuratedAreaRequest } from "@/api/types.gen";
import { AreaVisualPicker } from "@/components/admin/practices/AreaVisualPicker";
import { generateSlug, isValidSlug } from "@/components/admin/practices/constants";
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
	/** What Hephaestus ships now, when it differs — shown before anything is taken. */
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
	onKeepOurVersion?: () => void;
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
		onKeepOurVersion,
		onSubmit,
	} = props;
	const [resetOpen, setResetOpen] = useState(false);
	const [form, setForm] = useState<FormState>(() => initialState(initialData));
	const [submitted, setSubmitted] = useState(false);
	const isDirty = JSON.stringify(form) !== JSON.stringify(initialState(initialData));
	const blocker = useBlocker({
		shouldBlockFn: () => isDirty,
		enableBeforeUnload: isDirty,
		disabled: !isDirty || isPending,
		withResolver: true,
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
	const valid = form.name.trim().length >= 3 && (mode === "edit" || isValidSlug(form.slug));

	const submit = (event: React.FormEvent) => {
		event.preventDefault();
		setSubmitted(true);
		if (!valid) {
			const firstInvalidId = form.name.trim().length < 3 ? "area-name" : "area-slug";
			requestAnimationFrame(() => document.getElementById(firstInvalidId)?.focus());
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
						<AlertDialogTitle>Use the Hephaestus version?</AlertDialogTitle>
						<AlertDialogDescription>
							Your version and any unsaved edits are discarded. From now on this area follows
							Hephaestus. Whether it is offered, and every workspace copy of it, stay as they are.
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
							{isResetPending ? "Using the Hephaestus version…" : "Use the Hephaestus version"}
						</AlertDialogAction>
					</AlertDialogFooter>
				</AlertDialogContent>
			</AlertDialog>

			<AlertDialog
				open={blocker.status === "blocked"}
				onOpenChange={(open, eventDetails) => {
					if (!open && eventDetails.reason === "escape-key") {
						blocker.reset?.();
					}
				}}
			>
				<AlertDialogContent>
					<AlertDialogHeader>
						<AlertDialogTitle>Discard unsaved changes?</AlertDialogTitle>
						<AlertDialogDescription>
							Your draft will be lost if you leave this page.
						</AlertDialogDescription>
					</AlertDialogHeader>
					<AlertDialogFooter>
						<AlertDialogCancel onClick={blocker.reset}>Keep editing</AlertDialogCancel>
						<AlertDialogAction variant="destructive" onClick={blocker.proceed}>
							Discard changes
						</AlertDialogAction>
					</AlertDialogFooter>
				</AlertDialogContent>
			</AlertDialog>

			<Link
				from="/admin/catalog"
				to="/admin/catalog"
				search={(previous) => previous}
				className={cn(buttonVariants({ variant: "ghost", size: "sm" }), "-ml-3 w-fit")}
			>
				<ArrowLeft className="size-4" aria-hidden />
				Practice catalog
			</Link>
			<PageHeader
				icon={mode === "create" ? <ListPlus /> : <ClipboardPenLine />}
				title={mode === "create" ? "Add area" : `Edit: ${initialData.name}`}
				description={
					mode === "create"
						? "Areas group the practices a workspace receives."
						: "Saving replaces what this instance offers. Workspaces that already have it are unaffected."
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
					onKeepOurVersion={onKeepOurVersion}
				/>
			)}

			{conflict && (
				<div className="max-w-3xl space-y-2">
					<Alert variant="warning">
						<RotateCcw />
						<AlertTitle>Someone else saved this area while you were editing</AlertTitle>
						<AlertDescription>
							Your draft is untouched. If you keep it and save, their changes are overwritten by
							everything in your draft. To see theirs instead, leave this page and open it again.
						</AlertDescription>
					</Alert>
					{onContinueWithDraft && (
						<Button type="button" variant="outline" size="sm" onClick={onContinueWithDraft}>
							Keep my draft
						</Button>
					)}
				</div>
			)}

			<form onSubmit={submit} className="flex flex-col gap-8" noValidate>
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
								<FieldLabel htmlFor="area-slug">Slug {mode === "create" && "*"}</FieldLabel>
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
												setForm((previous) => ({ ...previous, slug: generateSlug(previous.name) }))
											}
											aria-label="Reset to auto-generated slug"
										>
											<RotateCcw className="size-3.5" aria-hidden />
										</Button>
									)}
								</div>
								<FieldDescription id="area-slug-description">
									{mode === "edit"
										? "Slug cannot be changed after creation."
										: "A permanent id, not shown to developers. It cannot be changed later."}
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
									disabled={isPending || isResetPending || isKeepPending}
								/>
								<FieldDescription id="area-appearance-help">
									The icon and color every workspace copy inherits.
								</FieldDescription>
							</Field>
						</FieldGroup>
					</section>
				</div>

				<div className="flex max-w-3xl justify-between border-t pt-4">
					<Link
						from="/admin/catalog"
						to="/admin/catalog"
						search={(previous) => previous}
						className={buttonVariants({ variant: "outline" })}
					>
						Cancel
					</Link>
					<Button type="submit" disabled={isPending || conflict || isResetPending || isKeepPending}>
						{isPending && <Spinner className="size-4" />}
						{isPending
							? mode === "create"
								? "Adding…"
								: "Saving…"
							: mode === "create"
								? "Add area"
								: "Save changes"}
					</Button>
				</div>
			</form>
		</PageLayout>
	);
}

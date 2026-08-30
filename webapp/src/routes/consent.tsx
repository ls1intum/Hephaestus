import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, Link, redirect, useNavigate } from "@tanstack/react-router";
import { useState } from "react";

import {
	completeFirstLoginConsentMutation,
	getConsentStatusOptions,
	getConsentStatusQueryKey,
} from "@/api/@tanstack/react-query.gen";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Field, FieldContent, FieldDescription, FieldLabel } from "@/components/ui/field";
import { Spinner } from "@/components/ui/spinner";
import { resolveCurrentUser, safeReturnTo } from "@/integrations/auth/guard";

interface ConsentSearch {
	returnTo?: string;
}

export const Route = createFileRoute("/consent")({
	staticData: { surface: "auth" },
	validateSearch: (search): ConsentSearch => ({
		returnTo: typeof search.returnTo === "string" ? search.returnTo : undefined,
	}),
	beforeLoad: async ({ context, search }) => {
		const user = await resolveCurrentUser(context.queryClient);
		if (!user)
			throw redirect({ to: "/login", search: { returnTo: safeReturnTo(search.returnTo) } });
		const consent = await context.queryClient.query(getConsentStatusOptions({}));
		if (consent.completed) throw redirect({ href: safeReturnTo(search.returnTo) });
	},
	component: ConsentPage,
});

function ConsentPage() {
	const { returnTo } = Route.useSearch();
	const navigate = useNavigate();
	const queryClient = useQueryClient();
	const [termsAccepted, setTermsAccepted] = useState(false);
	const [research, setResearch] = useState(false);
	const { data, isPending, isError } = useQuery(getConsentStatusOptions({}));
	const mutation = useMutation({
		...completeFirstLoginConsentMutation(),
		onSuccess: (status) => {
			queryClient.setQueryData(getConsentStatusQueryKey({}), status);
			void navigate({ href: safeReturnTo(returnTo), replace: true });
		},
	});

	if (isPending) return <Spinner className="size-8" aria-label="Loading transparency notice" />;
	if (isError) {
		return (
			<p role="alert">We couldn't load the transparency notice. Please reload and try again.</p>
		);
	}

	return (
		<div className="mx-auto flex min-h-[100dvh] w-full max-w-2xl items-center px-4 py-10">
			<section
				className="w-full space-y-8 rounded-xl border bg-card p-6 shadow-sm sm:p-10"
				aria-labelledby="consent-heading"
			>
				<header className="space-y-2">
					<p className="text-sm font-medium text-primary">Before you continue</p>
					<h1 id="consent-heading" className="text-3xl font-semibold tracking-tight">
						How Hephaestus uses your data
					</h1>
					<p className="text-sm text-muted-foreground">Notice version {data.noticeVersion}</p>
				</header>
				<div className="space-y-4 text-sm leading-6">
					{data.noticeText.split("\n\n").map((paragraph) => (
						<p key={paragraph}>{paragraph}</p>
					))}
					<Link
						to="/privacy"
						target="_blank"
						className="font-medium text-primary underline underline-offset-4"
					>
						Read the full privacy notice
					</Link>
				</div>
				<form
					className="space-y-6 border-t pt-6"
					onSubmit={(event) => {
						event.preventDefault();
						mutation.mutate({
							body: {
								noticeVersion: data.noticeVersion,
								termsAccepted,
								participateInResearch: research,
							},
						});
					}}
				>
					<Field orientation="horizontal">
						<Checkbox
							id="accept-terms"
							checked={termsAccepted}
							onCheckedChange={setTermsAccepted}
						/>
						<FieldContent>
							<FieldLabel htmlFor="accept-terms">I accept the Hephaestus terms of use</FieldLabel>
							<FieldDescription>
								Terms acceptance is separate from the optional research choice.
							</FieldDescription>
						</FieldContent>
					</Field>
					<Field orientation="horizontal" className="rounded-lg border p-4">
						<Checkbox id="research-opt-in" checked={research} onCheckedChange={setResearch} />
						<FieldContent>
							<FieldLabel htmlFor="research-opt-in">
								I consent to academic research participation
							</FieldLabel>
							<FieldDescription>
								Optional and unchecked by default. Declining does not change your access.
							</FieldDescription>
						</FieldContent>
					</Field>
					<p className="text-sm text-muted-foreground">
						Continuing records that you received the privacy notice. This acknowledgement is not
						consent.
					</p>
					<Button type="submit" className="w-full" disabled={!termsAccepted || mutation.isPending}>
						{mutation.isPending ? "Saving…" : "Continue"}
					</Button>
					{mutation.isError && (
						<p role="alert" className="text-sm text-destructive">
							Your choice wasn't saved. Review the current notice and try again.
						</p>
					)}
				</form>
			</section>
		</div>
	);
}

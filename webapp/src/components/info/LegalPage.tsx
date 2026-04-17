import { type AnchorHTMLAttributes, type ImgHTMLAttributes, useEffect, useState } from "react";
import { Streamdown } from "streamdown";
import { Button } from "@/components/ui/button";
import environment from "@/environment";
import {
	detectLegalLocale,
	isSafeLegalHref,
	isSafeLegalImageSrc,
	LEGAL_LOCALES,
	type LegalLocale,
	type LegalPageId,
	type ResolvedLegalContent,
	resolveLegalContent,
} from "@/lib/legal";

const LOCALE_LABELS: Record<LegalLocale, string> = {
	en: "English",
	de: "Deutsch",
};

const DISCLAIMER_BANNER: Record<LegalLocale, string> = {
	en: "This deployment has not been configured with a legal profile. The content below is a placeholder and does not identify the operator of this instance.",
	de: "Für diese Bereitstellung ist kein Rechtsprofil konfiguriert. Die folgenden Inhalte sind ein Platzhalter und benennen den Betreiber dieser Instanz nicht.",
};

const ERROR_COPY: Record<LegalLocale, string> = {
	en: "Unable to load legal content.",
	de: "Der rechtliche Inhalt konnte nicht geladen werden.",
};

const LANGUAGE_GROUP_LABEL: Record<LegalLocale, string> = {
	en: "Select language",
	de: "Sprache auswählen",
};

// Operator-supplied Markdown is untrusted from the renderer's point of view:
// the override mount is writable by whoever controls the host filesystem.
// Strip raw HTML at the remark-rehype boundary (empty rehypePlugins drops
// Streamdown's default rehype-raw; allowDangerousHtml:false demotes HTML
// nodes to text) and filter link/image URLs through an allow-list so
// javascript:, data:, and unknown schemes never reach the DOM. Streamdown's
// bundled rehype-harden is configured with allowedProtocols:["*"] and
// allowDataImages:true by default, so it cannot be relied on for URL
// filtering — these wrappers are the authoritative guard.
// Authored `title` attributes (`[x](url "title")`) are a UI-spoof primitive
// from an untrusted author, and streamdown passes through internal props
// like `node`. Render only the whitelist of attributes we actively rely on.
function SafeAnchor({ href, children, className }: AnchorHTMLAttributes<HTMLAnchorElement>) {
	if (!isSafeLegalHref(href)) {
		return <span className={className}>{children}</span>;
	}
	const isExternal = /^https?:/i.test(href);
	return (
		<a
			href={href}
			className={className}
			rel={isExternal ? "noopener noreferrer" : undefined}
			target={isExternal ? "_blank" : undefined}
		>
			{children}
		</a>
	);
}

function SafeImage({ src, alt, className }: ImgHTMLAttributes<HTMLImageElement>) {
	if (!isSafeLegalImageSrc(src)) return null;
	return <img src={src} alt={alt ?? ""} className={className} />;
}

const SAFE_COMPONENTS = { a: SafeAnchor, img: SafeImage };

export interface LegalPageProps {
	page: LegalPageId;
	title: Record<LegalLocale, string>;
	/** Injected fetcher, primarily for tests and Storybook. */
	resolver?: typeof resolveLegalContent;
	/** Initial locale override; otherwise detected from navigator. */
	initialLocale?: LegalLocale;
	/**
	 * Profile override, primarily for Storybook. Production reads
	 * `environment.legal.profile`.
	 */
	profileOverride?: string;
}

export function LegalPage({
	page,
	title,
	resolver = resolveLegalContent,
	initialLocale,
	profileOverride,
}: LegalPageProps) {
	const [locale, setLocale] = useState<LegalLocale>(
		() =>
			initialLocale ?? detectLegalLocale(typeof navigator === "undefined" ? undefined : navigator),
	);
	const [resolved, setResolved] = useState<ResolvedLegalContent | null>(null);
	const [error, setError] = useState<Error | null>(null);

	const profile = profileOverride ?? environment.legal.profile;

	useEffect(() => {
		const controller = new AbortController();
		setError(null);
		setResolved(null);
		resolver(page, locale, { signal: controller.signal, profile })
			.then((next) => {
				if (controller.signal.aborted) return;
				setResolved(next);
				if (next.source === "disclaimer") {
					// Shipping the disclaimer in production violates § 5 DDG and Art. 13
					// GDPR; the red banner alone is not enough because operators rarely
					// open the page themselves.
					console.warn(
						`[legal] Disclaimer fallback served for page=${page}. Configure LEGAL_PROFILE or mount /legal-overrides/. See docs/admin/legal-pages.`,
					);
				}
			})
			.catch((err: unknown) => {
				if (controller.signal.aborted || (err instanceof DOMException && err.name === "AbortError"))
					return;
				setError(err instanceof Error ? err : new Error("Failed to load legal content"));
			});
		return () => controller.abort();
	}, [page, locale, profile, resolver]);

	return (
		<div className="max-w-4xl mx-auto flex flex-col gap-4">
			<div className="flex items-start justify-between gap-4">
				<h1 className="text-3xl font-bold">{title[locale]}</h1>
				<div className="flex gap-1" role="group" aria-label={LANGUAGE_GROUP_LABEL[locale]}>
					{LEGAL_LOCALES.map((candidate) => (
						<Button
							key={candidate}
							type="button"
							size="sm"
							variant={candidate === locale ? "default" : "outline"}
							aria-pressed={candidate === locale}
							lang={candidate}
							onClick={() => setLocale(candidate)}
						>
							{LOCALE_LABELS[candidate]}
						</Button>
					))}
				</div>
			</div>

			{resolved?.source === "disclaimer" ? (
				<div
					role="alert"
					className="rounded-md border border-destructive/40 bg-destructive/10 px-4 py-3 text-sm text-destructive"
					data-testid="legal-disclaimer-banner"
				>
					{DISCLAIMER_BANNER[locale]}
				</div>
			) : null}

			{error ? (
				<div
					role="alert"
					className="rounded-md border border-destructive/40 bg-destructive/10 px-4 py-3 text-sm text-destructive"
				>
					{ERROR_COPY[locale]}
				</div>
			) : null}

			{resolved ? (
				<article lang={resolved.locale} className="prose dark:prose-invert max-w-none">
					<Streamdown
						mode="static"
						rehypePlugins={[]}
						remarkRehypeOptions={{ allowDangerousHtml: false }}
						parseIncompleteMarkdown={false}
						components={SAFE_COMPONENTS}
					>
						{resolved.markdown}
					</Streamdown>
				</article>
			) : null}
		</div>
	);
}

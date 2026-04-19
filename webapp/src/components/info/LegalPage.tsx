import { type AnchorHTMLAttributes, type ImgHTMLAttributes, useEffect, useState } from "react";
import { Streamdown } from "streamdown";
import environment from "@/environment";
import {
	isSafeLegalHref,
	isSafeLegalImageSrc,
	type LegalPageId,
	type ResolvedLegalContent,
	resolveLegalContent,
} from "@/lib/legal";

const DISCLAIMER_BANNER =
	"This deployment has not been configured with a legal profile. The content below is a placeholder and does not identify the operator of this instance.";

const ERROR_COPY = "Unable to load legal content.";

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

// Warn once per page, per module-lifetime. The entrypoint already logs a
// discoverable WARN at container startup; this is a belt-and-braces nudge for
// operators who open devtools, not a metric, so re-emitting on every render
// adds noise without information.
const warnedDisclaimer = new Set<LegalPageId>();

export interface LegalPageProps {
	page: LegalPageId;
	title: string;
	/** Injected fetcher, primarily for tests and Storybook. */
	resolver?: typeof resolveLegalContent;
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
	profileOverride,
}: LegalPageProps) {
	const [resolved, setResolved] = useState<ResolvedLegalContent | null>(null);
	const [error, setError] = useState<Error | null>(null);

	const profile = profileOverride ?? environment.legal.profile;

	useEffect(() => {
		const controller = new AbortController();
		setError(null);
		setResolved(null);
		resolver(page, { signal: controller.signal, profile })
			.then((next) => {
				if (controller.signal.aborted) return;
				setResolved(next);
				if (next.source === "disclaimer" && !warnedDisclaimer.has(page)) {
					warnedDisclaimer.add(page);
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
	}, [page, profile, resolver]);

	return (
		<div className="max-w-4xl mx-auto flex flex-col gap-4">
			<h1 className="text-3xl font-bold">{title}</h1>

			{resolved?.source === "disclaimer" ? (
				<div
					role="alert"
					className="rounded-md border border-destructive/40 bg-destructive/10 px-4 py-3 text-sm text-destructive"
					data-testid="legal-disclaimer-banner"
				>
					{DISCLAIMER_BANNER}
				</div>
			) : null}

			{error ? (
				<div
					role="alert"
					className="rounded-md border border-destructive/40 bg-destructive/10 px-4 py-3 text-sm text-destructive"
				>
					{ERROR_COPY}
				</div>
			) : null}

			{resolved ? (
				<article lang="en" className="prose dark:prose-invert max-w-none">
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

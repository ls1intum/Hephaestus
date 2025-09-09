import { AnimatePresence, motion } from "framer-motion";
import { CheckCircle2 } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { cn } from "@/lib/utils";
import { Streamdown } from "streamdown";

interface MessageReasoningProps {
	isLoading: boolean;
	reasoning: string;
	variant?: "default" | "artifact";
}

export function MessageReasoning({
	isLoading,
	reasoning,
	variant = "default",
}: MessageReasoningProps) {
	const [isExpanded, setIsExpanded] = useState(false);
	const hasContent = (reasoning ?? "").trim().length > 0;

	// Track elapsed thinking time
	const startRef = useRef<number | null>(null);
	const endRef = useRef<number | null>(null);
	const [, forceTick] = useState(0);

	// Initialize start/end timestamps based on loading transitions
	useEffect(() => {
		if (isLoading) {
			if (startRef.current == null) {
				startRef.current =
					typeof performance !== "undefined" ? performance.now() : Date.now();
			}
			// Reset end when resuming loading (rare)
			endRef.current = null;
		} else if (startRef.current != null && endRef.current == null) {
			endRef.current =
				typeof performance !== "undefined" ? performance.now() : Date.now();
		}
	}, [isLoading]);

	// While loading and started, tick to update the display time live
	useEffect(() => {
		if (!isLoading || startRef.current == null) return;
		const id = setInterval(() => forceTick((n) => (n + 1) % 1000), 1000);
		return () => clearInterval(id);
	}, [isLoading]);

	const formatDuration = (ms: number) => {
		const totalSeconds = Math.max(0, Math.ceil(ms / 1000));
		const hours = Math.floor(totalSeconds / 3600);
		const minutes = Math.floor((totalSeconds % 3600) / 60);
		const seconds = totalSeconds % 60;
		const parts: string[] = [];
		if (hours > 0) parts.push(`${hours}h`);
		if (minutes > 0) parts.push(`${minutes}m`);
		parts.push(`${seconds}s`);
		return parts.join(" ");
	};

	const elapsedMs = (() => {
		if (startRef.current == null) return 0;
		const end =
			endRef.current ??
			(typeof performance !== "undefined" ? performance.now() : Date.now());
		return end - startRef.current;
	})();

	const variants = {
		collapsed: { height: 0, opacity: 0, marginTop: 0, marginBottom: 0 },
		expanded: {
			height: "auto",
			opacity: 1,
			marginTop: "1rem",
			marginBottom: "0.5rem",
		},
	} as const;

	// Parse headings (markdown # or inline **bold**) and split into sections
	type ParsedHeading = {
		title: string;
		index: number;
		length: number;
		kind: "md" | "bold";
	};

	const parseHeadings = useCallback((text: string): ParsedHeading[] => {
		const found: ParsedHeading[] = [];
		if (!text) return found;
		// Markdown headings at start of line
		const mdRegex = /^#{1,6}\s+(.+?)\s*$/gm;
		let m: RegExpExecArray | null = mdRegex.exec(text);
		for (; m !== null; m = mdRegex.exec(text)) {
			found.push({
				title: m[1].trim(),
				index: m.index,
				length: m[0].length,
				kind: "md",
			});
		}
		// Bold headings anywhere (inline too)
		const boldRegex = /\*\*(.+?)\*\*/g;
		m = boldRegex.exec(text);
		for (; m !== null; m = boldRegex.exec(text)) {
			const title = (m[1] ?? "").trim();
			if (title.length === 0) continue;
			found.push({ title, index: m.index, length: m[0].length, kind: "bold" });
		}
		found.sort((a, b) => a.index - b.index);
		return found;
	}, []);

	type Section = { title?: string; body: string };
	const parseSections = useCallback(
		(text: string): Section[] => {
			const sections: Section[] = [];
			if (!text) return sections;
			const heads = parseHeadings(text);
			if (heads.length === 0)
				return [{ title: "Thinking for a few seconds", body: text }];
			// Preamble before first heading
			if (heads[0].index > 0) {
				sections.push({ body: text.slice(0, heads[0].index).trim() });
			}
			for (let i = 0; i < heads.length; i++) {
				const h = heads[i];
				const startBody = h.index + h.length;
				const end = i + 1 < heads.length ? heads[i + 1].index : text.length;
				sections.push({
					title: h.title,
					body: text.slice(startBody, end).trim(),
				});
			}
			return sections;
		},
		[parseHeadings],
	);

	const sections = useMemo(
		() => (hasContent ? parseSections(reasoning) : []),
		[hasContent, reasoning, parseSections],
	);
	const lastHeading = useMemo(
		() =>
			sections.length > 0
				? (sections[sections.length - 1].title ?? null)
				: null,
		[sections],
	);
	const hasTiming = startRef.current != null && endRef.current != null;
	const headerText = isLoading
		? lastHeading || "Thinking"
		: hasTiming
			? `Thought for ${formatDuration(elapsedMs)}`
			: lastHeading || "Reasoning details";

	return (
		<div className="flex flex-col">
			{isLoading && !hasContent ? (
				// Loading, but no content yet: static header with shimmer
				<div className="flex flex-col gap-1">
					{/* Embed keyframes locally so Storybook always has them */}
					<style>{`
						@keyframes message-reasoning-shimmer { from { background-position-x: -200%; } to { background-position-x: 200%; } }
						@media (prefers-reduced-motion: reduce) { .mr-shimmer { animation: none !important; } }
					`}</style>
					<div className="font-medium text-muted-foreground relative inline-block align-middle">
						<span>{"Thinking"}</span>
						<span
							aria-hidden
							className="absolute inset-0 pointer-events-none opacity-70 mr-shimmer"
							style={{
								background:
									"linear-gradient(90deg, transparent 45%, rgba(255,255,255,0.6) 50%, transparent 55%)",
								backgroundSize: "200% 100%",
								animation:
									"message-reasoning-shimmer 1.4s linear infinite reverse",
							}}
						/>
					</div>
				</div>
			) : (
				// Either not loading, or loading with some content: clickable header (no chevron/spinner)
				<button
					data-testid="message-reasoning-toggle"
					type="button"
					aria-expanded={isExpanded}
					disabled={isLoading && !hasContent}
					className="text-left cursor-pointer flex flex-col gap-1 items-start text-muted-foreground hover:text-secondary-foreground/70 disabled:cursor-default"
					onClick={() => {
						if (isLoading && !hasContent) return;
						setIsExpanded(!isExpanded);
					}}
				>
					<div className="font-medium relative inline-block align-middle">
						{/* Embed keyframes locally so Storybook always has them */}
						<style>{`
							@keyframes message-reasoning-shimmer { from { background-position-x: -200%; } to { background-position-x: 200%; } }
							@media (prefers-reduced-motion: reduce) { .mr-shimmer { animation: none !important; } }
						`}</style>
						<span>{headerText}</span>
						{isLoading && (
							<span
								aria-hidden
								className="absolute inset-0 pointer-events-none opacity-70 mr-shimmer"
								style={{
									background:
										"linear-gradient(90deg, transparent 20%, rgba(255,255,255,0.6) 50%, transparent 55%)",
									backgroundSize: "200% 100%",
									animation:
										"message-reasoning-shimmer 2s linear infinite reverse",
								}}
							/>
						)}
					</div>
				</button>
			)}

			<AnimatePresence initial={false}>
				{isExpanded && hasContent && (
					<motion.div
						data-testid="message-reasoning"
						key="content"
						initial="collapsed"
						animate="expanded"
						exit="collapsed"
						variants={variants}
						transition={{ duration: 0.2, ease: "easeInOut" }}
						className={cn(
							"pl-4 text-zinc-600 dark:text-zinc-400 border-l flex flex-col gap-2",
							// Use the artifact chat surface for ring to blend in when embedded
							variant === "artifact" ? "[--ring-color:var(--artifact-bg)]" : "",
						)}
					>
						{sections.length > 0 ? (
							<>
								{sections.map((s, idx) => {
									const k = `${s.title ?? "_"}-${(s.body ?? "").slice(0, 24)}-${idx}`;
									return (
										<div key={k} className="relative">
											<span
												className={cn(
													"absolute -left-[19.5px] top-2.5 h-1.5 w-1.5 rounded-full bg-zinc-400 ring-12",
													variant === "artifact"
														? "ring-muted dark:ring-background"
														: "ring-background",
												)}
											/>
											{s.body && <Streamdown>{s.body}</Streamdown>}
										</div>
									);
								})}
								{!isLoading && (
									<div className="relative pt-1">
										<span
											className={cn(
												"absolute -left-[24px] top-1.5 text-zinc-400 ring-8",
												variant === "artifact"
													? "bg-muted dark:bg-background ring-muted dark:ring-background"
													: "ring-background bg-background",
											)}
										>
											<CheckCircle2 className="size-4" />
										</span>
										<div className="text-sm text-muted-foreground">Done</div>
									</div>
								)}
							</>
						) : (
							<Streamdown>{reasoning}</Streamdown>
						)}
					</motion.div>
				)}
			</AnimatePresence>
		</div>
	);
}

import { AnimatePresence, motion } from "framer-motion";
import { CheckCircle2 } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Markdown } from "./Markdown";

interface MessageReasoningProps {
	isLoading: boolean;
	reasoning: string;
}

export function MessageReasoning({
	isLoading,
	reasoning,
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
		const totalSeconds = Math.max(0, Math.floor(ms / 1000));
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
			if (heads.length === 0) return [{ body: text }];
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
	const headerText = isLoading
		? lastHeading || "Thinking"
		: `Thought for ${formatDuration(elapsedMs)}`;

	return (
		<div className="flex flex-col">
			{isLoading && !hasContent ? (
				// Loading, but no content yet: static header with shimmer
				<div className="flex flex-col gap-1">
					<div className="font-medium text-muted-foreground">Thinking</div>
					<div className="h-1.5 w-24 rounded-full skeleton-div" />
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
					<div className="font-medium">{headerText}</div>
					{isLoading && (
						<div className="h-1.5 w-24 rounded-full skeleton-div" />
					)}
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
						className="pl-4 text-zinc-600 dark:text-zinc-400 border-l flex flex-col gap-2"
					>
						{sections.length > 0 ? (
							<>
								{sections.map((s, idx) => {
									const k = `${s.title ?? "_"}-${(s.body ?? "").slice(0, 24)}-${idx}`;
									return (
										<div key={k} className="relative">
											<span className="absolute -left-[18.5px] top-2.5 h-1 w-1 rounded-full bg-zinc-400 ring-10 ring-background" />
											{s.body && <Markdown>{s.body}</Markdown>}
										</div>
									);
								})}
								<div className="relative pt-1">
									<span className="absolute -left-[22.5px] top-2 text-zinc-400 bg-background ring-4 ring-background rounded-full">
										<CheckCircle2 className="size-3" />
									</span>
									<div className="text-sm text-muted-foreground">Done</div>
								</div>
							</>
						) : (
							<Markdown>{reasoning}</Markdown>
						)}
					</motion.div>
				)}
			</AnimatePresence>
		</div>
	);
}

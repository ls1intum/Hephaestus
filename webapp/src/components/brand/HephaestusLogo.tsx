export function HephMark({ className = "size-8" }: { className?: string }) {
	return (
		<span className={`inline-flex shrink-0 ${className}`} aria-hidden="true">
			<img className="size-full" src="/brand/hephaestus-mark.svg" alt="" />
		</span>
	);
}

export function HephaestusWordmark({ className = "" }: { className?: string }) {
	return (
		<span className={className}>
			<span className="text-brand-accent">Heph</span>aestus
		</span>
	);
}

interface HephaestusLogoProps {
	className?: string;
	markClassName?: string;
	wordmarkClassName?: string;
}

export function HephaestusLogo({
	className = "",
	markClassName,
	wordmarkClassName = "",
}: HephaestusLogoProps) {
	return (
		<span className={`inline-flex items-center gap-2 ${className}`}>
			<HephMark className={markClassName} />
			<HephaestusWordmark className={`font-semibold tracking-tight ${wordmarkClassName}`} />
		</span>
	);
}

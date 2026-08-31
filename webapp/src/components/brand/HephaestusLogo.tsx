import { HephIcon } from "./HephIcon";

export function HephMark({ className = "size-8" }: { className?: string }) {
	return (
		<span
			className={`inline-flex shrink-0 items-center justify-center rounded-full bg-brand text-white ${className}`}
			aria-hidden="true"
		>
			<HephIcon className="size-[72%]" animated={false} pad={3} />
		</span>
	);
}

export function HephaestusWordmark({ className = "" }: { className?: string }) {
	return (
		<span className={className} aria-label="Hephaestus">
			<span className="text-brand-accent" aria-hidden="true">
				Heph
			</span>
			<span aria-hidden="true">aestus</span>
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

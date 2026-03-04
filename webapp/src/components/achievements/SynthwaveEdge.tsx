import { type Edge, type EdgeProps } from "@xyflow/react";
import { useEffect, useRef, useState } from "react";

export type SynthwaveVariant = "neon" | "rarity";

export type SynthwaveEdge = Edge<{ isEnabled: boolean; variant?: SynthwaveVariant }, "synthwave">;

/**
 * Compute a chaotic sine-wave SVG path between two points.
 *
 * Mixes a **primary** sine wave with a **secondary harmonic** at a
 * different frequency ratio to create an interference pattern that
 * looks more organic / equalizer-like.
 *
 * @param sx         source X
 * @param sy         source Y
 * @param tx         target X
 * @param ty         target Y
 * @param amplitude  peak displacement of the primary wave (px)
 * @param frequency  full sine cycles of the primary wave along the edge
 * @param phase      phase offset (radians) — animated externally
 * @param harmonic   frequency multiplier for the secondary wave (e.g. 1.6)
 * @param harmonicAmp amplitude ratio of the secondary wave (0–1)
 * @param steps      polyline resolution (higher = smoother)
 */
function getWavePath(
	sx: number,
	sy: number,
	tx: number,
	ty: number,
	amplitude: number,
	frequency: number,
	phase: number,
	harmonic = 1.6,
	harmonicAmp = 0.35,
	steps = 100,
): string {
	const dx = tx - sx;
	const dy = ty - sy;
	const len = Math.sqrt(dx * dx + dy * dy);

	if (len === 0) return `M ${sx} ${sy}`;

	// Unit vectors: along and perpendicular to the edge
	const nx = -dy / len; // normal X
	const ny = dx / len; // normal Y

	const parts: string[] = [];
	const tau = Math.PI * 2;

	for (let i = 0; i <= steps; i++) {
		const t = i / steps;
		// Position along the edge
		const px = sx + dx * t;
		const py = sy + dy * t;
		// Primary wave + secondary harmonic → chaotic interference
		const primary = Math.sin(t * frequency * tau + phase);
		const secondary = Math.sin(t * frequency * harmonic * tau + phase * 1.3);
		const wave = (primary + secondary * harmonicAmp) * amplitude;
		const x = px + nx * wave;
		const y = py + ny * wave;

		parts.push(i === 0 ? `M ${x} ${y}` : `L ${x} ${y}`);
	}

	return parts.join(" ");
}

/**
 * Wave shape parameters (identical for every color variant).
 *
 * Each layer differs dramatically in frequency, amplitude, speed, and
 * harmonic ratio to produce a clearly chaotic, layered equalizer effect.
 */
const WAVE_SHAPES = [
	{
		// Layer 1 — mid-freq backbone, heavy harmonic wobble
		amplitude: 6,
		frequency: 2.5,
		phaseOffset: 0,
		speedMultiplier: 0.8,
		harmonic: 2.1,
		harmonicAmp: 0.45,
		opacity: 0.95,
		width: 2,
	},
	{
		// Layer 2 — higher freq, taller, faster, energetic
		amplitude: 7,
		frequency: 3.8,
		phaseOffset: (Math.PI * 2) / 3,
		speedMultiplier: 1.5,
		harmonic: 1.4,
		harmonicAmp: 0.25,
		opacity: 1.0,
		width: 2.2,
	},
	{
		// Layer 3 — highest freq, widest amp, rapid, very chaotic
		amplitude: 8,
		frequency: 5.5,
		phaseOffset: (Math.PI * 4) / 3,
		speedMultiplier: 2.2,
		harmonic: 1.7,
		harmonicAmp: 0.5,
		opacity: 0.9,
		width: 1.8,
	},
] as const;

/**
 * Color palettes keyed by variant name.
 *
 * - **neon** — vivid synthwave oklch neons (cyan / magenta / violet)
 * - **rarity** — uses the app's rarity CSS custom properties
 *   (uncommon green, rare blue, epic purple) so they adapt to
 *   light/dark mode automatically.
 */
const VARIANT_COLORS: Record<SynthwaveVariant, readonly [string, string, string]> = {
	neon: [
		"oklch(0.82 0.18 195)", // Electric cyan
		"oklch(0.72 0.25 340)", // Hot magenta
		"oklch(0.68 0.22 300)", // Neon violet
	],
	rarity: [
		"var(--rarity-uncommon)", // Green
		"var(--rarity-rare)",     // Blue
		"var(--rarity-epic)",     // Purple
	],
};

/** Base animation speed in radians/sec */
const BASE_SPEED = 1.5;

export function SynthwaveEdge(props: EdgeProps<SynthwaveEdge>) {
	const { sourceX, sourceY, targetX, targetY, data } = props;
	const isEnabled = data?.isEnabled ?? false;
	const variant: SynthwaveVariant = data?.variant ?? "neon";
	const colors = VARIANT_COLORS[variant];

	// One phase value per layer so each layer can run at its own speed
	const [phases, setPhases] = useState(() => WAVE_SHAPES.map(() => 0));
	const rafRef = useRef<number>(0);
	const prevTimeRef = useRef<number>(0);

	// Animate phases with requestAnimationFrame (only when enabled)
	useEffect(() => {
		if (!isEnabled) return;

		const animate = (time: number) => {
			if (prevTimeRef.current === 0) {
				prevTimeRef.current = time;
			}
			const delta = (time - prevTimeRef.current) * 0.001; // → seconds
			prevTimeRef.current = time;

			setPhases((prev) =>
				prev.map((p, i) => p - delta * BASE_SPEED * WAVE_SHAPES[i].speedMultiplier),
			);

			rafRef.current = requestAnimationFrame(animate);
		};

		rafRef.current = requestAnimationFrame(animate);

		return () => {
			cancelAnimationFrame(rafRef.current);
			prevTimeRef.current = 0;
		};
	}, [isEnabled]);

	// Inactive: render a simple straight line (matches AchievementEdge inactive)
	if (!isEnabled) {
		return (
			<path
				d={`M ${sourceX} ${sourceY} L ${targetX} ${targetY}`}
				stroke="var(--edge-inactive)"
				strokeWidth={1}
				fill="none"
			/>
		);
	}

	return (
		<>
			{/* Render 3 overlapping sine-wave paths (sharp, no blur filters) */}
			{WAVE_SHAPES.map((layer, i) => (
				<path
					key={i}
					d={getWavePath(
						sourceX,
						sourceY,
						targetX,
						targetY,
						layer.amplitude,
						layer.frequency,
						phases[i] + layer.phaseOffset,
						layer.harmonic,
						layer.harmonicAmp,
					)}
					stroke={colors[i]}
					strokeWidth={layer.width}
					fill="none"
					opacity={layer.opacity}
					strokeLinecap="round"
				/>
			))}
		</>
	);
}

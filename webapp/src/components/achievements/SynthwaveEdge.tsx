import type { Edge, EdgeProps } from "@xyflow/react";
import { useAnimationTime } from "@/hooks/use-animation-time";

export type SynthwaveEdge = Edge<{ isEnabled: boolean }, "synthwave">;

/**
 * Compute a chaotic sine-wave SVG path between two points.
 *
 * Mixes a **primary** sine wave with a **secondary harmonic** at a
 * different frequency ratio to create an interference pattern that
 * looks more organic / equalizer-like.
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

	const nx = -dy / len; // normal X
	const ny = dx / len; // normal Y

	const parts: string[] = [];
	const tau = Math.PI * 2;

	for (let i = 0; i <= steps; i++) {
		const t = i / steps;
		const px = sx + dx * t;
		const py = sy + dy * t;
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
 * Wave shape parameters.
 */
const WAVE_SHAPES = [
	{
		// Layer 1
		amplitude: 6,
		frequency: 2.5,
		phaseOffset: 0,
		speedMultiplier: 0.8,
		harmonic: 2.1,
		harmonicAmp: 0.45,
		opacity: 0.8,
		width: 1.2,
	},
	{
		// Layer 2
		amplitude: 7,
		frequency: 3.8,
		phaseOffset: (Math.PI * 2) / 3,
		speedMultiplier: 1.5,
		harmonic: 1.4,
		harmonicAmp: 0.25,
		opacity: 0.6,
		width: 1.0,
	},
	{
		// Layer 3
		amplitude: 8,
		frequency: 5.5,
		phaseOffset: (Math.PI * 4) / 3,
		speedMultiplier: 2.2,
		harmonic: 1.7,
		harmonicAmp: 0.5,
		opacity: 0.4,
		width: 0.8,
	},
] as const;

/** Base animation speed in radians/sec */
const BASE_SPEED = 1.5;

export function SynthwaveEdge(props: EdgeProps<SynthwaveEdge>) {
	const { sourceX, sourceY, targetX, targetY, data } = props;
	const isEnabled = data?.isEnabled ?? false;

	const rawTime = useAnimationTime();
	const time = isEnabled ? rawTime : 0;

	// Derive phases deterministically from the shared clock instead of accumulating deltas
	const phases = WAVE_SHAPES.map((layer) => -(time * BASE_SPEED * layer.speedMultiplier));

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
			{/* Static base track logic removed for Synthwave, just using waves but monochromatic */}
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
					stroke="var(--foreground)"
					strokeWidth={layer.width}
					fill="none"
					opacity={layer.opacity}
					strokeLinecap="round"
				/>
			))}
		</>
	);
}

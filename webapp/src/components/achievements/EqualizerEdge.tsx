import type { Edge, EdgeProps } from "@xyflow/react";
import { useEffect, useRef, useState } from "react";

export type EqualizerVariant = "traveling" | "static";

export type EqualizerEdge = Edge<
	{ isEnabled: boolean; variant?: EqualizerVariant; monochrome?: boolean },
	"equalizer"
>;

/**
 * Compute an SVG path for a straight line with a localized, traveling audio-wave burst.
 *
 * @param sx            source X
 * @param sy            source Y
 * @param tx            target X
 * @param ty            target Y
 * @param pulsePosition relative position of the burst center (0 = start, 1 = end)
 * @param timePhase     continuously increasing time for inner wave animation
 * @param seedOffset    phase shift to allow multiple layered waves to look distinct
 * @param amplitudeMult scales the height of the burst
 * @param steps         polyline resolution
 */
function getTravelingWavePath(
	sx: number,
	sy: number,
	tx: number,
	ty: number,
	pulsePosition: number,
	timePhase: number,
	seedOffset: number,
	amplitudeMult = 1.0,
	steps = 100,
): string {
	const dx = tx - sx;
	const dy = ty - sy;
	const len = Math.sqrt(dx * dx + dy * dy);

	// Fallback to straight line if too short
	if (len < 5) return `M ${sx} ${sy} L ${tx} ${ty}`;

	// Unit normal vector perpendicular to the edge
	const nx = -dy / len;
	const ny = dx / len;

	const parts: string[] = [];

	for (let i = 0; i <= steps; i++) {
		const t = i / steps;
		const px = sx + dx * t;
		const py = sy + dy * t;

		// Convert relative progress to absolute pixel distance
		const absPos = t * len;
		const absCenter = pulsePosition * len;
		const distFromCenter = Math.abs(absPos - absCenter);

		// Envelope: Gaussian bell curve, ~40px wide regardless of edge length
		const envelope = Math.exp(-((distFromCenter / 20) ** 2));

		let waveDisp = 0;
		if (envelope > 0.01) {
			// Rapid audio-like frequencies creating chaotic interference
			const primary = Math.sin(absPos * 0.3 - timePhase * 15 + seedOffset * 10);
			const secondary = Math.sin(absPos * 0.7 + timePhase * 25 + seedOffset * 20) * 0.5;
			const noise = Math.sin(absPos * 1.5 - timePhase * 40 + seedOffset * 30) * 0.25;

			const waveForm = primary + secondary + noise;
			const maxAmp = 12 * amplitudeMult; // Max pixel peak displacement
			waveDisp = envelope * waveForm * maxAmp;
		}

		const x = px + nx * waveDisp;
		const y = py + ny * waveDisp;

		parts.push(i === 0 ? `M ${x} ${y}` : `L ${x} ${y}`);
	}

	return parts.join(" ");
}

/**
 * Compute an SVG path for a straight line with a static equalizer wave that pulses
 * randomly across the whole line based on a chaotic noise generator.
 */
function getStaticWavePath(
	sx: number,
	sy: number,
	tx: number,
	ty: number,
	timePhase: number,
	seedOffset: number,
	amplitudeMult = 1.0,
	steps = 100,
): string {
	const dx = tx - sx;
	const dy = ty - sy;
	const len = Math.sqrt(dx * dx + dy * dy);

	if (len < 5) return `M ${sx} ${sy} L ${tx} ${ty}`;

	const nx = -dy / len;
	const ny = dx / len;

	const parts: string[] = [];

	// Pseudo-random outbursts using multiplied uneven sine waves.
	// By mixing sine waves with prime-number frequencies, the pattern takes a very long time to repeat.
	const tScale = timePhase * 0.4 + seedOffset;

	// Helper to generate isolated chaotic bursts. Returns 0 most of the time, and a curve up to ~1 during bursts.
	const getBurst = (t: number, s1: number, s2: number, s3: number, sharpness: number) => {
		const noise = Math.sin(t * s1) * Math.sin(t * s2) * Math.sin(t * s3);
		return Math.abs(noise) ** sharpness;
	};

	// Low freq band outbursts (wider peaks, slightly more frequent)
	const lowBurst = getBurst(tScale, 1.1, 1.7, 2.3, 3);
	// Mid freq band outbursts (punchier, medium occurrence)
	const midBurst = getBurst(tScale, 1.3, 1.9, 2.9, 5);
	// High freq band outbursts (razor sharp peaks, more sporadic)
	const highBurst = getBurst(tScale, 1.5, 2.1, 3.1, 8);

	// Apply some chaotic cross-talk so when one band peaks heavily, the others react slightly
	const low = lowBurst + midBurst * 0.1 + highBurst * 0.05;
	const mid = midBurst + lowBurst * 0.2 + highBurst * 0.1;
	const high = highBurst + midBurst * 0.2 + lowBurst * 0.05;

	for (let i = 0; i <= steps; i++) {
		const t = i / steps;
		const px = sx + dx * t;
		const py = sy + dy * t;

		const absPos = t * len;
		// envelope so it ends cleanly at the nodes
		const envelope = Math.sin(t * Math.PI);
		const envSq = envelope * envelope;

		let waveDisp = 0;
		if (envSq > 0.01) {
			const lowWave = Math.sin(absPos * 0.05 + timePhase * 10 + seedOffset);
			const midWave = Math.sin(absPos * 0.2 - timePhase * 25 + seedOffset * 2);
			const highWave = Math.sin(absPos * 0.8 + timePhase * 45 + seedOffset * 3);

			const maxAmp = 15 * amplitudeMult;
			const waveForm = lowWave * low + midWave * mid + highWave * high;
			waveDisp = envSq * waveForm * maxAmp;
		}

		const x = px + nx * waveDisp;
		const y = py + ny * waveDisp;

		parts.push(i === 0 ? `M ${x} ${y}` : `L ${x} ${y}`);
	}

	return parts.join(" ");
}

export function EqualizerEdge(props: EdgeProps<EqualizerEdge>) {
	const { sourceX, sourceY, targetX, targetY, data } = props;
	const isEnabled = data?.isEnabled ?? false;
	const variant: EqualizerVariant = data?.variant ?? "traveling";
	const monochrome = data?.monochrome ?? false;

	const [time, setTime] = useState(0);
	const rafRef = useRef<number>(0);
	const prevTimeRef = useRef<number>(0);

	useEffect(() => {
		if (!isEnabled) return;

		const animate = (now: number) => {
			if (prevTimeRef.current === 0) prevTimeRef.current = now;
			const delta = (now - prevTimeRef.current) * 0.001; // in seconds
			prevTimeRef.current = now;

			setTime((t) => t + delta);
			rafRef.current = requestAnimationFrame(animate);
		};

		rafRef.current = requestAnimationFrame(animate);

		return () => {
			cancelAnimationFrame(rafRef.current);
			prevTimeRef.current = 0;
		};
	}, [isEnabled]);

	// Inactive: render a simple straight dotted or muted line
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

	// Pulse travels from -0.2 (before start) to 1.2 (past end) over 3 seconds.
	const cycleTime = time % 3;
	const pulsePosition = (cycleTime / 3) * 1.4 - 0.2;

	// Theme / monochrome styling applied based on props
	const baseStroke = monochrome ? "var(--edge-active)" : "var(--edge-active)";
	const baseWidth = monochrome ? 3 : 1.5; // Bigger static connection line

	const layer1Stroke = monochrome ? "var(--foreground)" : "oklch(0.82 0.18 195)";
	const layer1Width = monochrome ? 1.0 : 1.8; // Thinner dark/white lines

	const layer2Stroke = monochrome ? "var(--foreground)" : "oklch(0.72 0.25 340)";
	const layer2Width = monochrome ? 0.6 : 1.4; // Even thinner offset layer
	const layer2Opacity = monochrome ? 0.7 : 1.0;

	return (
		<>
			{/* 1. Static base track */}
			<path
				d={`M ${sourceX} ${sourceY} L ${targetX} ${targetY}`}
				stroke={baseStroke}
				strokeWidth={baseWidth}
				fill="none"
			/>

			{/* 2. Equalizer burst - Layer 1 */}
			<path
				d={
					variant === "traveling"
						? getTravelingWavePath(sourceX, sourceY, targetX, targetY, pulsePosition, time, 1, 1.0)
						: getStaticWavePath(sourceX, sourceY, targetX, targetY, time, 1, 1.0)
				}
				stroke={layer1Stroke}
				strokeWidth={layer1Width}
				fill="none"
				strokeLinecap="round"
				strokeLinejoin="round"
			/>

			{/* 3. Equalizer burst - Layer 2 */}
			<path
				d={
					variant === "traveling"
						? getTravelingWavePath(sourceX, sourceY, targetX, targetY, pulsePosition, time, 2, 0.65)
						: getStaticWavePath(sourceX, sourceY, targetX, targetY, time, 2, 0.65)
				}
				stroke={layer2Stroke}
				strokeWidth={layer2Width}
				fill="none"
				strokeLinecap="round"
				strokeLinejoin="round"
				opacity={layer2Opacity}
			/>
		</>
	);
}

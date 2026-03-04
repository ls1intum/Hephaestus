import type { Edge, EdgeProps } from "@xyflow/react";
import { useEffect, useRef, useState } from "react";

export type EqualizerVariant = "traveling" | "static";

export type EqualizerEdge = Edge<
	{ isEnabled: boolean; variant?: EqualizerVariant; depth?: number; maxDepth?: number },
	"equalizer"
>;

/** Maximum pixel displacement for any wave outburst to prevent overlapping other nodes */
const MAX_DISPLACEMENT = 18;

/** Global speed factor for wave animations and traveling pulses. Higher = faster. */
const ANIMATION_SPEED_FACTOR = 0.5;

/** Duration of an active outburst cycle for static equalizer (in seconds) */
const STATIC_BURST_DURATION = 2.0;

/** Cooldown between static equalizer outbursts (in seconds) */
const STATIC_BURST_COOLDOWN = 0.8;

/**
 * Normalizes and "squeezes" displacement so it approaches MAX_DISPLACEMENT
 * without hard-clamping (avoiding flat plateaus).
 */
function softLimit(value: number, limit: number): number {
	const x = value / limit;
	const squeezed = x / (1 + Math.abs(x));
	return squeezed * limit;
}

/**
 * Compute an SVG path for a straight line with a localized, traveling audio-wave burst.
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

	if (len < 5) return `M ${sx} ${sy} L ${tx} ${ty}`;

	const nx = -dy / len;
	const ny = dx / len;

	const parts: string[] = [];

	for (let i = 0; i <= steps; i++) {
		const t = i / steps;
		const px = sx + dx * t;
		const py = sy + dy * t;

		const absPos = t * len;
		const absCenter = pulsePosition * len;
		const distFromCenter = Math.abs(absPos - absCenter);

		const envelope = Math.exp(-((distFromCenter / 20) ** 2));

		let waveDisp = 0;
		if (envelope > 0.01) {
			const primary = Math.sin(absPos * 0.3 - timePhase * 15 + seedOffset * 10);
			const secondary = Math.sin(absPos * 0.7 + timePhase * 25 + seedOffset * 20) * 0.5;
			const noise = Math.sin(absPos * 1.5 - timePhase * 40 + seedOffset * 30) * 0.25;

			const waveForm = primary + secondary + noise;
			const maxAmp = 12 * amplitudeMult;
			const rawDisp = envelope * waveForm * maxAmp;
			waveDisp = softLimit(rawDisp, MAX_DISPLACEMENT);
		}

		const x = px + nx * waveDisp;
		const y = py + ny * waveDisp;

		parts.push(i === 0 ? `M ${x} ${y}` : `L ${x} ${y}`);
	}

	return parts.join(" ");
}

/**
 * Compute an SVG path for a static equalizer wave that pulses
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
	minEnergy = 0,
): string {
	const dx = tx - sx;
	const dy = ty - sy;
	const len = Math.sqrt(dx * dx + dy * dy);

	if (len < 5) return `M ${sx} ${sy} L ${tx} ${ty}`;

	const nx = -dy / len;
	const ny = dx / len;

	const parts: string[] = [];
	const tScale = timePhase * 0.4 + seedOffset;

	const getBurst = (t: number, s1: number, s2: number, s3: number, sharpness: number) => {
		const noise = Math.sin(t * s1) * Math.sin(t * s2) * Math.sin(t * s3);
		return Math.abs(noise) ** sharpness;
	};

	const lowBurst = getBurst(tScale, 1.1, 1.7, 2.3, 3);
	const midBurst = getBurst(tScale, 1.3, 1.9, 2.9, 5);
	const highBurst = getBurst(tScale, 1.5, 2.1, 3.1, 8);

	let low = lowBurst + midBurst * 0.1 + highBurst * 0.05;
	const mid = midBurst + lowBurst * 0.2 + highBurst * 0.1;
	const high = highBurst + midBurst * 0.2 + lowBurst * 0.05;

	// Ensure at least some movement if minEnergy floor is provided
	if (minEnergy > 0) {
		const total = low + mid + high;
		if (total < minEnergy) {
			low += minEnergy - total;
		}
	}

	for (let i = 0; i <= steps; i++) {
		const t = i / steps;
		const px = sx + dx * t;
		const py = sy + dy * t;

		const absPos = t * len;
		const envelope = Math.sin(t * Math.PI);
		const envSq = envelope * envelope;

		let waveDisp = 0;
		if (envSq > 0.01) {
			const lowWave = Math.sin(absPos * 0.05 + timePhase * 10 + seedOffset);
			const midWave = Math.sin(absPos * 0.2 - timePhase * 25 + seedOffset * 2);
			const highWave = Math.sin(absPos * 0.8 + timePhase * 45 + seedOffset * 3);

			const maxAmp = 15 * amplitudeMult;
			const waveForm = lowWave * low + midWave * mid + highWave * high;
			const rawDisp = envSq * waveForm * maxAmp;
			waveDisp = softLimit(rawDisp, MAX_DISPLACEMENT);
		}

		const x = px + nx * waveDisp;
		const y = py + ny * waveDisp;

		parts.push(i === 0 ? `M ${x} ${y}` : `L ${x} ${y}`);
	}

	return parts.join(" ");
}

export function EqualizerEdge(props: EdgeProps<EqualizerEdge>) {
	const { sourceX, sourceY, targetX, targetY, data, id } = props;
	const isEnabled = data?.isEnabled ?? false;
	const variant: EqualizerVariant = data?.variant ?? "traveling";

	const seedRef = useRef<number>(0);
	if (seedRef.current === 0) {
		let hash = 0;
		for (let i = 0; i < id.length; i++) {
			hash = (hash << 5) - hash + id.charCodeAt(i);
			hash |= 0;
		}
		seedRef.current = Math.abs(hash) % 1000;
	}

	const [time, setTime] = useState(0);
	const rafRef = useRef<number>(0);
	const prevTimeRef = useRef<number>(0);

	useEffect(() => {
		if (!isEnabled) return;

		const animate = (now: number) => {
			if (prevTimeRef.current === 0) prevTimeRef.current = now;
			const delta = (now - prevTimeRef.current) * 0.001;
			prevTimeRef.current = now;

			setTime((t) => t + delta * ANIMATION_SPEED_FACTOR);
			rafRef.current = requestAnimationFrame(animate);
		};

		rafRef.current = requestAnimationFrame(animate);

		return () => {
			cancelAnimationFrame(rafRef.current);
			prevTimeRef.current = 0;
		};
	}, [isEnabled]);

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

	// Always use monochromatic styling
	const baseStroke = "var(--edge-active)";
	const baseWidth = 3;

	const layer1Stroke = "var(--foreground)";
	const layer1Width = 1.0;

	const layer2Stroke = "var(--foreground)";
	const layer2Width = 0.6;
	const layer2Opacity = 0.7;

	// Pulse and Static timing logic
	let pulsePosition = -1; // Specific to traveling wave
	let chainEnvelope = 1.0; // Global multiplier for any outburst

	if (data?.depth !== undefined) {
		// Chain variant: Every level takes 1.0 seconds.
		// Cycle restarts as soon as the last level is done.
		// maxDepth of 3 means depth 0, 1, 2, 3.
		// Start times: 0, 1, 2, 3. End time of 3 is 4.2.
		const maxD = data.maxDepth ?? 8;
		const cycleDuration = maxD + 1.2;
		const globalPulseTime = time % cycleDuration;
		const startTime = data.depth;
		const duration = 1.2;
		const endTime = startTime + duration;

		if (globalPulseTime >= startTime && globalPulseTime <= endTime) {
			const progress = (globalPulseTime - startTime) / duration;
			pulsePosition = progress * 1.4 - 0.2;
			// Bell curve for static outbursts so they fade in/out during their "turn"
			// Use a steeper curve so it looks punchy
			chainEnvelope = Math.sin(progress * Math.PI);
		} else {
			chainEnvelope = 0;
		}
	} else if (variant === "static") {
		// Static variant: Burst for duration, then wait for cooldown.
		// Shift by seed so everything isn't pulsating at once.
		const cycle = STATIC_BURST_DURATION + STATIC_BURST_COOLDOWN;
		const localTime = (time + seedRef.current * 0.77) % cycle;

		if (localTime < STATIC_BURST_DURATION) {
			const progress = localTime / STATIC_BURST_DURATION;
			// Smooth pulse curve
			chainEnvelope = Math.sin(progress * Math.PI);
		} else {
			chainEnvelope = 0;
		}
	} else {
		// Normal traveling variant: Traveling pulse runs every 3 seconds, Static is always active.
		const cycleTime = time % 3;
		pulsePosition = (cycleTime / 3) * 1.4 - 0.2;
		chainEnvelope = 1.0;
	}

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
						? getTravelingWavePath(
								sourceX,
								sourceY,
								targetX,
								targetY,
								pulsePosition,
								time,
								seedRef.current + 1,
								chainEnvelope,
							)
						: getStaticWavePath(
								sourceX,
								sourceY,
								targetX,
								targetY,
								time,
								seedRef.current + 1,
								chainEnvelope,
								100,
								data?.depth !== undefined ? 0.4 : 0,
							)
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
						? getTravelingWavePath(
								sourceX,
								sourceY,
								targetX,
								targetY,
								pulsePosition,
								time,
								seedRef.current + 2,
								chainEnvelope * 0.65,
							)
						: getStaticWavePath(
								sourceX,
								sourceY,
								targetX,
								targetY,
								time,
								seedRef.current + 2,
								chainEnvelope * 0.65,
								100,
								data?.depth !== undefined ? 0.4 : 0,
							)
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

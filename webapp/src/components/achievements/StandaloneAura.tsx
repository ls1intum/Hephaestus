import { useEffect, useRef, useState } from "react";
import type { UIAchievement } from "@/components/achievements/types";

interface StandaloneAuraProps {
	achievement: UIAchievement;
	size: number;
}

/**
 * Animated "energy field" aura for standalone (floating) achievements.
 * Uses multiple layers of fluctuating circular waves to create a vibrant,
 * mystic effect similar to the equalizer edges.
 */
export function StandaloneAura({ achievement, size }: StandaloneAuraProps) {
	const [time, setTime] = useState(0);
	const rafRef = useRef<number>(0);

	useEffect(() => {
		const animate = (now: number) => {
			setTime(now * 0.001);
			rafRef.current = requestAnimationFrame(animate);
		};
		rafRef.current = requestAnimationFrame(animate);
		return () => cancelAnimationFrame(rafRef.current);
	}, []);

	// Generate a circular wave path
	const getWavePath = (
		radius: number,
		amplitude: number,
		frequency: number,
		phase: number,
		steps = 72,
	) => {
		const points: string[] = [];
		const centerX = 0;
		const centerY = 0;

		for (let i = 0; i <= steps; i++) {
			const angle = (i / steps) * Math.PI * 2;
			// Use multiple harmonics for an organic "equalizer" feel
			const wave =
				Math.sin(angle * frequency + phase) * amplitude +
				Math.sin(angle * frequency * 1.5 - phase * 0.7) * (amplitude * 0.4);

			const r = radius + wave;
			const x = centerX + Math.cos(angle) * r;
			const y = centerY + Math.sin(angle) * r;

			points.push(`${i === 0 ? "M" : "L"} ${x} ${y}`);
		}
		return points.join(" ") + " Z";
	};

	const baseRadius = size / 2;
	const isUnlocked = achievement.status === "unlocked";
	const color = `var(--rarity-${achievement.rarity})`;

	// Define layers with relative parameters that scale with size
	const layers = [
		{
			radius: baseRadius * 1.05,
			amplitude: size * 0.05,
			frequency: 5,
			speed: 2.0,
			opacity: 0.6,
			width: 1.5,
		},
		{
			radius: baseRadius * 1.15,
			amplitude: size * 0.08,
			frequency: 3,
			speed: -1.5,
			opacity: 0.4,
			width: 1.0,
		},
		{
			radius: baseRadius * 1.25,
			amplitude: size * 0.04,
			frequency: 8,
			speed: 3.5,
			opacity: 0.2,
			width: 0.8,
		},
	];

	const viewSize = size * 2;

	return (
		<svg
			style={{
				position: "absolute",
				top: "50%",
				left: "50%",
				transform: "translate(-50%, -50%)",
				width: viewSize,
				height: viewSize,
				pointerEvents: "none",
				overflow: "visible",
				zIndex: -1,
			}}
			viewBox={`${-viewSize / 2} ${-viewSize / 2} ${viewSize} ${viewSize}`}
		>
			<defs>
				<filter id={`aura-glow-${achievement.id}`} x="-50%" y="-50%" width="200%" height="200%">
					<feGaussianBlur stdDeviation="3" result="blur" />
					<feComposite in="SourceGraphic" in2="blur" operator="over" />
				</filter>
			</defs>
			<g filter={`url(#aura-glow-${achievement.id})`}>
				{layers.map((layer, i) => (
					<path
						key={i}
						d={getWavePath(
							layer.radius,
							layer.amplitude * (isUnlocked ? 1.0 : 0.4),
							layer.frequency,
							time * layer.speed,
						)}
						stroke={color}
						strokeWidth={layer.width}
						fill="none"
						opacity={isUnlocked ? layer.opacity : layer.opacity * 0.5}
						strokeLinecap="round"
					/>
				))}
			</g>
		</svg>
	);
}

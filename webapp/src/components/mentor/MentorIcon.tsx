import { useId } from "react";

interface MentorIconProps {
	/** Size of the icon */
	size?: number;
	/** Stroke width of the icon */
	strokeWidth?: number;
	/** Padding around the icon to ensure it fits well in various contexts */
	pad?: number;
	/** Whether to enable animations */
	animated?: boolean;
	/** Highlight streaming state: faster & larger blue antenna ping */
	streaming?: boolean;
	/** Optional CSS class name */
	className?: string;
}

export function MentorIcon({
	size = 16,
	strokeWidth = 2,
	pad = 2,
	animated = true,
	streaming = false,
	className,
}: MentorIconProps) {
	const uniqueId = useId();

	return (
		<svg
			className={className}
			height={size}
			width={size}
			viewBox={`-${pad} -${pad} ${24 + pad * 2} ${24 + pad * 2}`}
			fill="none"
			stroke="currentColor"
			strokeWidth={strokeWidth}
			strokeLinecap="round"
			strokeLinejoin="round"
			style={
				{
					color: "currentColor",
					height: size,
					width: size,
					// Theme-aware CSS custom properties
					"--mentor-shadow-opacity": "0.08",
					"--mentor-blush-opacity": "0.25",
				} as React.CSSProperties
			}
			role="img"
			aria-label="Heph - AI Mentor"
		>
			<title>Heph - AI Mentor</title>

			{animated && (
				<style>
					{`
						.mentor-float-${uniqueId} { 
							transform-origin: 50% 50%; 
							animation: mentor-float-${uniqueId} 4s cubic-bezier(.25,.1,.25,1) infinite; 
						}
						.mentor-wobble-${uniqueId} { 
							transform-origin: 12px 14px; 
							animation: mentor-wobble-${uniqueId} 3.6s cubic-bezier(.25,.1,.25,1) infinite; 
						}
						.mentor-ping-${uniqueId} { 
							opacity: 0.5; 
							transform-origin: 12px 3px; 
							animation: mentor-ping-${uniqueId} 2.2s ease-out infinite; 
						}
						.mentor-ping-fast-${uniqueId} { 
							opacity: 0.7; 
							transform-origin: 12px 3px; 
							animation: mentor-ping-fast-${uniqueId} 1.2s ease-out infinite; 
						}
						.mentor-eyes-${uniqueId} { 
							transform-origin: 12px 13.5px; 
							animation: mentor-blink-${uniqueId} 6.5s steps(1,end) infinite; 
						}
						.mentor-shadow-${uniqueId} { 
							fill: currentColor; 
							opacity: var(--mentor-shadow-opacity, 0.08); 
							transform-origin: 50% 50%; 
							animation: mentor-shadow-${uniqueId} 4s cubic-bezier(.25,.1,.25,1) infinite; 
						}

						@keyframes mentor-float-${uniqueId} { 
							0%, 100% { transform: translateY(0); } 
							50% { transform: translateY(-0.7px); } 
						}
						@keyframes mentor-wobble-${uniqueId} { 
							0%, 100% { transform: rotate(0deg); } 
							50% { transform: rotate(1.25deg); } 
						}
						@keyframes mentor-ping-${uniqueId} { 
							0% { transform: scale(0.6); opacity: 0.45; } 
							70%, 100% { transform: scale(1.9); opacity: 0; } 
						}
						@keyframes mentor-ping-fast-${uniqueId} { 
							0% { transform: scale(0.6); opacity: 0.6; } 
							70%, 100% { transform: scale(2.4); opacity: 0; } 
						}
						@keyframes mentor-blink-${uniqueId} { 
							0%, 86%, 92%, 100% { transform: scaleY(1); } 
							89% { transform: scaleY(0.12); } 
						}
						@keyframes mentor-shadow-${uniqueId} { 
							0%, 100% { transform: scaleX(1); opacity: var(--mentor-shadow-opacity, 0.08); } 
							50% { transform: scaleX(0.85); opacity: calc(var(--mentor-shadow-opacity, 0.08) * 0.6); } 
						}

						@media (prefers-reduced-motion: reduce) {
							.mentor-float-${uniqueId}, 
							.mentor-wobble-${uniqueId}, 
							.mentor-ping-${uniqueId}, 
							.mentor-eyes-${uniqueId}, 
							.mentor-shadow-${uniqueId} { 
								animation: none !important; 
							}
						}

						/* Dark mode adaptations */
						@media (prefers-color-scheme: dark) {
							:root {
								--mentor-shadow-opacity: 0.15;
								--mentor-blush-opacity: 0.2;
							}
						}

						/* Fallback for explicit dark class */
						.dark {
							--mentor-shadow-opacity: 0.15;
							--mentor-blush-opacity: 0.2;
						}
					`}
				</style>
			)}

			{/* Soft ground shadow - theme-aware */}
			<ellipse
				className={animated ? `mentor-shadow-${uniqueId}` : undefined}
				cx="12"
				cy="23.6"
				rx="5"
				ry="1.2"
				fill="currentColor"
				opacity="0.08"
				style={{
					opacity: "var(--mentor-shadow-opacity, 0.08)",
				}}
			/>

			<g className={animated ? `mentor-float-${uniqueId}` : undefined}>
				<g className={animated ? `mentor-wobble-${uniqueId}` : undefined}>
					{/* Antenna: perfectly centered stem + cap */}
					<line x1="12" y1="7.2" x2="12" y2="3.6" />
					<circle cx="12" cy="2.8" r="1" fill="currentColor" />
					<circle
						className={
							animated
								? `${streaming ? `mentor-ping-fast-${uniqueId}` : `mentor-ping-${uniqueId}`}`
								: undefined
						}
						cx="12"
						cy="2.8"
						r={streaming ? 4 : 1.6}
						fill="none"
						style={{ color: streaming ? "#3B82F6" : undefined }}
						opacity={animated ? (streaming ? "1" : "0.5") : "0"}
					/>

					{/* Body: slightly softer corners for friendliness */}
					<rect x="4" y="8" width="16" height="12" rx="3" />

					{/* Side connectors */}
					<path d="M2 14h2" />
					<path d="M20 14h2" />

					{/* Eyes as rounded "dot" strokes */}
					<g className={animated ? `mentor-eyes-${uniqueId}` : undefined}>
						<path d="M8.6 13.5h0.8" />
						<path d="M14.6 13.5h0.8" />
					</g>

					{/* Smile (gentle curve) */}
					<path d="M9.7 16.8c1.1 1.1 3.5 1.1 4.6 0" />

					{/* Optional blush (theme-aware) */}
					<path
						d="M6.8 15.2h0.6"
						opacity="0.25"
						style={{
							opacity: "var(--mentor-blush-opacity, 0.25)",
						}}
					/>
					<path
						d="M16.6 15.2h0.6"
						opacity="0.25"
						style={{
							opacity: "var(--mentor-blush-opacity, 0.25)",
						}}
					/>
				</g>
			</g>
		</svg>
	);
}

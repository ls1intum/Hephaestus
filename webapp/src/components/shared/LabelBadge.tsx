import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";

interface LabelBadgeProps extends React.ComponentPropsWithoutRef<typeof Badge> {
	label: string;
	color?: string; // Hex color without #
}

/**
 * LabelBadge renders a badge styled to match repository label appearance,
 * supporting both light and dark themes with proper color contrast handling.
 */
export function LabelBadge({ label, color, className, ...props }: LabelBadgeProps) {
	// Create style object for the badge
	let style: React.CSSProperties = {
		alignItems: "center",
		borderRadius: "999px",
		fontFamily: "inherit",
		textDecoration: "none",
		maxWidth: "100%",
		whiteSpace: "nowrap",
		fontSize: "12px",
		height: "20px",
		lineHeight: "20px",
		padding: "0px 8px",
		position: "relative",
		minWidth: "0px",
		overflow: "hidden",
		fontWeight: 500,
		borderWidth: "1px",
		borderStyle: "solid",
	};

	if (color) {
		// Parse the RGB values from the hex color
		const r = Number.parseInt(color.slice(0, 2), 16);
		const g = Number.parseInt(color.slice(2, 4), 16);
		const b = Number.parseInt(color.slice(4, 6), 16);

		// Convert RGB to HSL
		const max = Math.max(r, g, b) / 255;
		const min = Math.min(r, g, b) / 255;
		const delta = max - min;

		let h = 0;
		if (delta !== 0) {
			if (max === r / 255) h = ((g / 255 - b / 255) / delta) % 6;
			else if (max === g / 255) h = (b / 255 - r / 255) / delta + 2;
			else h = (r / 255 - g / 255) / delta + 4;
		}
		h = Math.round(h * 60);
		if (h < 0) h += 360;

		const l = (max + min) / 2;
		let s = 0;
		if (delta !== 0) {
			s = delta / (1 - Math.abs(2 * l - 1));
		}
		const hslS = Math.round(s * 100);
		const hslL = Math.round(l * 100);

		// Perceived lightness formula for color contrast
		const perceivedLightness = (r * 0.2126 + g * 0.7152 + b * 0.0722) / 255;
		const lightnessThreshold = 0.6;
		const lightnessSwitch = Math.max(
			0,
			Math.min((perceivedLightness - lightnessThreshold) * -1000, 1),
		);
		const lightenBy = (lightnessThreshold - perceivedLightness) * 100 * lightnessSwitch;

		// Dark mode styling
		style = {
			...style,
			"--label-r": r.toString(),
			"--label-g": g.toString(),
			"--label-b": b.toString(),
			"--label-h": h.toString(),
			"--label-s": hslS.toString(),
			"--label-l": hslL.toString(),
			"--perceived-lightness": perceivedLightness.toString(),
			"--lightness-threshold": "0.6",
			"--background-alpha": "0.18",
			"--border-alpha": "0.3",
			"--lighten-by": lightenBy.toString(),
			backgroundColor: `rgba(${r}, ${g}, ${b}, 0.18)`,
			color:
				perceivedLightness > lightnessThreshold
					? `hsl(${h}, ${hslS}%, ${hslL}%)`
					: `hsl(${h}, ${hslS}%, ${hslL + lightenBy}%)`,
			borderColor: `hsla(${h}, ${hslS}%, ${hslL + lightenBy}%, 0.3)`,
		} as React.CSSProperties;
	}

	// Light mode styling with different contrast formula
	let lightModeStyle = { ...style };

	if (color) {
		const r = Number.parseInt(color.slice(0, 2), 16);
		const g = Number.parseInt(color.slice(2, 4), 16);
		const b = Number.parseInt(color.slice(4, 6), 16);

		const perceivedLightness = (r * 0.2126 + g * 0.7152 + b * 0.0722) / 255;
		const lightModeThreshold = 0.453;
		const borderThreshold = 0.96;
		const textContrast =
			Math.max(0, Math.min(1 / (lightModeThreshold - perceivedLightness), 1)) * 100;
		const borderAlpha = Math.max(0, Math.min((perceivedLightness - borderThreshold) * 100, 1));

		const max = Math.max(r, g, b) / 255;
		const min = Math.min(r, g, b) / 255;
		const delta = max - min;
		let h = 0;
		if (delta !== 0) {
			if (max === r / 255) h = ((g / 255 - b / 255) / delta) % 6;
			else if (max === g / 255) h = (b / 255 - r / 255) / delta + 2;
			else h = (r / 255 - g / 255) / delta + 4;
		}
		h = Math.round(h * 60);
		if (h < 0) h += 360;
		const l = (max + min) / 2;
		let s = 0;
		if (delta !== 0) {
			s = delta / (1 - Math.abs(2 * l - 1));
		}
		const hslS = Math.round(s * 100);
		const hslL = Math.round(l * 100);

		lightModeStyle = {
			...style,
			"--label-r": r.toString(),
			"--label-g": g.toString(),
			"--label-b": b.toString(),
			"--perceived-lightness": perceivedLightness.toString(),
			backgroundColor: `rgb(${r}, ${g}, ${b})`,
			color: `hsl(0deg, 0%, ${textContrast}%)`,
			borderColor: `hsla(${h}, ${hslS}%, ${Math.max(hslL - 25, 0)}%, ${borderAlpha})`,
		} as React.CSSProperties;
	}

	return (
		<>
			<Badge
				variant="outline"
				className={cn(
					"hidden dark:inline-flex items-center border-solid transition-colors hover:no-underline",
					className,
				)}
				style={style}
				{...props}
			>
				{label}
			</Badge>
			<Badge
				variant="outline"
				className={cn(
					"dark:hidden inline-flex items-center border-solid transition-colors hover:no-underline",
					className,
				)}
				style={lightModeStyle}
				{...props}
			>
				{label}
			</Badge>
		</>
	);
}

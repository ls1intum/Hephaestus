import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";

interface GithubBadgeProps extends React.ComponentPropsWithoutRef<typeof Badge> {
  label: string;
  color?: string;  // Hex color without #
}

/**
 * GithubBadge renders a badge with GitHub-like styling, supporting both light and dark themes.
 * This component precisely replicates GitHub's label appearance.
 */
export function GithubBadge({
  label,
  color,
  className,
  ...props
}: GithubBadgeProps) {
  // Create style object for the badge with GitHub's exact styling 
  let style: React.CSSProperties = {
    alignItems: "center",
    borderRadius: "999px",
    cursor: "pointer",
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
    const r = parseInt(color.slice(0, 2), 16);
    const g = parseInt(color.slice(2, 4), 16);
    const b = parseInt(color.slice(4, 6), 16);
    
    // Calculate HSL values
    // Convert RGB to HSL - this is a simplified version
    const max = Math.max(r, g, b) / 255;
    const min = Math.min(r, g, b) / 255;
    const delta = max - min;
    
    // Calculate hue
    let h = 0;
    if (delta !== 0) {
      if (max === r / 255) h = ((g / 255 - b / 255) / delta) % 6;
      else if (max === g / 255) h = (b / 255 - r / 255) / delta + 2;
      else h = (r / 255 - g / 255) / delta + 4;
    }
    h = Math.round(h * 60);
    if (h < 0) h += 360;
    
    // Calculate lightness
    const l = (max + min) / 2;
    
    // Calculate saturation
    let s = 0;
    if (delta !== 0) {
      s = delta / (1 - Math.abs(2 * l - 1));
    }
    
    // Convert to percentage
    const hslS = Math.round(s * 100);
    const hslL = Math.round(l * 100);
    
    // GitHub's exact formula for perceived lightness
    const perceivedLightness = ((r * 0.2126) + (g * 0.7152) + (b * 0.0722)) / 255;
    const lightnessThreshold = 0.6;
    const lightnessSwitch = Math.max(0, Math.min((perceivedLightness - lightnessThreshold) * -1000, 1));
    const lightenBy = ((lightnessThreshold - perceivedLightness) * 100) * lightnessSwitch;
    
    // Set the exact styling from GitHub's implementation
    style = {
      ...style,
      // CSS variables used in GitHub's implementation
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
      
      // Apply the calculated styles
      backgroundColor: `rgba(${r}, ${g}, ${b}, 0.18)`,
      color: perceivedLightness > lightnessThreshold ? 
        `hsl(${h}, ${hslS}%, ${hslL}%)` : 
        `hsl(${h}, ${hslS}%, ${hslL + lightenBy}%)`,
      borderColor: `hsla(${h}, ${hslS}%, ${hslL + lightenBy}%, 0.3)`,
    } as React.CSSProperties;
  }

  // Create separate styles for light mode with GitHub's exact formula
  let lightModeStyle = { ...style };
  
  if (color) {
    // Parse the RGB values from the hex color again
    const r = parseInt(color.slice(0, 2), 16);
    const g = parseInt(color.slice(2, 4), 16);
    const b = parseInt(color.slice(4, 6), 16);
    
    // Calculate the perceived lightness exactly as GitHub does for light mode
    const perceivedLightness = ((r * 0.2126) + (g * 0.7152) + (b * 0.0722)) / 255;
    
    // GitHub's light mode thresholds
    const lightModeThreshold = 0.453;
    const borderThreshold = 0.96;
    
    // Text color exactly as GitHub calculates it
    // color: hsl(0deg, 0%, calc(max(0, min(calc((1/(0.453 - perceivedLightness))), 1)) * 100%));
    const textContrast = Math.max(0, Math.min(1 / (lightModeThreshold - perceivedLightness), 1)) * 100;
    
    // Border color using GitHub's exact formula
    // border-color: hsla(180, calc(100 * 1%), calc((70 - 25) * 1%), max(0, min(calc((perceivedLightness - 0.96) * 100), 1)));
    const borderAlpha = Math.max(0, Math.min((perceivedLightness - borderThreshold) * 100, 1));
    
    // Convert RGB to HSL - this is a simplified version
    const max = Math.max(r, g, b) / 255;
    const min = Math.min(r, g, b) / 255;
    const delta = max - min;
    
    // Calculate hue
    let h = 0;
    if (delta !== 0) {
      if (max === r / 255) h = ((g / 255 - b / 255) / delta) % 6;
      else if (max === g / 255) h = (b / 255 - r / 255) / delta + 2;
      else h = (r / 255 - g / 255) / delta + 4;
    }
    h = Math.round(h * 60);
    if (h < 0) h += 360;
    
    // Calculate lightness and saturation
    const l = (max + min) / 2;
    let s = 0;
    if (delta !== 0) {
      s = delta / (1 - Math.abs(2 * l - 1));
    }
    const hslS = Math.round(s * 100);
    const hslL = Math.round(l * 100);
    
    // Light mode badge with exact GitHub styling
    lightModeStyle = {
      ...style,
      "--label-r": r.toString(),
      "--label-g": g.toString(),
      "--label-b": b.toString(),
      "--perceived-lightness": perceivedLightness.toString(),
      
      // Apply GitHub's exact light mode styles
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
          className
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
          className
        )}
        style={lightModeStyle}
        {...props}
      >
        {label}
      </Badge>
    </>
  );
}
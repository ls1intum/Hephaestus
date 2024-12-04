import type { Config } from "tailwindcss"

const config = {
  darkMode: ["class"],
  content: [
    __dirname + '/src/**/*.{html,ts}',
    __dirname + '/.storybook/**/*.{html,ts}'
  ],
  prefix: "",
  theme: {
    container: {
      center: true,
      padding: "2rem",
      screens: {
        "2xl": "1400px",
      },
    },
    extend: {
      colors: {
        border: "hsl(var(--border))",
        input: "hsl(var(--input))",
        ring: "hsl(var(--ring))",
        background: "hsl(var(--background))",
        foreground: "hsl(var(--foreground))",
        primary: {
          DEFAULT: "hsl(var(--primary))",
          foreground: "hsl(var(--primary-foreground))",
        },
        secondary: {
          DEFAULT: "hsl(var(--secondary))",
          foreground: "hsl(var(--secondary-foreground))",
        },
        destructive: {
          DEFAULT: "hsl(var(--destructive))",
          foreground: "hsl(var(--destructive-foreground))",
        },
        muted: {
          DEFAULT: "hsl(var(--muted))",
          foreground: "hsl(var(--muted-foreground))",
        },
        accent: {
          DEFAULT: "hsl(var(--accent))",
          foreground: "hsl(var(--accent-foreground))",
        },
        popover: {
          DEFAULT: "hsl(var(--popover))",
          foreground: "hsl(var(--popover-foreground))",
        },
        card: {
          DEFAULT: "hsl(var(--card))",
          foreground: "hsl(var(--card-foreground))",
        },
        github: {
          foreground: "var(--fgColor-default)",
          background: "var(--bgColor-default)",
          muted: {
            DEFAULT: "var(--bgColor-muted)",
            foreground: "var(--fgColor-muted)",
          },
          onEmphasis: {
            DEFAULT: "var(--bgColor-emphasis)",
            foreground: "var(--fgColor-onEmphasis)",
          },
          onInverse: {
            DEFAULT: "var(--bgColor-inverse)",
            foreground: "var(--fgColor-onInverse)",
          },
          white: {
            DEFAULT: "var(--bgColor-white)",
            foreground: "var(--fgColor-white)",
          },
          black: {
            DEFAULT: "var(--bgColor-black)",
            foreground: "var(--fgColor-black)",
          },
          disabled: {
            DEFAULT: "var(--bgColor-disabled)",
            foreground: "var(--fgColor-disabled)",
          },
          link: {
            DEFAULT: "var(--bgColor-link)",
            foreground: "var(--fgColor-link)",
          },
          neutral: {
            DEFAULT: "var(--bgColor-neutral)",
            foreground: "var(--fgColor-neutral)",
          },
          accent: {
            DEFAULT: "var(--bgColor-accent)",
            foreground: "var(--fgColor-accent)",
          },
          success: {
            DEFAULT: "var(--bgColor-success)",
            foreground: "var(--fgColor-success)",
          },
          open: {
            DEFAULT: "var(--bgColor-open)",
            foreground: "var(--fgColor-open)",
          },
          attention: {
            DEFAULT: "var(--bgColor-attention)",
            foreground: "var(--fgColor-attention)",
          },
          severe: {
            DEFAULT: "var(--bgColor-severe)",
            foreground: "var(--fgColor-severe)",
          },
          danger: {
            DEFAULT: "var(--bgColor-danger)",
            foreground: "var(--fgColor-danger)",
          },
          closed: {
            DEFAULT: "var(--bgColor-closed)",
            foreground: "var(--fgColor-closed)",
          },
          done: {
            DEFAULT: "var(--bgColor-done)",
            foreground: "var(--fgColor-done)",
          },
          upsell: {
            DEFAULT: "var(--bgColor-upsell)",
            foreground: "var(--fgColor-upsell)",
          },
          sponsors: {
            DEFAULT: "var(--bgColor-sponsors)",
            foreground: "var(--fgColor-sponsors)",
          },
        },
        league: {
          bronze: {
            DEFAULT: "hsl(var(--league-bronze))",
          },
          silver: {
            DEFAULT: "hsl(var(--league-silver))",
          },
          gold: {
            DEFAULT: "hsl(var(--league-gold))",
          },
          diamond: {
            DEFAULT: "hsl(var(--league-diamond))",
          },
          master: {
            DEFAULT: "hsl(var(--league-master))",
          },
        }
      },
      borderRadius: {
        lg: "var(--radius)",
        md: "calc(var(--radius) - 2px)",
        sm: "calc(var(--radius) - 4px)",
      },
      keyframes: {
        "accordion-down": {
          from: { height: "0" },
          to: { height: "var(--radix-accordion-content-height)" },
        },
        "accordion-up": {
          from: { height: "var(--radix-accordion-content-height)" },
          to: { height: "0" },
        },
      },
      animation: {
        "accordion-down": "accordion-down 0.2s ease-out",
        "accordion-up": "accordion-up 0.2s ease-out",
      },
    },
  },
  plugins: [
    require("tailwindcss-animate"),
    require('@tailwindcss/typography'),
  ],
} satisfies Config

export default config

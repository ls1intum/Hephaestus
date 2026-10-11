# Hephaestus design

Hephaestus should feel like a calm, precise view of engineering work. Developers come here to understand feedback, inspect its evidence, and choose what to do next. Quiet surfaces make the work easier to read. Emphasis belongs to an actual action, state, or useful distinction.

## Foundations

The existing system is the starting point:

| Fact | Authoritative owner |
| --- | --- |
| Application colors, light/dark roles and radius | `src/styles/theme-tokens.css` |
| Application fonts and integration palettes | `src/styles.css` |
| Component family and registry style | `components.json` |
| Control geometry and semantic variants | `src/components/ui/` |
| Brand artwork | `../docs/contributor/brand-assets.mdx` |
| Product language | `../docs/contributor/practice-feedback-language.md` |

These owners define the values. A CLI fallback preset or an external design reference does not replace them.

## Reading data

A table should let someone identify a row, interpret a figure, and inspect the associated work. Identity deserves readable space at a narrow width. A pinned name must still leave room to discover the scrolling figures. A truncated visible name retains its full accessible label.

Visible headers name visual data. Icons and color support those names; they should not require someone to guess what a figure represents. Zero, unknown, stale data and an empty result mean different things. Their existing domain display owners preserve those meanings.

Use the available page width for data and the existing prose measure for explanation. Group controls near what they affect. On a small screen, the primary content should follow the minimum explanation needed to interpret it; compress spacing before removing meaning.

## Composition owners

| Need | Existing recipe |
| --- | --- |
| Standard page | `StandardPageSurface`, `PageLayout`, `PageHeader`, `Section` in `src/components/layout/` |
| Scrollable data | `Table` and the feature's table composition; the table owns horizontal scroll |
| Empty, error and pending regions | `AGENTS.md` § Loading and errors |
| Contextual levels | `AGENTS.md` § Drawer or route and § Panel regions |
| Practice surface emphasis | `AGENTS.md` § Practice surfaces palette |
| Observable component states | Existing feature stories and `design-system-axes.stories.tsx` |

Standard page recipes apply where the route uses the standard surface. Fullscreen and existing specialized surfaces keep their own layout owners. Contextual navigation follows the individual level's supported contract; this document does not add URL or parent-navigation behavior.

## Evolving the system

Improve one readable composition at a time. Start from the actual screen and its existing Storybook owner. A new token, variant or shared recipe needs a named product constraint that the current system cannot express. Keep brand inspiration separate from that constraint.

Engineering, accessibility, interaction, loading and verification requirements remain in `AGENTS.md` and its linked skills. Storybook is the living reference for how the real components compose; a second showcase route is unnecessary while those owners cover the need.

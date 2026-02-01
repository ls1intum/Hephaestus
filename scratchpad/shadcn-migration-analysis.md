# Shadcn UI Migration Analysis Report

**Generated:** January 31, 2026
**Project:** Hephaestus3
**Current State:** Radix UI-based Shadcn components with Tailwind v4

---

## Executive Summary

The Hephaestus3 webapp uses a comprehensive Shadcn UI setup with 53 component files built on Radix UI primitives. The codebase is already using modern patterns including:
- Tailwind CSS v4.1.18
- React 19.2.3
- Function components with `data-slot` attributes (not forwardRef)
- OKLCH color system

Shadcn has released major updates allowing users to choose between **Radix UI** and **Base UI** as the underlying primitive library. This report analyzes the current state and provides migration guidance.

---

## Part 1: Current Codebase Analysis

### 1.1 Configuration

**File:** `/webapp/components.json`
```json
{
  "$schema": "https://ui.shadcn.com/schema.json",
  "style": "new-york",
  "rsc": false,
  "tsx": true,
  "tailwind": {
    "config": "",
    "css": "src/styles.css",
    "baseColor": "zinc",
    "cssVariables": true,
    "prefix": ""
  },
  "aliases": {
    "components": "@/components",
    "utils": "@/lib/utils",
    "ui": "@/components/ui",
    "lib": "@/lib",
    "hooks": "@/hooks"
  },
  "iconLibrary": "lucide"
}
```

**Observations:**
- Uses `new-york` style (the default for new projects)
- No RSC (React Server Components) - using Vite
- Lucide icons
- Standard alias configuration

### 1.2 Dependencies (Radix UI Packages)

The webapp uses **24 Radix UI packages**:

| Package | Version |
|---------|---------|
| @radix-ui/react-accordion | 1.2.12 |
| @radix-ui/react-alert-dialog | 1.1.15 |
| @radix-ui/react-aspect-ratio | 1.1.8 |
| @radix-ui/react-avatar | 1.1.11 |
| @radix-ui/react-checkbox | 1.3.3 |
| @radix-ui/react-collapsible | 1.1.12 |
| @radix-ui/react-context-menu | 2.2.16 |
| @radix-ui/react-dialog | 1.1.15 |
| @radix-ui/react-dropdown-menu | 2.1.16 |
| @radix-ui/react-hover-card | 1.1.15 |
| @radix-ui/react-label | 2.1.8 |
| @radix-ui/react-menubar | 1.1.16 |
| @radix-ui/react-navigation-menu | 1.2.14 |
| @radix-ui/react-popover | 1.1.15 |
| @radix-ui/react-progress | 1.1.8 |
| @radix-ui/react-radio-group | 1.3.8 |
| @radix-ui/react-scroll-area | 1.2.10 |
| @radix-ui/react-select | 2.2.6 |
| @radix-ui/react-separator | 1.1.8 |
| @radix-ui/react-slider | 1.3.6 |
| @radix-ui/react-slot | 1.2.4 |
| @radix-ui/react-switch | 1.2.6 |
| @radix-ui/react-tabs | 1.1.13 |
| @radix-ui/react-toggle | 1.1.10 |
| @radix-ui/react-toggle-group | 1.1.11 |
| @radix-ui/react-tooltip | 1.2.8 |

**Related Dependencies:**
- `class-variance-authority`: 0.7.1
- `clsx`: 2.1.1
- `tailwind-merge`: 3.4.0
- `cmdk`: 1.1.1 (Command component)
- `sonner`: 2.0.7 (Toast notifications)
- `vaul`: 1.1.2 (Drawer component)
- `embla-carousel-react`: 8.6.0 (Carousel)
- `react-day-picker`: 9.13.0 (Calendar)
- `input-otp`: 1.4.2 (OTP Input)
- `react-resizable-panels`: 3.0.6 (Resizable)
- `recharts`: 2.15.4 (Charts)

### 1.3 Components Inventory

**Total:** 53 component files in `/webapp/src/components/ui/`

#### Standard Shadcn Components (32 files using Radix):
| Component | Radix Package | asChild Usage |
|-----------|---------------|---------------|
| accordion.tsx | @radix-ui/react-accordion | No |
| alert-dialog.tsx | @radix-ui/react-alert-dialog | No |
| aspect-ratio.tsx | @radix-ui/react-aspect-ratio | No |
| avatar.tsx | @radix-ui/react-avatar | No |
| checkbox.tsx | @radix-ui/react-checkbox | No |
| collapsible.tsx | @radix-ui/react-collapsible | No |
| context-menu.tsx | @radix-ui/react-context-menu | No |
| dialog.tsx | @radix-ui/react-dialog | No |
| dropdown-menu.tsx | @radix-ui/react-dropdown-menu | No |
| hover-card.tsx | @radix-ui/react-hover-card | No |
| label.tsx | @radix-ui/react-label | No |
| menubar.tsx | @radix-ui/react-menubar | No |
| navigation-menu.tsx | @radix-ui/react-navigation-menu | No |
| popover.tsx | @radix-ui/react-popover | No |
| progress.tsx | @radix-ui/react-progress | No |
| radio-group.tsx | @radix-ui/react-radio-group | No |
| scroll-area.tsx | @radix-ui/react-scroll-area | No |
| select.tsx | @radix-ui/react-select | Yes (1) |
| separator.tsx | @radix-ui/react-separator | No |
| sheet.tsx | @radix-ui/react-dialog | No |
| slider.tsx | @radix-ui/react-slider | No |
| switch.tsx | @radix-ui/react-switch | No |
| tabs.tsx | @radix-ui/react-tabs | No |
| toggle.tsx | @radix-ui/react-toggle | No |
| toggle-group.tsx | @radix-ui/react-toggle-group | No |
| tooltip.tsx | @radix-ui/react-tooltip | No |

#### Components Using @radix-ui/react-slot (asChild pattern):
| Component | asChild Occurrences |
|-----------|---------------------|
| button.tsx | 3 |
| badge.tsx | 3 |
| breadcrumb.tsx | 3 |
| button-group.tsx | 3 |
| item.tsx | 3 |
| sidebar.tsx | 16 |

#### Non-Radix Components:
| Component | Dependencies |
|-----------|--------------|
| alert.tsx | None (pure Tailwind) |
| calendar.tsx | react-day-picker |
| card.tsx | None (pure Tailwind) |
| carousel.tsx | embla-carousel-react |
| chart.tsx | recharts |
| command.tsx | cmdk |
| drawer.tsx | vaul |
| empty.tsx | None (custom) |
| field.tsx | Uses Label, Separator |
| input.tsx | None (pure Tailwind) |
| input-group.tsx | Uses Input, Textarea, Button |
| input-otp.tsx | input-otp |
| kbd.tsx | None (pure Tailwind) |
| pagination.tsx | None (uses Button) |
| resizable.tsx | react-resizable-panels |
| skeleton.tsx | None (pure Tailwind) |
| sonner.tsx | sonner |
| spinner.tsx | lucide-react |
| table.tsx | None (pure Tailwind) |
| textarea.tsx | None (pure Tailwind) |

### 1.4 Custom Components (Project-Specific)

These components are NOT standard Shadcn and contain project-specific logic:

1. **empty.tsx** - Empty state component with variants
2. **field.tsx** - Form field wrapper with validation support
3. **kbd.tsx** - Keyboard shortcut display
4. **button-group.tsx** - Button grouping with separators
5. **input-group.tsx** - Input with addons
6. **item.tsx** - List item component with media, content, actions
7. **spinner.tsx** - Loading spinner

### 1.5 Customizations Detected

1. **Button Component:**
   - Added `icon-sm`, `icon-lg`, and `none` size variants
   - Custom focus styling

2. **Dialog Component:**
   - Added `showCloseButton` prop

3. **Select Component:**
   - Added `size` prop ("sm" | "default")
   - Custom alignment defaults

4. **Tooltip Component:**
   - Changed default `delayDuration` to 0
   - Added arrow styling
   - Wraps each Tooltip in its own Provider

5. **Sidebar Component:**
   - Extensive customization with context, state management
   - Custom keyboard shortcuts

### 1.6 Component Usage Statistics

- **Total imports across codebase:** 223 component imports
- **Files importing UI components:** 119 files
- **Most used components:** Button, Dialog, Card, Select, Tooltip

---

## Part 2: Latest Shadcn Updates (2025-2026)

### 2.1 Major Changes

#### January 2026: Base UI Support
- Full documentation for Base UI components
- Choice between Radix UI or Base UI primitives
- Same component API regardless of primitive library

#### January 2026: RTL Support
- First-class right-to-left layout support
- Physical CSS classes converted to logical equivalents at install time
- New `pnpm dlx shadcn@latest migrate rtl` command

#### December 2025: npx shadcn create
- Customize component library, icons, base color, theme, fonts
- Five visual styles: Vega, Nova, Maia, Lyra, Mira
- Components auto-detect library preference from registries

#### October 2025: Registry Directory
- Published registry for community components
- Built into CLI

#### February 2025: Tailwind v4
- Full Tailwind v4 support
- OKLCH color system
- `tailwindcss-animate` deprecated for `tw-animate-css`
- Toast component deprecated for Sonner

### 2.2 Key API Changes: Radix vs Base UI

| Feature | Radix UI | Base UI |
|---------|----------|---------|
| Composition | `asChild` prop | `render` prop |
| Slot handling | `<Slot>` component | `useRender` hook |
| Positioning | Props on Content | Dedicated `Positioner` wrapper |
| Package structure | Multiple packages | Single `@base-ui-components/react` |

**Example - Button with composition:**

```tsx
// Radix UI (current)
<Button asChild>
  <a href="/contact">Contact</a>
</Button>

// Base UI (new)
<Button render={<a href="/contact" />}>Contact</Button>
```

**Example - Popover positioning:**

```tsx
// Radix UI (current)
<PopoverContent side="bottom" align="start">

// Base UI (new)
<PopoverPositioner side="bottom" align="start">
  <PopoverContent>
```

---

## Part 3: Component Comparison Matrix

| Component | Current (Radix) | Available (Base UI) | Migration Impact |
|-----------|-----------------|---------------------|------------------|
| Accordion | Yes | Yes | Low |
| Alert | Yes | Yes | None (no primitives) |
| Alert Dialog | Yes | Yes | Medium |
| Aspect Ratio | Yes | Yes | Low |
| Avatar | Yes | Yes | Low |
| Badge | Yes | Yes | Low (asChild -> render) |
| Breadcrumb | Yes | Yes | Low (asChild -> render) |
| Button | Yes | Yes | Low (asChild -> render) |
| Button Group | Yes (custom) | Yes | Low |
| Calendar | Yes | Yes | None (react-day-picker) |
| Card | Yes | Yes | None (no primitives) |
| Carousel | Yes | Yes | None (embla) |
| Chart | Yes | Yes | None (recharts) |
| Checkbox | Yes | Yes | Low |
| Collapsible | Yes | Yes | Low |
| Combobox | No | Yes | New component available |
| Command | Yes | Yes | None (cmdk) |
| Context Menu | Yes | Yes | Medium |
| Data Table | Yes | Yes | None (tanstack) |
| Date Picker | Yes | Yes | None (react-day-picker) |
| Dialog | Yes | Yes | Medium |
| Direction | No | Yes | New component available |
| Drawer | Yes | Yes | None (vaul) |
| Dropdown Menu | Yes | Yes | Medium |
| Empty | Yes (custom) | Yes | None |
| Field | Yes (custom) | Yes | None |
| Hover Card | Yes | Yes | Medium |
| Input | Yes | Yes | None (no primitives) |
| Input Group | Yes (custom) | Yes | None |
| Input OTP | Yes | Yes | None (input-otp) |
| Item | Yes (custom) | Yes (custom) | Low (asChild -> render) |
| Kbd | Yes (custom) | Yes | None |
| Label | Yes | Yes | Low |
| Menubar | Yes | Yes | High |
| Native Select | No | Yes | New component available |
| Navigation Menu | Yes | Yes | High |
| Pagination | Yes | Yes | None |
| Popover | Yes | Yes | Medium |
| Progress | Yes | Yes | Low |
| Radio Group | Yes | Yes | Low |
| Resizable | Yes | Yes | None (react-resizable-panels) |
| Scroll Area | Yes | Yes | Medium |
| Select | Yes | Yes | High |
| Separator | Yes | Yes | Low |
| Sheet | Yes | Yes | Medium |
| Sidebar | Yes | Yes | Medium (many asChild) |
| Skeleton | Yes | Yes | None |
| Slider | Yes | Yes | Low |
| Sonner | Yes | Yes | None |
| Spinner | Yes (custom) | Yes | None |
| Switch | Yes | Yes | Low |
| Table | Yes | Yes | None |
| Tabs | Yes | Yes | Low |
| Textarea | Yes | Yes | None |
| Toggle | Yes | Yes | Low |
| Toggle Group | Yes | Yes | Low |
| Tooltip | Yes | Yes | Medium |
| Typography | No | Yes | New component available |

### Migration Impact Legend:
- **None:** No primitive changes needed
- **Low:** Simple prop changes or minimal updates
- **Medium:** Structural changes (positioning, composition)
- **High:** Significant API differences, recommend fresh install

---

## Part 4: Breaking Changes Analysis

### 4.1 Changes Affecting This Codebase

#### High Priority (Structural Changes)

1. **Select Component**
   - Base UI requires different value handling
   - Current customizations (`size` prop) need adaptation
   - `<Select.Value />` behavior differs - needs `items` prop

2. **Menubar Component**
   - Major structural differences
   - 16 Radix imports, complex interactions

3. **Navigation Menu**
   - Significant API differences
   - Complex trigger/content relationships

#### Medium Priority (Positioning + Composition)

4. **Dialog/Sheet Components**
   - Need `Positioner` wrapper
   - Close button implementation differs
   - Custom `showCloseButton` prop needs review

5. **Dropdown Menu/Context Menu**
   - Positioning architecture change
   - Label placement in groups differs

6. **Tooltip/Popover/Hover Card**
   - Positioning requires wrapper components
   - Arrow implementation may differ

7. **Sidebar Component**
   - 16 `asChild` usages need conversion to `render` prop
   - Complex state management unaffected

#### Low Priority (Simple Prop Changes)

8. **Button/Badge/Breadcrumb**
   - `asChild` -> `render` prop conversion
   - Approximately 12 occurrences

9. **Item Component (Custom)**
   - 3 `asChild` usages

10. **All Other Radix Components**
    - Standard prop updates
    - Mostly straightforward

### 4.2 No Breaking Changes (Safe)

These components use external libraries or pure Tailwind:
- Calendar (react-day-picker)
- Carousel (embla)
- Chart (recharts)
- Command (cmdk)
- Drawer (vaul)
- Input OTP (input-otp)
- Resizable (react-resizable-panels)
- Sonner (sonner)
- All pure Tailwind components

---

## Part 5: Migration Path Recommendations

### Option A: Stay on Radix UI (Recommended for Now)

**Rationale:**
1. No breaking changes for existing Radix components
2. Codebase is already modern (Tailwind v4, React 19, function components)
3. Base UI migration codemods not yet available
4. High customization would require manual migration

**Actions:**
1. Update to latest Shadcn CLI
2. Ensure `components.json` is current
3. Add new components using Radix when needed

### Option B: Migrate to Base UI

**When to Consider:**
- Need Base UI-specific features (multi-select, non-dialog Combobox)
- Prefer single dependency over multiple @radix-ui/* packages
- Starting fresh is acceptable for UI layer

**Migration Steps:**

1. **Preparation**
   ```bash
   # Backup current components
   cp -r webapp/src/components/ui webapp/src/components/ui-backup

   # Document all customizations
   ```

2. **Generate New Components**
   ```bash
   # Create fresh project config for reference
   npx shadcn create --base=base

   # Or initialize with Base UI
   npx shadcn init --primitive base
   ```

3. **Component-by-Component Migration**

   Priority order:
   1. Low impact components first (pure Tailwind, external libraries)
   2. Simple Radix components (Checkbox, Switch, Progress)
   3. Components with `asChild` usage
   4. Complex components (Select, Dialog, Menu systems)

4. **Key Transformations**

   ```tsx
   // Before (Radix)
   import { Slot } from "@radix-ui/react-slot";
   const Comp = asChild ? Slot : "button";

   // After (Base UI)
   import { useRender } from "@base-ui/react/use-render";
   return useRender({ defaultTagName: 'button', render, props });
   ```

5. **Preserve Customizations**
   - Button: `icon-sm`, `icon-lg`, `none` sizes
   - Dialog: `showCloseButton` prop
   - Select: `size` prop
   - Tooltip: `delayDuration=0`, arrow styling

### Option C: Hybrid Approach

**Strategy:** Keep Radix for existing, use Base UI for new components

**Not Recommended** because:
- Two primitive libraries = confusion
- Inconsistent patterns across codebase
- Increased bundle size

---

## Part 6: Available Tools and Resources

### 6.1 Codemods

| Tool | Status | Purpose |
|------|--------|---------|
| `@tailwindcss/upgrade@next` | Available | Tailwind v4 migration |
| `react-codemod remove-forward-ref` | Available | Remove forwardRef patterns |
| `shadcn migrate radix-ui` | In Development | Radix package consolidation |
| Base UI migration codemod | Not Available | Manual migration required |

### 6.2 CLI Commands

```bash
# Initialize new project with Base UI
npx shadcn create --base=base

# Initialize with RTL support
pnpm dlx shadcn@latest init --rtl

# Migrate existing project to RTL
pnpm dlx shadcn@latest migrate rtl

# Add components (auto-detects library)
npx shadcn add button dialog

# Update dependencies
pnpm up "@radix-ui/*" cmdk lucide-react recharts tailwind-merge clsx --latest
```

### 6.3 Documentation Links

- [Shadcn Changelog](https://ui.shadcn.com/docs/changelog)
- [Base UI Migration Guide](https://basecn.dev/docs/get-started/migrating-from-radix-ui)
- [Tailwind v4 Migration](https://ui.shadcn.com/docs/tailwind-v4)
- [Base UI Documentation](https://base-ui.com)
- [GitHub Discussion #6248](https://github.com/shadcn-ui/ui/discussions/6248)

---

## Part 7: Action Items

### Immediate (No Migration)
- [ ] Verify all Radix packages are latest versions
- [ ] Review `tailwindcss-animate` usage (deprecated)
- [ ] Ensure Sonner is used instead of Toast

### Short Term (Preparation)
- [ ] Document all component customizations
- [ ] Create component usage audit
- [ ] Set up component testing

### Medium Term (If Migrating)
- [ ] Create migration branch
- [ ] Start with low-impact components
- [ ] Test each component thoroughly before proceeding

### Long Term (Strategy)
- [ ] Monitor Base UI codemod development
- [ ] Evaluate new components (Combobox, Native Select, Typography)
- [ ] Consider visual style options (Vega, Nova, Maia, Lyra, Mira)

---

## Appendix A: Component File Locations

All UI components are located in:
```
/Users/felixdietrich/Documents/Hephaestus3/webapp/src/components/ui/
```

Configuration file:
```
/Users/felixdietrich/Documents/Hephaestus3/webapp/components.json
```

Styles:
```
/Users/felixdietrich/Documents/Hephaestus3/webapp/src/styles.css
```

Utils:
```
/Users/felixdietrich/Documents/Hephaestus3/webapp/src/lib/utils.ts
```

## Appendix B: Dependency Consolidation (If Migrating to Base UI)

Current (24+ packages):
```json
"@radix-ui/react-accordion": "1.2.12",
"@radix-ui/react-alert-dialog": "1.1.15",
// ... 22 more packages
```

After Base UI migration (1 package):
```json
"@base-ui-components/react": "^1.0.0"
```

---

*Report generated by analyzing the Hephaestus3 codebase and researching the latest Shadcn UI updates.*

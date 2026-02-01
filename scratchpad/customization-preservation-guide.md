# Radix UI to Base UI Migration: Customization Preservation Guide

This document catalogs all customizations in the current Shadcn components that MUST be preserved during the migration from Radix UI to Base UI.

---

## Table of Contents
1. [Button Component](#1-button-component)
2. [Dialog Component](#2-dialog-component)
3. [Select Component](#3-select-component)
4. [Tooltip Component](#4-tooltip-component)
5. [Sidebar Component](#5-sidebar-component)
6. [Badge Component](#6-badge-component)
7. [Breadcrumb Component](#7-breadcrumb-component)
8. [ButtonGroup Component](#8-buttongroup-component)
9. [Item Component](#9-item-component)

---

## 1. Button Component

**File:** `/webapp/src/components/ui/button.tsx`

### 1.1 Custom Size Variants: `icon-sm`, `icon-lg`, `none`

#### What It Does
- **`icon-sm`**: Provides a compact 8x8 (32px) square button for small icon buttons
- **`icon-lg`**: Provides a larger 10x10 (40px) square button for prominent icon buttons
- **`none`**: Removes all height and padding constraints (`h-auto p-0`) for completely unstyled button dimensions

#### Why It Was Added
The standard Shadcn button only includes `icon` (36px square). The team needed:
- Smaller icon buttons for dense UIs (toolbars, inline actions)
- Larger icon buttons for primary actions
- A "reset" variant for wrapping custom content without button styling affecting layout

#### Current Implementation
```typescript
size: {
  default: "h-9 px-4 py-2 has-[>svg]:px-3",
  sm: "h-8 rounded-md gap-1.5 px-3 has-[>svg]:px-2.5",
  lg: "h-10 rounded-md px-6 has-[>svg]:px-4",
  icon: "size-9",
  "icon-sm": "size-8",      // CUSTOM
  "icon-lg": "size-10",     // CUSTOM
  none: "h-auto p-0",       // CUSTOM
}
```

#### How to Preserve in Base UI
Base UI buttons are unstyled by default, so these variants are purely CSS-based using CVA. No Radix-specific logic is involved.

**Preservation Strategy:**
- Keep the exact CVA configuration unchanged
- The `size` variants are class-based only; they will work identically with Base UI

---

### 1.2 Custom Focus Styling

#### What It Does
Uses a 3-ring focus indicator with a semi-transparent ring color for better visibility across light/dark themes.

#### Why It Was Added
Provides consistent, accessible focus indicators that work well on varied backgrounds.

#### Current Implementation
```typescript
"outline-none focus-visible:border-ring focus-visible:ring-ring/50 focus-visible:ring-[3px]"
```

#### How to Preserve in Base UI
This is pure Tailwind CSS - no Radix dependency.

**Preservation Strategy:**
- Copy these classes to the Base UI button implementation
- Ensure `--ring` CSS variable is defined in the theme

---

### 1.3 asChild Pattern with Radix Slot

#### What It Does
Allows the button to render as a child component (e.g., Link) while maintaining button styling.

#### Current Implementation
```typescript
import { Slot } from "@radix-ui/react-slot";
// ...
const Comp = asChild ? Slot : "button";
```

#### How to Preserve in Base UI
**CRITICAL:** Base UI does not have a `Slot` equivalent built-in.

**Preservation Strategy:**
- Option A: Continue importing `@radix-ui/react-slot` as a standalone utility (it has no runtime dependencies on other Radix packages)
- Option B: Use the `render` prop pattern that Base UI supports
- Option C: Implement a custom Slot component using `React.cloneElement`

**Recommended:** Option A - `@radix-ui/react-slot` is lightweight (~2KB) and battle-tested

---

## 2. Dialog Component

**File:** `/webapp/src/components/ui/dialog.tsx`

### 2.1 `showCloseButton` Prop

#### What It Does
Conditionally renders the close (X) button in the dialog header. Defaults to `true`.

#### Why It Was Added
Some dialog use cases require:
- Custom close behavior (confirmation before close)
- Alternative close UI (footer buttons only)
- Modal dialogs that should not be easily dismissed

#### Current Implementation
```typescript
function DialogContent({
  className,
  children,
  showCloseButton = true,  // CUSTOM PROP
  ...props
}: React.ComponentProps<typeof DialogPrimitive.Content> & {
  showCloseButton?: boolean;  // CUSTOM TYPE EXTENSION
}) {
  return (
    // ...
    {showCloseButton && (
      <DialogPrimitive.Close
        className="..."
      >
        <XIcon />
        <span className="sr-only">Close</span>
      </DialogPrimitive.Close>
    )}
  );
}
```

#### How to Preserve in Base UI
Base UI's Dialog (Modal) component has different primitives but supports similar patterns.

**Preservation Strategy:**
- Add `showCloseButton` prop to the Base UI DialogContent wrapper
- Conditionally render a close button that calls the modal's close function
- Ensure the close button styling is preserved exactly:
  ```css
  "ring-offset-background focus:ring-ring data-[state=open]:bg-accent
   data-[state=open]:text-muted-foreground absolute top-4 right-4 rounded-xs
   opacity-70 transition-opacity hover:opacity-100 focus:ring-2 focus:ring-offset-2
   focus:outline-hidden disabled:pointer-events-none [&_svg]:pointer-events-none
   [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4"
  ```

---

### 2.2 Data Slot Attributes

#### What It Does
All dialog sub-components include `data-slot` attributes for styling and testing.

#### Current Implementation
```typescript
<DialogPrimitive.Root data-slot="dialog" {...props} />
<DialogPrimitive.Trigger data-slot="dialog-trigger" {...props} />
// etc.
```

#### How to Preserve in Base UI
**Preservation Strategy:**
- Add `data-slot` attributes to all Base UI dialog wrapper components
- These are used for CSS selectors and potentially testing

---

## 3. Select Component

**File:** `/webapp/src/components/ui/select.tsx`

### 3.1 `size` Prop on SelectTrigger

#### What It Does
Provides two size variants for the select trigger: `"sm"` (32px height) and `"default"` (36px height).

#### Why It Was Added
Allows consistent sizing with other form controls and supports dense form layouts.

#### Current Implementation
```typescript
function SelectTrigger({
  className,
  size = "default",  // CUSTOM PROP
  children,
  ...props
}: React.ComponentProps<typeof SelectPrimitive.Trigger> & {
  size?: "sm" | "default";  // CUSTOM TYPE
}) {
  return (
    <SelectPrimitive.Trigger
      data-size={size}  // CUSTOM DATA ATTRIBUTE
      className={cn(
        // ...
        "data-[size=default]:h-9 data-[size=sm]:h-8",  // SIZE-BASED STYLES
        className,
      )}
      // ...
    />
  );
}
```

#### How to Preserve in Base UI
**Preservation Strategy:**
- Add `size` prop to the Base UI SelectTrigger wrapper
- Use `data-size` attribute for CSS-based sizing
- Preserve the height classes: `data-[size=default]:h-9 data-[size=sm]:h-8`

---

### 3.2 Icon Inside SelectPrimitive.Icon

#### What It Does
Wraps the chevron icon in `SelectPrimitive.Icon` with `asChild`.

#### Current Implementation
```typescript
<SelectPrimitive.Icon asChild>
  <ChevronDownIcon className="size-4 opacity-50" />
</SelectPrimitive.Icon>
```

#### How to Preserve in Base UI
Base UI Select handles icons differently.

**Preservation Strategy:**
- Use Base UI's slot system or render the icon as a direct child
- Ensure the icon styling is preserved: `size-4 opacity-50`

---

### 3.3 Radix CSS Variable Usage

#### What It Does
Uses Radix-specific CSS variables for dynamic sizing based on content/trigger.

#### Current Implementation
```css
max-h-(--radix-select-content-available-height)
origin-(--radix-select-content-transform-origin)
h-[var(--radix-select-trigger-height)]
min-w-[var(--radix-select-trigger-width)]
```

#### How to Preserve in Base UI
**CRITICAL:** These CSS variables are Radix-specific.

**Preservation Strategy:**
- Base UI may provide similar CSS variables (check documentation)
- If not available, implement JavaScript-based sizing
- Alternative: Use fixed heights or viewport-relative heights

---

## 4. Tooltip Component

**File:** `/webapp/src/components/ui/tooltip.tsx`

### 4.1 `delayDuration=0` Default

#### What It Does
Tooltips appear instantly without the default 700ms delay.

#### Why It Was Added
Improves perceived responsiveness, especially for icon-only buttons where users need immediate feedback about what the button does.

#### Current Implementation
```typescript
function TooltipProvider({
  delayDuration = 0,  // CUSTOM DEFAULT (Radix default is 700)
  ...props
}: React.ComponentProps<typeof TooltipPrimitive.Provider>) {
  return (
    <TooltipPrimitive.Provider
      delayDuration={delayDuration}
      {...props}
    />
  );
}
```

#### How to Preserve in Base UI
**Preservation Strategy:**
- Set `openDelay={0}` (or equivalent prop) in Base UI Tooltip
- Ensure this is the default in the wrapper component

---

### 4.2 Auto-Wrapping Tooltip with TooltipProvider

#### What It Does
Each `<Tooltip>` automatically wraps itself in a `<TooltipProvider>`, eliminating the need for a global provider.

#### Why It Was Added
Simplifies usage - developers do not need to remember to wrap their app in a provider.

#### Current Implementation
```typescript
function Tooltip({ ...props }: React.ComponentProps<typeof TooltipPrimitive.Root>) {
  return (
    <TooltipProvider>  {/* AUTO-WRAPPED */}
      <TooltipPrimitive.Root data-slot="tooltip" {...props} />
    </TooltipProvider>
  );
}
```

#### How to Preserve in Base UI
**Preservation Strategy:**
- Check if Base UI Tooltip requires a provider
- If yes, wrap automatically in the Tooltip wrapper component
- Ensure delay settings are passed through

---

### 4.3 Custom Arrow Styling

#### What It Does
Renders a styled arrow that visually connects the tooltip to its trigger.

#### Current Implementation
```typescript
<TooltipPrimitive.Arrow
  className="bg-foreground fill-foreground z-50 size-2.5
             translate-y-[calc(-50%_-_2px)] rotate-45 rounded-[2px]"
/>
```

#### Why It Was Added
Creates a polished, themed arrow that matches the tooltip background.

#### How to Preserve in Base UI
**Preservation Strategy:**
- Check if Base UI Tooltip has an Arrow component
- If not, create a custom arrow using CSS (positioned pseudo-element)
- Preserve exact styling:
  - Size: 10px (size-2.5)
  - Background: foreground color
  - Rotation: 45deg
  - Border radius: 2px
  - Z-index: 50

---

### 4.4 `sideOffset=0` Default

#### What It Does
Tooltip appears flush with the trigger (no gap).

#### Current Implementation
```typescript
function TooltipContent({
  sideOffset = 0,  // CUSTOM DEFAULT (Radix default is 5)
  // ...
}) {
```

#### How to Preserve in Base UI
**Preservation Strategy:**
- Set offset prop to 0 by default in Base UI wrapper

---

## 5. Sidebar Component

**File:** `/webapp/src/components/ui/sidebar.tsx`

This is the most complex component with extensive customizations.

### 5.1 All 16 `asChild` Usages

#### Summary Table

| Component | Line | Purpose |
|-----------|------|---------|
| `SidebarGroupLabel` | 377-380 | Render label as custom element (e.g., link) |
| `SidebarGroupAction` | 396-401 | Render action as custom element |
| `SidebarMenuButton` | 474-487 | Render button as link/custom element |
| `SidebarMenuAction` | 524-533 | Render action as custom element |
| `SidebarMenuSubButton` | 637-648 | Render sub-button as link |
| (internal) `TooltipTrigger` | 513 | Wrap button without extra element |
| (internal) `SelectPrimitive.Icon` | 38-40 (select.tsx) | Used within sidebar selects |

#### Current Implementation Pattern
```typescript
function SidebarMenuButton({
  asChild = false,
  // ...
}: React.ComponentProps<"button"> & {
  asChild?: boolean;
  // ...
}) {
  const Comp = asChild ? Slot : "button";
  return <Comp ... />;
}
```

#### How to Preserve in Base UI
**CRITICAL:** This is the same Radix Slot pattern used throughout.

**Preservation Strategy:**
- Keep importing `@radix-ui/react-slot` as a standalone utility
- All 16 usages follow the same pattern and can be preserved identically

---

### 5.2 State Management

#### What It Does
Comprehensive state management for sidebar open/closed states on desktop and mobile.

#### Current Implementation
```typescript
type SidebarContextProps = {
  state: "expanded" | "collapsed";
  open: boolean;
  setOpen: (open: boolean) => void;
  openMobile: boolean;
  setOpenMobile: (open: boolean) => void;
  isMobile: boolean;
  toggleSidebar: () => void;
};

const SidebarContext = React.createContext<SidebarContextProps | null>(null);
```

#### Key Features
1. **Controlled/Uncontrolled Pattern:** Supports both via `open`/`onOpenChange` props
2. **Cookie Persistence:** State is persisted to cookie (`sidebar_state`)
3. **Separate Mobile State:** `openMobile` is independent of desktop `open`
4. **Derived State:** `state` is computed from `open` for CSS data attributes

#### How to Preserve in Base UI
This is pure React state management with no Radix dependency.

**Preservation Strategy:**
- Keep the context and state management code unchanged
- This is framework-agnostic React code

---

### 5.3 Keyboard Shortcut (Cmd/Ctrl+B)

#### What It Does
Toggles the sidebar with keyboard shortcut.

#### Current Implementation
```typescript
const SIDEBAR_KEYBOARD_SHORTCUT = "b";

React.useEffect(() => {
  const handleKeyDown = (event: KeyboardEvent) => {
    if (event.key === SIDEBAR_KEYBOARD_SHORTCUT && (event.metaKey || event.ctrlKey)) {
      event.preventDefault();
      toggleSidebar();
    }
  };

  window.addEventListener("keydown", handleKeyDown);
  return () => window.removeEventListener("keydown", handleKeyDown);
}, [toggleSidebar]);
```

#### How to Preserve in Base UI
This is pure DOM event handling with no Radix dependency.

**Preservation Strategy:**
- Keep the keyboard handler unchanged
- This is framework-agnostic JavaScript

---

### 5.4 CSS Custom Properties for Width

#### What It Does
Uses CSS custom properties for consistent sidebar widths.

#### Current Implementation
```typescript
const SIDEBAR_WIDTH = "16rem";
const SIDEBAR_WIDTH_MOBILE = "18rem";
const SIDEBAR_WIDTH_ICON = "3rem";

// Applied as inline styles:
style={{
  "--sidebar-width": SIDEBAR_WIDTH,
  "--sidebar-width-icon": SIDEBAR_WIDTH_ICON,
}}
```

#### How to Preserve in Base UI
This is pure CSS custom properties.

**Preservation Strategy:**
- Keep all constants and inline style application unchanged

---

### 5.5 Tooltip Integration in SidebarMenuButton

#### What It Does
Optionally wraps menu buttons in tooltips that only appear when sidebar is collapsed.

#### Current Implementation
```typescript
function SidebarMenuButton({
  tooltip,
  // ...
}: {
  tooltip?: string | React.ComponentProps<typeof TooltipContent>;
  // ...
}) {
  if (!tooltip) return button;

  return (
    <Tooltip>
      <TooltipTrigger asChild>{button}</TooltipTrigger>
      <TooltipContent
        side="right"
        align="center"
        hidden={state !== "collapsed" || isMobile}
        {...tooltip}
      />
    </Tooltip>
  );
}
```

#### How to Preserve in Base UI
**Preservation Strategy:**
- When migrating Tooltip to Base UI, ensure this integration still works
- The `hidden` prop conditionally hides tooltip based on sidebar state

---

### 5.6 Sheet Component for Mobile

#### What It Does
Uses Sheet (drawer) component for mobile sidebar instead of the fixed sidebar.

#### Current Implementation
```typescript
if (isMobile) {
  return (
    <Sheet open={openMobile} onOpenChange={setOpenMobile}>
      <SheetContent
        data-sidebar="sidebar"
        data-mobile="true"
        side={side}
        // ...
      >
        {children}
      </SheetContent>
    </Sheet>
  );
}
```

#### How to Preserve in Base UI
**Preservation Strategy:**
- When migrating Sheet to Base UI, ensure the sidebar mobile integration continues to work
- The Sheet component may need separate migration consideration

---

## 6. Badge Component

**File:** `/webapp/src/components/ui/badge.tsx`

### 6.1 `asChild` Prop

#### What It Does
Allows Badge to render as any element (e.g., link, button) while maintaining badge styling.

#### Current Implementation
```typescript
function Badge({
  className,
  variant,
  asChild = false,
  ...props
}: React.ComponentProps<"span"> & VariantProps<typeof badgeVariants> & {
  asChild?: boolean
}) {
  const Comp = asChild ? Slot : "span";
  return <Comp data-slot="badge" className={...} {...props} />;
}
```

#### How to Preserve in Base UI
**Preservation Strategy:**
- Keep using `@radix-ui/react-slot` for the asChild pattern
- Badge is a purely styled component; Base UI migration has minimal impact

---

### 6.2 Variant-Specific Hover Styles with Anchor Selector

#### What It Does
Applies hover styles only when the badge is rendered as an anchor element.

#### Current Implementation
```typescript
variant: {
  default: "... [a&]:hover:bg-primary/90",
  secondary: "... [a&]:hover:bg-secondary/90",
  destructive: "... [a&]:hover:bg-destructive/90",
  outline: "... [a&]:hover:bg-accent [a&]:hover:text-accent-foreground",
}
```

#### How to Preserve in Base UI
This is pure Tailwind CSS using the `[a&]` selector.

**Preservation Strategy:**
- Keep these classes unchanged; they work with any component system

---

## 7. Breadcrumb Component

**File:** `/webapp/src/components/ui/breadcrumb.tsx`

### 7.1 `asChild` Prop on BreadcrumbLink

#### What It Does
Allows BreadcrumbLink to render as a router Link component.

#### Current Implementation
```typescript
function BreadcrumbLink({
  asChild,
  className,
  ...props
}: React.ComponentProps<"a"> & {
  asChild?: boolean;
}) {
  const Comp = asChild ? Slot : "a";
  return <Comp data-slot="breadcrumb-link" className={...} {...props} />;
}
```

#### How to Preserve in Base UI
**Preservation Strategy:**
- Keep using `@radix-ui/react-slot` for the asChild pattern
- Breadcrumb is purely styled; no Radix primitives are used

---

### 7.2 No Radix Primitives Used

#### What It Does
The breadcrumb component uses only native HTML elements and Radix Slot.

#### How to Preserve in Base UI
**Preservation Strategy:**
- This component has minimal migration impact
- Only the Slot import needs consideration (keep or replace)

---

## 8. ButtonGroup Component

**File:** `/webapp/src/components/ui/button-group.tsx`

### 8.1 Custom Component Structure

#### What It Does
Provides a container for grouping buttons with automatic border radius handling.

#### Current Implementation
```typescript
const buttonGroupVariants = cva(
  "flex w-fit items-stretch [&>*]:focus-visible:z-10 [&>*]:focus-visible:relative
   [&>[data-slot=select-trigger]:not([class*='w-'])]:w-fit [&>input]:flex-1
   has-[select[aria-hidden=true]:last-child]:[&>[data-slot=select-trigger]:last-of-type]:rounded-r-md
   has-[>[data-slot=button-group]]:gap-2",
  {
    variants: {
      orientation: {
        horizontal: "[&>*:not(:first-child)]:rounded-l-none [&>*:not(:first-child)]:border-l-0
                     [&>*:not(:last-child)]:rounded-r-none",
        vertical: "flex-col [&>*:not(:first-child)]:rounded-t-none [&>*:not(:first-child)]:border-t-0
                   [&>*:not(:last-child)]:rounded-b-none",
      },
    },
  },
);
```

#### Key Features
1. **Orientation Variants:** Horizontal and vertical layouts
2. **Border Radius Management:** First/last child get rounded corners, middle items are flat
3. **Border Collapse:** Removes duplicate borders between items
4. **Focus Z-Index:** Ensures focused items appear above siblings
5. **Select Integration:** Special handling for select triggers within group
6. **Nested Groups:** Adds gap when button groups are nested

#### How to Preserve in Base UI
This is entirely CSS-based with no Radix primitives.

**Preservation Strategy:**
- Keep the CVA configuration unchanged
- Only the Slot import in `ButtonGroupText` needs consideration

---

### 8.2 `ButtonGroupText` with asChild

#### Current Implementation
```typescript
function ButtonGroupText({
  asChild = false,
  ...props
}: React.ComponentProps<"div"> & { asChild?: boolean }) {
  const Comp = asChild ? Slot : "div";
  return <Comp className={...} {...props} />;
}
```

#### How to Preserve in Base UI
**Preservation Strategy:**
- Keep using `@radix-ui/react-slot` for the asChild pattern

---

### 8.3 ButtonGroupSeparator

#### What It Does
Provides a visual separator between button group items.

#### Current Implementation
```typescript
function ButtonGroupSeparator({
  orientation = "vertical",
  ...props
}: React.ComponentProps<typeof Separator>) {
  return (
    <Separator
      data-slot="button-group-separator"
      orientation={orientation}
      className={cn(
        "bg-input relative !m-0 self-stretch data-[orientation=vertical]:h-auto",
        className,
      )}
      {...props}
    />
  );
}
```

#### How to Preserve in Base UI
**Preservation Strategy:**
- Depends on Separator component migration
- The styling is CSS-based and can be preserved

---

## 9. Item Component

**File:** `/webapp/src/components/ui/item.tsx`

### 9.1 Custom Component Structure with Media, Content, Actions

#### What It Does
Provides a flexible list item component with slots for media, content, and actions.

#### Component Hierarchy
```
ItemGroup (container)
  Item (list item)
    ItemMedia (avatar/icon/image slot)
    ItemContent (text container)
      ItemTitle
      ItemDescription
    ItemActions (action buttons slot)
    ItemHeader (full-width header slot)
    ItemFooter (full-width footer slot)
  ItemSeparator
```

---

### 9.2 Item Component with Variants

#### Current Implementation
```typescript
const itemVariants = cva(
  "group/item flex items-center border border-transparent text-sm rounded-md
   transition-colors [a]:hover:bg-accent/50 [a]:transition-colors duration-100
   flex-wrap outline-none focus-visible:border-ring focus-visible:ring-ring/50
   focus-visible:ring-[3px]",
  {
    variants: {
      variant: {
        default: "bg-transparent",
        outline: "border-border",
        muted: "bg-muted/50",
      },
      size: {
        default: "p-4 gap-4",
        sm: "py-3 px-4 gap-2.5",
      },
    },
  },
);
```

#### Key Features
1. **Variant System:** default, outline, muted backgrounds
2. **Size System:** default and small padding/gap
3. **asChild Support:** Can render as link or other element
4. **Focus Styling:** 3-ring focus indicator
5. **Group Context:** Uses Tailwind group for hover states

---

### 9.3 ItemMedia Variants

#### Current Implementation
```typescript
const itemMediaVariants = cva(
  "flex shrink-0 items-center justify-center gap-2
   group-has-[[data-slot=item-description]]/item:self-start
   [&_svg]:pointer-events-none
   group-has-[[data-slot=item-description]]/item:translate-y-0.5",
  {
    variants: {
      variant: {
        default: "bg-transparent",
        icon: "size-8 border rounded-sm bg-muted [&_svg:not([class*='size-'])]:size-4",
        image: "size-10 rounded-sm overflow-hidden [&_img]:size-full [&_img]:object-cover",
      },
    },
  },
);
```

#### Key Features
1. **Conditional Alignment:** Aligns to start when description is present (using `group-has-[]`)
2. **Icon Variant:** 32px container with muted background
3. **Image Variant:** 40px container with overflow hidden for images

---

### 9.4 asChild on Item

#### Current Implementation
```typescript
function Item({
  asChild = false,
  ...props
}: React.ComponentProps<"div"> & VariantProps<typeof itemVariants> & {
  asChild?: boolean
}) {
  const Comp = asChild ? Slot : "div";
  return <Comp data-slot="item" ... />;
}
```

#### How to Preserve in Base UI
**Preservation Strategy:**
- Keep using `@radix-ui/react-slot` for the asChild pattern
- Item is entirely CSS-based with no Radix primitives

---

## Summary: Migration Risk Assessment

### Low Risk (CSS/React only, no Radix primitives)
- ButtonGroup
- Item
- Breadcrumb (except Slot)
- Badge (except Slot)
- Sidebar state management and keyboard shortcuts

### Medium Risk (Uses Radix Slot)
- Button (asChild)
- Badge (asChild)
- Breadcrumb (asChild)
- ButtonGroup (asChild in ButtonGroupText)
- Item (asChild)
- Sidebar (16 asChild usages)

**Recommendation:** Keep `@radix-ui/react-slot` as a standalone dependency. It is:
- Lightweight (~2KB)
- Has no dependencies on other Radix packages
- Battle-tested and widely used
- The alternative (Base UI render prop pattern) would require significant API changes

### High Risk (Uses Radix Primitives)
- Dialog (DialogPrimitive.*)
- Select (SelectPrimitive.* + CSS variables)
- Tooltip (TooltipPrimitive.* + Provider pattern)
- Sidebar (uses Sheet which uses Radix Dialog)

**These require careful 1:1 mapping to Base UI equivalents.**

---

## Migration Checklist

- [ ] Decide on Slot strategy (keep @radix-ui/react-slot vs. alternative)
- [ ] Map Radix CSS variables to Base UI equivalents (Select, Tooltip)
- [ ] Preserve all data-slot attributes for styling/testing
- [ ] Preserve all custom props (showCloseButton, size, delayDuration)
- [ ] Preserve all CVA variant configurations
- [ ] Preserve all keyboard shortcuts and state management
- [ ] Test tooltip delay behavior (must be instant by default)
- [ ] Test sidebar mobile Sheet integration
- [ ] Test all asChild usages with router Links

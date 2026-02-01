# Base UI Migration Checklist

## Overview

This document details the migration from Radix UI to Base UI for shadcn/ui components in the Hephaestus3 webapp. Base UI reached v1.0 on December 11, 2025, providing a stable API with modern patterns.

**Package Change**: `@radix-ui/react-*` packages --> `@base-ui-components/react` (single package)

> **Note**: The package name is `@base-ui-components/react` (or newer: `@base-ui/react`). Always verify the current package name before installation.

---

## Table of Contents

1. [Configuration Changes](#1-configuration-changes)
2. [Core Pattern Changes](#2-core-pattern-changes)
3. [Component-by-Component Migration](#3-component-by-component-migration)
4. [Data Attribute Changes](#4-data-attribute-changes)
5. [CSS Variable Changes](#5-css-variable-changes)
6. [Gotchas and Edge Cases](#6-gotchas-and-edge-cases)
7. [Testing Checklist](#7-testing-checklist)

---

## 1. Configuration Changes

### 1.1 Update components.json

The current `components.json` uses `"style": "new-york"`. To use Base UI components:

```json
{
  "$schema": "https://ui.shadcn.com/schema.json",
  "style": "base-vega",  // Changed from "new-york"
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

**Available Base UI styles:**
- `base-vega` - Classic shadcn/ui look
- `base-nova` - Reduced padding/margins for compact layouts
- `base-maia` - Soft and rounded with generous spacing
- `base-lyra` - Boxy and sharp, pairs well with mono fonts
- `base-mira` - Compact, made for dense interfaces

### 1.2 Update package.json

**Remove Radix packages:**
```bash
pnpm remove @radix-ui/react-accordion @radix-ui/react-alert-dialog @radix-ui/react-aspect-ratio @radix-ui/react-avatar @radix-ui/react-checkbox @radix-ui/react-collapsible @radix-ui/react-context-menu @radix-ui/react-dialog @radix-ui/react-dropdown-menu @radix-ui/react-hover-card @radix-ui/react-label @radix-ui/react-menubar @radix-ui/react-navigation-menu @radix-ui/react-popover @radix-ui/react-progress @radix-ui/react-radio-group @radix-ui/react-scroll-area @radix-ui/react-select @radix-ui/react-separator @radix-ui/react-slider @radix-ui/react-slot @radix-ui/react-switch @radix-ui/react-tabs @radix-ui/react-toggle @radix-ui/react-toggle-group @radix-ui/react-tooltip
```

**Add Base UI:**
```bash
pnpm add @base-ui-components/react
```

---

## 2. Core Pattern Changes

### 2.1 Import Pattern Change

**Radix UI (before):**
```tsx
import * as DialogPrimitive from "@radix-ui/react-dialog";
import * as PopoverPrimitive from "@radix-ui/react-popover";
import * as TooltipPrimitive from "@radix-ui/react-tooltip";
```

**Base UI (after):**
```tsx
import { Dialog } from "@base-ui-components/react/dialog";
import { Popover } from "@base-ui-components/react/popover";
import { Tooltip } from "@base-ui-components/react/tooltip";
```

Or use barrel imports:
```tsx
import { Dialog, Popover, Tooltip } from "@base-ui-components/react";
```

### 2.2 asChild --> render Prop Pattern

This is the most significant API change. Radix uses `asChild` with `Slot`, while Base UI uses a `render` prop.

**Radix UI (before):**
```tsx
<DialogPrimitive.Trigger asChild>
  <Button>Open Dialog</Button>
</DialogPrimitive.Trigger>

<SelectPrimitive.Icon asChild>
  <ChevronDownIcon className="size-4" />
</SelectPrimitive.Icon>
```

**Base UI (after):**
```tsx
<Dialog.Trigger render={<Button />}>
  Open Dialog
</Dialog.Trigger>

<Select.Icon render={<ChevronDownIcon className="size-4" />} />
```

**Function variant for state access:**
```tsx
<Switch.Thumb
  render={(props, state) => (
    <span {...props}>
      {state.checked ? <CheckedIcon /> : <UncheckedIcon />}
    </span>
  )}
/>
```

### 2.3 Positioner Component Pattern

Base UI requires a `Positioner` wrapper component for floating elements. Positioning props move from Content to Positioner.

**Radix UI (before):**
```tsx
<PopoverPrimitive.Portal>
  <PopoverPrimitive.Content
    align="center"
    sideOffset={4}
    className="..."
  >
    {children}
  </PopoverPrimitive.Content>
</PopoverPrimitive.Portal>
```

**Base UI (after):**
```tsx
<Popover.Portal>
  <Popover.Positioner align="center" sideOffset={4}>
    <Popover.Popup className="...">
      {children}
    </Popover.Popup>
  </Popover.Positioner>
</Popover.Portal>
```

**Components requiring Positioner:**
- Dialog (uses Viewport instead of Positioner)
- Popover
- Tooltip
- Menu (DropdownMenu equivalent)
- Select
- HoverCard

### 2.4 Viewport Pattern for Dialog

Dialog uses `Viewport` instead of `Positioner` for centering.

**Radix UI (before):**
```tsx
<DialogPrimitive.Portal>
  <DialogPrimitive.Overlay className="..." />
  <DialogPrimitive.Content className="...">
    {children}
  </DialogPrimitive.Content>
</DialogPrimitive.Portal>
```

**Base UI (after):**
```tsx
<Dialog.Portal>
  <Dialog.Backdrop className="..." />
  <Dialog.Viewport>
    <Dialog.Popup className="...">
      {children}
    </Dialog.Popup>
  </Dialog.Viewport>
</Dialog.Portal>
```

---

## 3. Component-by-Component Migration

### 3.1 Dialog

| Radix Component | Base UI Component | Notes |
|-----------------|-------------------|-------|
| `Dialog.Root` | `Dialog.Root` | Same |
| `Dialog.Trigger` | `Dialog.Trigger` | `asChild` --> `render` |
| `Dialog.Portal` | `Dialog.Portal` | Same |
| `Dialog.Overlay` | `Dialog.Backdrop` | Name change |
| `Dialog.Content` | `Dialog.Popup` | Wrap in `Dialog.Viewport` |
| `Dialog.Title` | `Dialog.Title` | Same |
| `Dialog.Description` | `Dialog.Description` | Same |
| `Dialog.Close` | `Dialog.Close` | `asChild` --> `render` |

**Migration Example:**
```tsx
// BEFORE (Radix)
function DialogContent({ className, children, ...props }) {
  return (
    <DialogPrimitive.Portal>
      <DialogPrimitive.Overlay className="fixed inset-0 bg-black/50" />
      <DialogPrimitive.Content className={cn("fixed ...", className)} {...props}>
        {children}
        <DialogPrimitive.Close className="absolute top-4 right-4">
          <XIcon />
        </DialogPrimitive.Close>
      </DialogPrimitive.Content>
    </DialogPrimitive.Portal>
  );
}

// AFTER (Base UI)
function DialogContent({ className, children, ...props }) {
  return (
    <Dialog.Portal>
      <Dialog.Backdrop className="fixed inset-0 bg-black/50" />
      <Dialog.Viewport>
        <Dialog.Popup className={cn("...", className)} {...props}>
          {children}
          <Dialog.Close className="absolute top-4 right-4">
            <XIcon />
          </Dialog.Close>
        </Dialog.Popup>
      </Dialog.Viewport>
    </Dialog.Portal>
  );
}
```

### 3.2 Popover

| Radix Component | Base UI Component | Notes |
|-----------------|-------------------|-------|
| `Popover.Root` | `Popover.Root` | Same |
| `Popover.Trigger` | `Popover.Trigger` | `asChild` --> `render` |
| `Popover.Portal` | `Popover.Portal` | Same |
| `Popover.Content` | `Popover.Positioner` + `Popover.Popup` | Split component |
| `Popover.Anchor` | `Popover.Anchor` | Same |
| `Popover.Arrow` | `Popover.Arrow` | Same |

**Migration Example:**
```tsx
// BEFORE (Radix)
function PopoverContent({ align = "center", sideOffset = 4, ...props }) {
  return (
    <PopoverPrimitive.Portal>
      <PopoverPrimitive.Content
        align={align}
        sideOffset={sideOffset}
        className="bg-popover ..."
        {...props}
      />
    </PopoverPrimitive.Portal>
  );
}

// AFTER (Base UI)
function PopoverContent({ align = "center", sideOffset = 4, ...props }) {
  return (
    <Popover.Portal>
      <Popover.Positioner align={align} sideOffset={sideOffset}>
        <Popover.Popup className="bg-popover ..." {...props} />
      </Popover.Positioner>
    </Popover.Portal>
  );
}
```

### 3.3 Tooltip

| Radix Component | Base UI Component | Notes |
|-----------------|-------------------|-------|
| `Tooltip.Provider` | `Tooltip.Provider` | Same |
| `Tooltip.Root` | `Tooltip.Root` | Same |
| `Tooltip.Trigger` | `Tooltip.Trigger` | `asChild` --> `render` |
| `Tooltip.Portal` | `Tooltip.Portal` | Same |
| `Tooltip.Content` | `Tooltip.Positioner` + `Tooltip.Popup` | Split component |
| `Tooltip.Arrow` | `Tooltip.Arrow` | Same |

**Migration Example:**
```tsx
// BEFORE (Radix)
function TooltipContent({ sideOffset = 4, children, ...props }) {
  return (
    <TooltipPrimitive.Portal>
      <TooltipPrimitive.Content sideOffset={sideOffset} className="..." {...props}>
        {children}
        <TooltipPrimitive.Arrow className="..." />
      </TooltipPrimitive.Content>
    </TooltipPrimitive.Portal>
  );
}

// AFTER (Base UI)
function TooltipContent({ sideOffset = 4, children, ...props }) {
  return (
    <Tooltip.Portal>
      <Tooltip.Positioner sideOffset={sideOffset}>
        <Tooltip.Popup className="..." {...props}>
          {children}
          <Tooltip.Arrow className="..." />
        </Tooltip.Popup>
      </Tooltip.Positioner>
    </Tooltip.Portal>
  );
}
```

### 3.4 Dropdown Menu --> Menu

Base UI uses `Menu` instead of `DropdownMenu`.

| Radix Component | Base UI Component | Notes |
|-----------------|-------------------|-------|
| `DropdownMenu.Root` | `Menu.Root` | Name change |
| `DropdownMenu.Trigger` | `Menu.Trigger` | `asChild` --> `render` |
| `DropdownMenu.Portal` | `Menu.Portal` | Name change |
| `DropdownMenu.Content` | `Menu.Positioner` + `Menu.Popup` | Split + name change |
| `DropdownMenu.Item` | `Menu.Item` | Name change |
| `DropdownMenu.CheckboxItem` | `Menu.CheckboxItem` | Name change |
| `DropdownMenu.RadioGroup` | `Menu.RadioGroup` | Name change |
| `DropdownMenu.RadioItem` | `Menu.RadioItem` | Name change |
| `DropdownMenu.Label` | `Menu.GroupLabel` | Must be inside `Menu.Group` |
| `DropdownMenu.Separator` | `Menu.Separator` | Name change |
| `DropdownMenu.Sub` | `Menu.SubmenuRoot` | Name change |
| `DropdownMenu.SubTrigger` | `Menu.SubmenuTrigger` | Name change |
| `DropdownMenu.SubContent` | `Menu.Positioner` + `Menu.Popup` | Split |
| `DropdownMenu.Group` | `Menu.Group` | Name change |

**Important**: Labels must now be wrapped in `Menu.Group`:
```tsx
// BEFORE (Radix)
<DropdownMenuPrimitive.Label>Settings</DropdownMenuPrimitive.Label>

// AFTER (Base UI)
<Menu.Group>
  <Menu.GroupLabel>Settings</Menu.GroupLabel>
  {/* items */}
</Menu.Group>
```

### 3.5 Select

| Radix Component | Base UI Component | Notes |
|-----------------|-------------------|-------|
| `Select.Root` | `Select.Root` | Same |
| `Select.Trigger` | `Select.Trigger` | `asChild` --> `render` |
| `Select.Value` | `Select.Value` | Same |
| `Select.Icon` | `Select.Icon` | `asChild` --> `render` |
| `Select.Portal` | `Select.Portal` | Same |
| `Select.Content` | `Select.Positioner` + `Select.Popup` | Split |
| `Select.Viewport` | `Select.List` | Name change |
| `Select.Item` | `Select.Item` | Same |
| `Select.ItemText` | `Select.ItemText` | Same |
| `Select.ItemIndicator` | `Select.ItemIndicator` | Same |
| `Select.Group` | `Select.Group` | Same |
| `Select.Label` | `Select.GroupLabel` | Name change, must be in Group |
| `Select.Separator` | `Select.Separator` | Same |
| `Select.ScrollUpButton` | `Select.ScrollUpArrow` | Name change |
| `Select.ScrollDownButton` | `Select.ScrollDownArrow` | Name change |

### 3.6 Accordion

| Radix Component | Base UI Component | Notes |
|-----------------|-------------------|-------|
| `Accordion.Root` | `Accordion.Root` | Same |
| `Accordion.Item` | `Accordion.Item` | Same |
| `Accordion.Header` | `Accordion.Header` | Same |
| `Accordion.Trigger` | `Accordion.Trigger` | Same |
| `Accordion.Content` | `Accordion.Panel` | Name change |

**Prop changes:**
- `type="single"` / `type="multiple"` --> `multiple={false}` / `multiple={true}`
- `collapsible` prop removed (always collapsible in Base UI)

### 3.7 Checkbox

| Radix Component | Base UI Component | Notes |
|-----------------|-------------------|-------|
| `Checkbox.Root` | `Checkbox.Root` | Renders `<span>` not `<button>` |
| `Checkbox.Indicator` | `Checkbox.Indicator` | Same |

**Breaking changes:**
- Base UI Checkbox renders a `<span>` with hidden `<input>`, not a `<button>`
- Unchecked checkboxes no longer submit "off" value (matches native behavior)
- Use `uncheckedValue` prop if you need to submit a value when unchecked

### 3.8 Switch

| Radix Component | Base UI Component | Notes |
|-----------------|-------------------|-------|
| `Switch.Root` | `Switch.Root` | Renders `<span>` not `<button>` |
| `Switch.Thumb` | `Switch.Thumb` | Same |

**Breaking changes:**
- Base UI Switch renders a `<span>` with hidden `<input>`, not a `<button>`
- Unchecked switches no longer submit "off" value (matches native behavior)
- Use `uncheckedValue` prop if you need to submit a value when unchecked

### 3.9 Tabs

| Radix Component | Base UI Component | Notes |
|-----------------|-------------------|-------|
| `Tabs.Root` | `Tabs.Root` | Same |
| `Tabs.List` | `Tabs.List` | Same |
| `Tabs.Trigger` | `Tabs.Tab` | Name change |
| `Tabs.Content` | `Tabs.Panel` | Name change |
| N/A | `Tabs.Indicator` | New component for active indicator |

**Breaking changes:**
- `value` prop is now **required** on `Tabs.Tab` and `Tabs.Panel`
- `defaultValue` defaults to `0` instead of first tab value

### 3.10 Collapsible

| Radix Component | Base UI Component | Notes |
|-----------------|-------------------|-------|
| `Collapsible.Root` | `Collapsible.Root` | Same |
| `Collapsible.Trigger` | `Collapsible.Trigger` | Same |
| `Collapsible.Content` | `Collapsible.Panel` | Name change |

### 3.11 Slider

| Radix Component | Base UI Component | Notes |
|-----------------|-------------------|-------|
| `Slider.Root` | `Slider.Root` | Same |
| `Slider.Track` | `Slider.Track` | Same |
| `Slider.Range` | `Slider.Indicator` | Name change |
| `Slider.Thumb` | `Slider.Thumb` | Same |

### 3.12 Progress

| Radix Component | Base UI Component | Notes |
|-----------------|-------------------|-------|
| `Progress.Root` | `Progress.Root` | Same |
| `Progress.Indicator` | `Progress.Indicator` | Same |

### 3.13 Radio Group

| Radix Component | Base UI Component | Notes |
|-----------------|-------------------|-------|
| `RadioGroup.Root` | `RadioGroup.Root` | Same |
| `RadioGroup.Item` | `RadioGroup.Item` | Same |
| `RadioGroup.Indicator` | `RadioGroup.Indicator` | Same |

### 3.14 Alert Dialog

Base UI does not have a separate AlertDialog component. Use `Dialog` with `modal={true}` (default).

```tsx
// BEFORE (Radix)
<AlertDialog.Root>
  <AlertDialog.Trigger />
  <AlertDialog.Portal>
    <AlertDialog.Overlay />
    <AlertDialog.Content>
      <AlertDialog.Title />
      <AlertDialog.Description />
      <AlertDialog.Cancel />
      <AlertDialog.Action />
    </AlertDialog.Content>
  </AlertDialog.Portal>
</AlertDialog.Root>

// AFTER (Base UI)
<Dialog.Root modal={true}>
  <Dialog.Trigger />
  <Dialog.Portal>
    <Dialog.Backdrop />
    <Dialog.Viewport>
      <Dialog.Popup>
        <Dialog.Title />
        <Dialog.Description />
        {/* Use regular buttons for Cancel/Action */}
        <Button onClick={handleCancel}>Cancel</Button>
        <Button onClick={handleAction}>Continue</Button>
      </Dialog.Popup>
    </Dialog.Viewport>
  </Dialog.Portal>
</Dialog.Root>
```

---

## 4. Data Attribute Changes

### 4.1 State Attributes

| Radix Attribute | Base UI Attribute | Component |
|-----------------|-------------------|-----------|
| `data-state="open"` | `data-open` | Dialog, Popover, etc. |
| `data-state="closed"` | `data-closed` | Dialog, Popover, etc. |
| `data-state="checked"` | `data-checked` | Checkbox, Switch |
| `data-state="unchecked"` | `data-unchecked` | Checkbox, Switch |
| `data-state="indeterminate"` | `data-indeterminate` | Checkbox |
| `data-state="active"` | `data-active` | Tabs |
| `data-disabled` | `data-disabled` | Same |

### 4.2 Animation Attributes

Base UI provides specific attributes for animations:
- `data-starting-style` - Present when animating in
- `data-ending-style` - Present when animating out

**Update animation classes:**
```tsx
// BEFORE (Radix)
className="data-[state=open]:animate-in data-[state=closed]:animate-out"

// AFTER (Base UI)
className="data-[starting-style]:animate-in data-[ending-style]:animate-out"
// OR use data-open/data-closed
className="data-[open]:animate-in data-[closed]:animate-out"
```

### 4.3 Position Attributes

| Radix Attribute | Base UI Attribute |
|-----------------|-------------------|
| `data-side="top"` | `data-side="top"` |
| `data-side="bottom"` | `data-side="bottom"` |
| `data-side="left"` | `data-side="left"` |
| `data-side="right"` | `data-side="right"` |
| `data-align="start"` | `data-align="start"` |
| `data-align="center"` | `data-align="center"` |
| `data-align="end"` | `data-align="end"` |

---

## 5. CSS Variable Changes

### 5.1 Transform Origin

| Radix Variable | Base UI Variable |
|----------------|------------------|
| `--radix-popover-content-transform-origin` | `--transform-origin` |
| `--radix-tooltip-content-transform-origin` | `--transform-origin` |
| `--radix-dropdown-menu-content-transform-origin` | `--transform-origin` |
| `--radix-select-content-transform-origin` | `--transform-origin` |

### 5.2 Available Space

| Radix Variable | Base UI Variable |
|----------------|------------------|
| `--radix-popover-content-available-height` | `--available-height` |
| `--radix-popover-content-available-width` | `--available-width` |
| `--radix-dropdown-menu-content-available-height` | `--available-height` |
| `--radix-select-content-available-height` | `--available-height` |

### 5.3 Anchor Dimensions

| Radix Variable | Base UI Variable |
|----------------|------------------|
| `--radix-select-trigger-width` | `--anchor-width` |
| `--radix-select-trigger-height` | `--anchor-height` |

### 5.4 Collapsible/Accordion Panel

| Radix Variable | Base UI Variable |
|----------------|------------------|
| `--radix-accordion-content-height` | `--accordion-panel-height` |
| `--radix-accordion-content-width` | `--accordion-panel-width` |
| `--radix-collapsible-content-height` | `--collapsible-panel-height` |
| `--radix-collapsible-content-width` | `--collapsible-panel-width` |

---

## 6. Gotchas and Edge Cases

### 6.1 React Version Compatibility

Base UI examples assume React 19. For React 18/17:
- Use `React.forwardRef()` when building custom components
- Pass forwarded refs to the ref array in `useRender`

### 6.2 Custom Components with render Prop

Custom components used with `render` must:
1. Forward the `ref` to the underlying DOM node
2. Spread all received props on the DOM element

```tsx
// Correct custom component
const MyButton = React.forwardRef<HTMLButtonElement, ButtonProps>(
  (props, ref) => <button ref={ref} {...props} />
);

// Usage
<Dialog.Trigger render={<MyButton />}>Open</Dialog.Trigger>
```

### 6.3 Form Submission Changes

Checkbox and Switch no longer submit "off" value when unchecked (matching native HTML behavior). If you need to submit a value:
```tsx
<Switch.Root uncheckedValue="off" />
<Checkbox.Root uncheckedValue="off" />
```

### 6.4 Labels in Menus

Labels must be inside `Menu.Group`:
```tsx
// This will NOT work
<Menu.GroupLabel>Section</Menu.GroupLabel>

// This WILL work
<Menu.Group>
  <Menu.GroupLabel>Section</Menu.GroupLabel>
  <Menu.Item>Item 1</Menu.Item>
</Menu.Group>
```

### 6.5 Tabs Value Requirement

`value` prop is now required on both `Tabs.Tab` and `Tabs.Panel`:
```tsx
// BEFORE (Radix) - value was optional
<Tabs.Trigger value="tab1">Tab 1</Tabs.Trigger>
<Tabs.Content value="tab1">Content 1</Tabs.Content>

// AFTER (Base UI) - value is REQUIRED
<Tabs.Tab value="tab1">Tab 1</Tabs.Tab>
<Tabs.Panel value="tab1">Content 1</Tabs.Panel>
```

### 6.6 Modal Behavior Differences

- Dialog: `modal={true}` by default (traps focus)
- Popover: `modal={false}` by default (no focus trap)
- Menu: `modal={true}` by default

### 6.7 useRender Hook for Custom Components

If building custom components that need the render prop pattern:
```tsx
import { useRender } from "@base-ui-components/react/use-render";

function CustomButton({ render = <button />, ...props }) {
  return useRender({ render, props });
}
```

### 6.8 Slot Component Removal

Base UI does not have a `Slot` component. Replace Radix's `Slot` usage with the `render` prop pattern or `useRender` hook.

```tsx
// BEFORE (Radix)
import { Slot } from "@radix-ui/react-slot";

function Button({ asChild, ...props }) {
  const Comp = asChild ? Slot : "button";
  return <Comp {...props} />;
}

// AFTER (Base UI)
import { useRender } from "@base-ui-components/react/use-render";

function Button({ render = <button />, ...props }) {
  return useRender({ render, props });
}
```

---

## 7. Testing Checklist

### 7.1 Functionality Tests

- [ ] Dialog opens and closes correctly
- [ ] Dialog focus trap works (modal mode)
- [ ] Popover positioning works correctly
- [ ] Tooltip shows on hover with correct delay
- [ ] Menu items are keyboard navigable
- [ ] Select options are selectable
- [ ] Checkbox toggles state correctly
- [ ] Switch toggles state correctly
- [ ] Tabs switch content correctly
- [ ] Accordion expands/collapses
- [ ] Form submission includes correct values

### 7.2 Accessibility Tests

- [ ] Screen reader announces dialog title/description
- [ ] Focus returns to trigger on dialog close
- [ ] Keyboard navigation works (Tab, Arrow keys, Escape)
- [ ] ARIA attributes are correct
- [ ] Labels are properly associated

### 7.3 Animation Tests

- [ ] Enter animations play correctly
- [ ] Exit animations play correctly
- [ ] No flash of unstyled content
- [ ] Transitions are smooth

### 7.4 Responsive Tests

- [ ] Popovers reposition on scroll
- [ ] Collision avoidance works at edges
- [ ] Touch interactions work on mobile

---

## Current Components Requiring Migration

Based on the webapp's `src/components/ui/` directory:

| Component | Radix Package | Priority |
|-----------|---------------|----------|
| `accordion.tsx` | `@radix-ui/react-accordion` | Medium |
| `alert-dialog.tsx` | `@radix-ui/react-alert-dialog` | High |
| `checkbox.tsx` | `@radix-ui/react-checkbox` | High |
| `collapsible.tsx` | `@radix-ui/react-collapsible` | Low |
| `context-menu.tsx` | `@radix-ui/react-context-menu` | Medium |
| `dialog.tsx` | `@radix-ui/react-dialog` | High |
| `dropdown-menu.tsx` | `@radix-ui/react-dropdown-menu` | High |
| `hover-card.tsx` | `@radix-ui/react-hover-card` | Low |
| `label.tsx` | `@radix-ui/react-label` | High |
| `menubar.tsx` | `@radix-ui/react-menubar` | Medium |
| `navigation-menu.tsx` | `@radix-ui/react-navigation-menu` | Medium |
| `popover.tsx` | `@radix-ui/react-popover` | High |
| `progress.tsx` | `@radix-ui/react-progress` | Low |
| `radio-group.tsx` | `@radix-ui/react-radio-group` | Medium |
| `scroll-area.tsx` | `@radix-ui/react-scroll-area` | Low |
| `select.tsx` | `@radix-ui/react-select` | High |
| `separator.tsx` | `@radix-ui/react-separator` | Low |
| `sheet.tsx` | Uses `@radix-ui/react-dialog` | High |
| `slider.tsx` | `@radix-ui/react-slider` | Medium |
| `switch.tsx` | `@radix-ui/react-switch` | High |
| `tabs.tsx` | `@radix-ui/react-tabs` | High |
| `toggle.tsx` | `@radix-ui/react-toggle` | Low |
| `toggle-group.tsx` | `@radix-ui/react-toggle-group` | Low |
| `tooltip.tsx` | `@radix-ui/react-tooltip` | High |

**Also uses:**
- `@radix-ui/react-aspect-ratio` (aspect-ratio.tsx)
- `@radix-ui/react-avatar` (avatar.tsx)
- `@radix-ui/react-slot` (button.tsx and others)

---

## Migration Order Recommendation

1. **Phase 1 - Foundation**
   - Update `components.json`
   - Install Base UI package
   - Create utility functions if needed

2. **Phase 2 - Core Components (High Priority)**
   - Dialog (used by many other components)
   - Popover
   - Tooltip
   - Dropdown Menu --> Menu
   - Select
   - Checkbox
   - Switch
   - Tabs

3. **Phase 3 - Secondary Components**
   - Accordion
   - Radio Group
   - Slider
   - Context Menu
   - Menubar
   - Navigation Menu

4. **Phase 4 - Utility Components**
   - Collapsible
   - Progress
   - Scroll Area
   - Separator
   - Toggle/Toggle Group
   - Hover Card

5. **Phase 5 - Cleanup**
   - Remove Radix packages
   - Update any remaining CSS variables
   - Final testing pass

---

## Resources

- [Base UI Documentation](https://base-ui.com/react/overview/quick-start)
- [Base UI Composition Guide](https://base-ui.com/react/handbook/composition)
- [useRender Hook](https://base-ui.com/react/utils/use-render)
- [shadcn/ui Base UI Changelog](https://ui.shadcn.com/docs/changelog/2026-01-base-ui)
- [basecn.dev Migration Guide](https://basecn.dev/docs/get-started/migrating-from-radix-ui)
- [GitHub Discussion #6248](https://github.com/shadcn-ui/ui/discussions/6248)

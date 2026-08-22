# Story titles and the sidebar

Two conventions live side by side, and which one applies is a property of the component, not a taste.

- **Omit `title` by default.** `.storybook/main.ts` sets no `titlePrefix`, so a story with no title is
  filed by its path under `src` — `components/admin/ai/ModelPicker`. Most stories belong here: the file
  tree *is* the grouping, and it cannot go stale.

- **Declare a `title` when the file layout cannot express where a reader looks for the thing.** A
  product surface assembled from several directories, or one an admin knows by the screen it is on,
  earns an explicit title: `Workspace admin/Practices/Review/How much`. Do not rename an existing
  explicit tree into auto-titles — the path would file it under `components/`.

- **Sentence case throughout**, for segments and story names: `Practice trace/Outcome badge`, not
  `PracticeTrace/OutcomeBadge`. A leaf named after its component is still sentence case —
  `Common/Filter toolbar`, not `Common/FilterToolbar`. Product terms and acronyms keep their capitals
  (`AI mentoring`, `Hephaestus default panel`).

- **There are exactly two admin consoles, so there are exactly two admin namespaces.**
  `Workspace admin/…` for anything reached under `w/$workspaceSlug/admin/**`, `Instance admin/…` for
  anything under the `isAppAdmin` routes in `_authenticated/admin.*`. Never a bare `Admin/…`: the reader
  cannot tell which console it is, and every file that used to sit there was a workspace surface. A
  presentational component **both** consoles render belongs to neither — file it under `Shared/…`, as
  `Shared/Practice catalog/Area visual picker` already does.

- **A leaf and a folder must not share a name.** If `Foo` gains children, the leaf becomes `Foo/Overview`.

- **Every top-level segment must appear in `storySort.order` in `.storybook/preview.ts`.** One that is
  missing sorts alphabetically after every named one, which silently buries it. This applies to derived
  titles too — `integrations` is the auto-title of two feature-flag stories, and was missing.

- **A cross-cutting regression suite is not a component.** A file with no `component`, covering several
  primitives at once, belongs under `Tests/` — see `src/components/ui/overlay-reflow.stories.tsx`.

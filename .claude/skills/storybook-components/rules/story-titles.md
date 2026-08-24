# Story titles and the sidebar

Two conventions live side by side, and which one applies is a property of the component, not a taste.

- **Omit `title` by default.** `webapp/.storybook/main.ts` globs `../src/**` with no `titlePrefix`, so
  a story with no title is filed by its path under `src` — `components/admin/ai/ModelPicker`. Most
  stories belong here: the file tree *is* the grouping, and it cannot go stale.

- **Declare a `title` when the file layout cannot express where a reader looks for the thing.** A
  product surface assembled from several directories, or one an admin knows by the screen it is on,
  earns an explicit title: `Workspace admin/Practices/Review/How much`. Do not rename an existing
  explicit tree into auto-titles — the path would file it under `components/`.

- **Sentence case throughout**, for segments and story names: `Practice trace/Outcome badge`, not
  `PracticeTrace/OutcomeBadge`. A leaf named after its component is still sentence case —
  `Common/Filter toolbar`, not `Common/FilterToolbar`. Product terms and acronyms keep their capitals
  (`AI mentoring`, `Hephaestus default panel`).

- **There are exactly two admin consoles, so there are exactly two admin namespaces.**
  `Workspace admin/…` for anything under `webapp/src/routes/_authenticated/w/$workspaceSlug/admin/**`,
  `Instance admin/…` for the `isAppAdmin`-guarded `webapp/src/routes/_authenticated/admin.*`. Never a
  bare `Admin/…` — the reader cannot tell which console it is. A presentational component **both**
  consoles render belongs to neither: file it under `Shared/…`, as
  `Shared/Practice catalog/Area visual picker` does.

- **A leaf and a folder must not share a name.** If `Foo` gains children, the leaf becomes `Foo/Overview`.

- **Every top-level segment must appear in `storySort.order` in `webapp/.storybook/preview.tsx`, and
  every entry there must match a story.** A missing segment sorts alphabetically after every named one,
  which buries it silently; a stale entry orders nothing. `check:story-sort` fails the build on either.
  It reads `title:` at exactly one tab inside the `const meta` block, so a title assembled at runtime
  rather than written as a string literal fails the gate loudly.

- **A cross-cutting regression suite is not a component.** A file with no `component`, covering several
  primitives at once, belongs under `Tests/` — see `webapp/src/components/ui/overlay-reflow.stories.tsx`.

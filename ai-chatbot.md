# Notes

## data-stream-provider.tsx

## data-stream-handler.tsx

## chat-header.tsx

## artifact.tsx

## chat.tsx

## sidebar-history.tsx

- [ ] Remove grouping on server side
- [ ] Regenerate API client npm run generate:api:application-server
- [ ] groupChatsByDate on the client side
- [ ] Paginate history items
- [ ] Delete chat option
- [ ] Storybook story?

## sidebar-history-item.tsx

- [ ] Have a similar item
- [ ] Storybook story

## Extensions

suggestion.tsx // inline artifact suggestions
model-selector.tsx // potentially at some later point, similar to profiles, could be just for admin only
image-editor.tsx // at least we would have some mermaid js viewer
diffview.tsx
toolbar.tsx // Bottom right thingy in artifact

## TODO

### lib/types.ts

- [ ] Typesafe tools

### DocumentSkeleton

- [ ] Merge into one component with inline prop

## DONE

### code-block.tsx

- [x] Already copied over
- [x] Storybook story

### weather.tsx

- [x] Copied over
- [x] Storybook story

### suggested-actions.tsx

- [x] Starter actions for the chat
- [x] Change to presentational component
- [x] Storybook story

### preview-attachment.tsx

- [x] Copy over as is, also the loading spinner / icons
- [x] Storybook story

### multimodal-input.tsx

- [x] Replace current with this and have option to disable attachments (for now the default)
- [x] Change component API to be less smart
- [x] Storybook story

### markdown.tsx

- [x] Already copied over
- [x] Storybook story

### greeting.tsx

- [x] Copy over as is
- [x] Storybook story

### message-actions.tsx

Copy button with upvote / downvote

- [x] Copy over as is
- [x] Storybook story

### message-editor.tsx

- [x] Copy over as is
- [x] Change to be purely presentational
- [x] Storybook story

### message-reasoning.tsx

- [x] Copy over as is
- [x] Storybook story

### document.tsx

- [x] Reworked and combined tool call with result
- [x] Renamed to DocumentTool

### document-skeleton.tsx

### document-preview.tsx

### text-editor.tsx

### message.tsx

### messages.tsx

### create-artifact.tsx

### artifact-actions.tsx

### artifact-close-button.tsx

### version-footer.tsx

## artifact-messages.tsx

Incorporated into Messages.tsx

### app-sidebar.tsx

Nothing to do

### sidebar-user-nav.tsx

Nothing to do

### sidebar-toggle.tsx

Nothing to do

## Not needed

sign-out-form.tsx
submit-button.tsx // Auth submit button
theme-provider.tsx
toast.tsx
sheet-editor.tsx
auth-form.tsx
code-editor.tsx
console.tsx
visibility-selector.tsx

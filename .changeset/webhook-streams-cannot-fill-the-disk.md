---
"hephaestus": minor
---

Webhook message streams can no longer fill the disk and take ingestion down with them. They were bounded only by a message count, which says nothing about storage: one deployment's GitHub stream reached 32.3 GB at exactly its cap, filled the host, stopped the broker writing, and dropped every inbound webhook until the broker was restarted by hand.

Each stream now states both of its bounds: how long a delivery is kept at most, and a disk ceiling under that. Which one you actually get depends on your traffic — at low volume the time limit is delivered in full, at high volume the disk ceiling recycles the stream sooner — and the server reports the answer for your deployment as the age of the oldest message it still holds. It refuses to start if the streams together are allowed more than the broker's own budget. Lowering a limit also takes effect on a stream that already exists, instead of being reported and ignored; a change that would delete messages already stored is held back and logged with exactly what it would cost until you allow it, and a change that would leave a stream with no limit at all is held back regardless.

Deliveries larger than the broker will carry are no longer accepted and then lost at publish: the broker is configured to take everything the receiver admits, and the receiver says so loudly if the two disagree.

**Operators:** `NATS_JS_MAX_FILE` is **removed** and nothing reads it any more. Replace it with `NATS_JS_MAX_FILE_BYTES`, in bytes — a deployment that leaves the old variable set silently drops to the new 16 GiB default instead of the 50 GB it had. Set it below the free space on the broker's volume, and keep the per-stream ceilings totalling under it or the server will not start. The 180-day retention limit is unchanged, but a busy GitHub stream now recycles on disk well before that; `HEPHAESTUS_WEBHOOK_STREAM_MAX_BYTES` and the new `HEPHAESTUS_WEBHOOK_STREAM_MAX_BYTES_GITHUB` set that ceiling. A stream already larger than its new ceiling stays as it is and logs what bounding it would delete, until you set `HEPHAESTUS_WEBHOOK_STREAM_ALLOW_DESTRUCTIVE_LIMIT_UPDATES=true` once. See MIGRATION.md.

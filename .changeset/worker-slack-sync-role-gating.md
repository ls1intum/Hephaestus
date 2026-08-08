---
"hephaestus": patch
---

Fixes practice reviews and mentor replies never arriving on deployments that run the background
worker as its own container with the Slack integration switched on. The worker never finished
starting and was restarted over and over, so nothing picked the queued work up, and the people
waiting on a review or a reply saw no error — only silence. Slack channel syncing was, and remains,
the application server's job. Deployments that run everything in one container, or that leave Slack
switched off, were never affected.

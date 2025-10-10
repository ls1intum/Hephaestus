---
title: Mail Notifications
sidebar_position: 8
description: Configure outbound email for development and production environments.
---

Hephaestus sends mail notifications to users when it automatically detects bad practices in pull requests. Development and production use different SMTP servers.

## Development Mail Server
For development a personal mail server like Gmail can be used.
To configure the mail server, create or use a existing Gmail account.
Create an app password and set the local properties:

- `host`: `${MAIL_HOST:smtp.gmail.com}`
- `port`: `${MAIL_PORT:587}`
- `username`: `${POSTFIX_USERNAME:}` (your Gmail address, e.g., `hephaestus@gmail.com`)
- `password`: `${POSTFIX_PASSWORD:}` (the Gmail app password—[follow the Google guide](https://support.google.com/mail/answer/185833?hl=en))

Now sending mails from Hephaestus should work.

## Production Mail Server
Production uses a Postfix mail server provided by TUM. Most settings are configured automatically, but you must copy `main.cf` and `master.cf` from [ls1admin/postfix-container-tum-mailrelay](https://github.com/ls1admin/postfix-container-tum-mailrelay) to the Postfix server.

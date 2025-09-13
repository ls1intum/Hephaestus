# Mail Notifications
Hephaestus sends mail notifications to users when it automatically detects bad practices in pull requests.
For development and production we use different mail servers to deliver mail notifications.

## Development Mail Server
For development a personal mail server like Gmail can be used.
To configure the mail server, create or use a existing Gmail account.
Create a app code password and set the local properties:
  -  host: ${MAIL_HOST:smtp.gmail.com}
  -  port: ${MAIL_PORT:587}
  -  username: ${POSTFIX_USERNAME:} # gmail email address e.g hephaestus@gmail.com
  -  password: ${POSTFIX_PASSWORD:} # app code password from gmail. how to: https://support.google.com/mail/answer/185833?hl=en

Now sending mails from Hephaestus should work.

## Production Mail Server
For production we use a Postfix mail server provided by the TUM.
Everything should be configured automatically, but the main.cf and master.cf files have to be copied manually to the Postfix server from https://github.com/ls1admin/postfix-container-tum-mailrelay

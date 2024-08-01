# Webhook Distributor

A simple webhook distributor that forwards incoming webhooks to multiple destinations.  

## Usage

### Register a destination

To register a destination, send a POST request to `/register` with the following JSON payload:

```json
{
  "url": "<destination url>"
}
```

### Send a webhook

To send a webhook, send a POST request to `/`. The webhook will be forwarded to all registered destinations.
// ruleid: node-tls-verification-disabled
const insecure = { rejectUnauthorized: false };
// ok: node-tls-verification-disabled
const secure = { rejectUnauthorized: true };
// ruleid: node-tls-verification-disabled
process.env.NODE_TLS_REJECT_UNAUTHORIZED = "0";
// ok: node-tls-verification-disabled
process.env.NODE_EXTRA_CA_CERTS = "/etc/ssl/private-ca.pem";

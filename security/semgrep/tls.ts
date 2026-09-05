// ruleid: node-tls-verification-disabled
const insecure = { rejectUnauthorized: false };
// ok: node-tls-verification-disabled
const secure = { rejectUnauthorized: true };
// ok: node-tls-verification-disabled
const defaults = { ca: trustedCa };

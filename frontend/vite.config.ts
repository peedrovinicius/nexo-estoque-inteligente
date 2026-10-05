import { defineConfig } from 'vite';

const securityHeaders={
  'X-Content-Type-Options':'nosniff',
  'X-Frame-Options':'DENY',
  'Referrer-Policy':'strict-origin-when-cross-origin',
  'Permissions-Policy':'camera=(self), microphone=(), geolocation=()',
  'Cross-Origin-Opener-Policy':'same-origin',
  'Content-Security-Policy':[
    "default-src 'self'",
    "connect-src 'self' https://*.up.railway.app",
    "img-src 'self' data:",
    "style-src 'self' 'unsafe-inline'",
    "script-src 'self'",
    "font-src 'self' data:",
    "media-src 'self' blob:",
    "worker-src 'self' blob:",
    "object-src 'none'",
    "base-uri 'self'",
    "frame-ancestors 'none'",
    "form-action 'self'"
  ].join('; ')
};

export default defineConfig({
  server:{headers:securityHeaders},
  preview:{headers:securityHeaders}
});

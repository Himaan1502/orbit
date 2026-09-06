/**
 * Server-only OIDC / BFF configuration (ADR-013). Never import this from a
 * Client Component — `WEB_CLIENT_SECRET` and `SESSION_SECRET` must not reach
 * the browser bundle.
 */
import 'server-only';

function required(name: string, value: string | undefined): string {
  if (!value) {
    throw new Error(`Missing required env var ${name}`);
  }
  return value;
}

export const authConfig = {
  /** Issuer as seen by the browser (redirects go here). */
  issuer: process.env.OIDC_ISSUER_URI ?? 'http://localhost:9000',
  /** Issuer base URL the BFF uses server-to-server (token, jwks). Usually the same. */
  internalIssuer:
    process.env.OIDC_INTERNAL_ISSUER_URI ?? process.env.OIDC_ISSUER_URI ?? 'http://localhost:9000',
  clientId: process.env.WEB_CLIENT_ID ?? 'orbit-web',
  get clientSecret() {
    return required('WEB_CLIENT_SECRET', process.env.WEB_CLIENT_SECRET);
  },
  redirectUri: process.env.WEB_REDIRECT_URI ?? 'http://localhost:3000/auth/callback',
  postLogoutRedirectUri: process.env.WEB_POST_LOGOUT_URI ?? 'http://localhost:3000',
  /**
   * The app's own public origin, used to build absolute redirect URLs in route
   * handlers. `request.nextUrl.origin` is unreliable behind the Next standalone
   * server (reports the bind address `0.0.0.0`), so this is configured, not
   * inferred. Defaults from WEB_PUBLIC_URL, else the post-logout URI's origin.
   */
  get publicUrl(): string {
    if (process.env.WEB_PUBLIC_URL) {
      return process.env.WEB_PUBLIC_URL.replace(/\/$/, '');
    }
    return new URL(process.env.WEB_POST_LOGOUT_URI ?? 'http://localhost:3000').origin;
  },
  /** apps/api base, reached only from the BFF proxy (server-to-server). */
  apiBaseUrl: process.env.API_BASE_URL ?? 'http://localhost:8080',
  scope: 'openid profile orbit.read orbit.write',
  get sessionSecret() {
    return required('SESSION_SECRET', process.env.SESSION_SECRET);
  },
  /**
   * `Secure` cookie flag. Auto: on when the app is served over https (inferred
   * from the redirect URI), off for local http. Override with
   * `WEB_SECURE_COOKIES=true|false`.
   */
  get secureCookies(): boolean {
    const override = process.env.WEB_SECURE_COOKIES;
    if (override === 'true') return true;
    if (override === 'false') return false;
    return (process.env.WEB_REDIRECT_URI ?? 'http://').startsWith('https');
  },
} as const;

export const SESSION_COOKIE = 'orbit_session';
export const TXN_COOKIE = 'orbit_txn';

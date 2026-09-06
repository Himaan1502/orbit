/**
 * @vitest-environment node
 *
 * jose uses the platform's real WebCrypto / TextEncoder; jsdom's polyfilled
 * versions trip its cross-realm instanceof checks. This module only ever runs
 * server-side anyway.
 */
import { describe, it, expect, vi, beforeAll } from 'vitest';

// These modules are server-only and pull in next/headers; stub both so the pure
// logic (cookie sealing, same-origin checks) is unit-testable.
vi.mock('server-only', () => ({}));
vi.mock('next/headers', () => ({
  cookies: () => ({ get: () => undefined, set: () => undefined, delete: () => undefined }),
}));

beforeAll(() => {
  process.env.SESSION_SECRET = 'unit-test-session-secret-1234567890';
  process.env.WEB_CLIENT_SECRET = 'unit-test-client-secret';
  process.env.WEB_POST_LOGOUT_URI = 'http://localhost:3000';
});

describe('session cookie sealing', () => {
  it('round-trips a session and rejects tampering', async () => {
    const { sealSession, readSessionFromValue } = await import('./session');

    const session = {
      sub: '11111111-1111-1111-1111-111111111111',
      email: 'user@example.com',
      accessToken: 'aaa.bbb.ccc',
      refreshToken: 'refresh-1',
      idToken: 'id.token.value',
      expiresAt: Math.floor(Date.now() / 1000) + 900,
    };

    const sealed = await sealSession(session);
    expect(sealed).not.toContain('user@example.com'); // encrypted, not just signed

    const opened = await readSessionFromValue(sealed);
    expect(opened?.email).toBe('user@example.com');
    expect(opened?.sub).toBe(session.sub);

    expect(await readSessionFromValue(sealed.slice(0, -3) + 'xxx')).toBeNull();
    expect(await readSessionFromValue('not-a-jwe')).toBeNull();
    expect(await readSessionFromValue(undefined)).toBeNull();
  });
});

describe('isSameOrigin', () => {
  it('accepts matching Origin/Host and rejects a cross-site Origin', async () => {
    const { isSameOrigin } = await import('./request');

    const make = (headers: Record<string, string>) =>
      ({ headers: { get: (k: string) => headers[k.toLowerCase()] ?? null } }) as never;

    expect(isSameOrigin(make({ origin: 'http://localhost:3000', host: 'localhost:3000' }))).toBe(
      true
    );
    expect(isSameOrigin(make({ origin: 'http://evil.example', host: 'localhost:3000' }))).toBe(
      false
    );
    // No Origin header (top-level form POST): fall back to Sec-Fetch-Site.
    expect(isSameOrigin(make({ 'sec-fetch-site': 'same-origin' }))).toBe(true);
    expect(isSameOrigin(make({ 'sec-fetch-site': 'cross-site' }))).toBe(false);
  });
});

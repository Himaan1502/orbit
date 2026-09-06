/**
 * Encrypted session cookie (ADR-013 §1). The cookie is a compact JWE
 * (`dir` + `A256GCM`) holding the OIDC tokens; the browser cannot read it and
 * the tokens never reach client JavaScript.
 */
import 'server-only';
import { cookies } from 'next/headers';
import { EncryptJWT, jwtDecrypt } from 'jose';
import { authConfig, SESSION_COOKIE } from './config';

export interface Session {
  sub: string;
  email: string;
  accessToken: string;
  refreshToken?: string;
  idToken?: string;
  /** Access-token expiry, epoch seconds. */
  expiresAt: number;
}

let cachedKey: Uint8Array | null = null;

async function key(): Promise<Uint8Array> {
  if (!cachedKey) {
    const digest = await crypto.subtle.digest(
      'SHA-256',
      new TextEncoder().encode(authConfig.sessionSecret)
    );
    cachedKey = new Uint8Array(digest);
  }
  return cachedKey;
}

export async function sealSession(session: Session): Promise<string> {
  return new EncryptJWT({ ...session })
    .setProtectedHeader({ alg: 'dir', enc: 'A256GCM' })
    .setIssuedAt()
    .setExpirationTime('7d')
    .encrypt(await key());
}

export async function readSession(): Promise<Session | null> {
  const raw = cookies().get(SESSION_COOKIE)?.value;
  if (!raw) {
    return null;
  }
  try {
    const { payload } = await jwtDecrypt(raw, await key());
    return payload as unknown as Session;
  } catch {
    return null;
  }
}

export async function readSessionFromValue(raw: string | undefined): Promise<Session | null> {
  if (!raw) {
    return null;
  }
  try {
    const { payload } = await jwtDecrypt(raw, await key());
    return payload as unknown as Session;
  } catch {
    return null;
  }
}

function cookieOptions() {
  return {
    httpOnly: true,
    secure: authConfig.secureCookies,
    sameSite: 'lax' as const,
    path: '/',
  };
}

export async function writeSessionCookie(session: Session): Promise<void> {
  cookies().set(SESSION_COOKIE, await sealSession(session), {
    ...cookieOptions(),
    maxAge: 60 * 60 * 24 * 7,
  });
}

export function clearSessionCookie(): void {
  cookies().set(SESSION_COOKIE, '', { ...cookieOptions(), maxAge: 0 });
}

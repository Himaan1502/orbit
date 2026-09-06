import { NextRequest, NextResponse } from 'next/server';
import { cookies } from 'next/headers';
import { createAuthTransaction } from '@/lib/auth/oidc';
import { authConfig, TXN_COOKIE } from '@/lib/auth/config';

/**
 * Starts the Authorization Code + PKCE flow (ADR-013 §2). Stashes the PKCE
 * verifier / state / nonce in a short-lived HttpOnly cookie and redirects the
 * browser to the auth server.
 */
export async function GET(request: NextRequest) {
  const returnToParam = request.nextUrl.searchParams.get('returnTo') ?? '/';
  // Only allow same-app relative paths as a return target.
  const returnTo =
    returnToParam.startsWith('/') && !returnToParam.startsWith('//') ? returnToParam : '/';

  const { txn, authorizeUrl } = await createAuthTransaction(returnTo);

  cookies().set(TXN_COOKIE, JSON.stringify(txn), {
    httpOnly: true,
    secure: authConfig.secureCookies,
    sameSite: 'lax',
    path: '/',
    maxAge: 600,
  });

  return NextResponse.redirect(authorizeUrl);
}

import { NextRequest, NextResponse } from 'next/server';
import { cookies } from 'next/headers';
import { exchangeCode, verifyIdToken } from '@/lib/auth/oidc';
import { writeSessionCookie } from '@/lib/auth/session';
import { authConfig, TXN_COOKIE } from '@/lib/auth/config';
import type { PkceTxn } from '@/lib/auth/oidc';

export const dynamic = 'force-dynamic';

/**
 * OIDC redirect target (ADR-013 §2). Validates `state`, exchanges the code for
 * tokens (confidential client), verifies the ID token, and writes the encrypted
 * session cookie.
 */
export async function GET(request: NextRequest) {
  const params = request.nextUrl.searchParams;
  const error = params.get('error');
  if (error) {
    return redirectToError(request, `provider returned "${error}"`);
  }

  const code = params.get('code');
  const state = params.get('state');
  const txnRaw = cookies().get(TXN_COOKIE)?.value;
  cookies().delete(TXN_COOKIE);

  if (!code || !state || !txnRaw) {
    return redirectToError(request, 'missing code, state, or transaction');
  }

  let txn: PkceTxn;
  try {
    txn = JSON.parse(txnRaw) as PkceTxn;
  } catch {
    return redirectToError(request, 'unreadable transaction');
  }

  if (state !== txn.state) {
    return redirectToError(request, 'state mismatch');
  }

  try {
    const tokens = await exchangeCode(code, txn.verifier);
    const { sub, email } = tokens.id_token
      ? await verifyIdToken(tokens.id_token, txn.nonce)
      : { sub: '', email: '' };

    await writeSessionCookie({
      sub,
      email,
      accessToken: tokens.access_token,
      refreshToken: tokens.refresh_token,
      idToken: tokens.id_token,
      expiresAt: Math.floor(Date.now() / 1000) + tokens.expires_in,
    });
  } catch (e) {
    return redirectToError(request, e instanceof Error ? e.message : 'token exchange failed');
  }

  const returnTo = txn.returnTo?.startsWith('/') ? txn.returnTo : '/';
  return NextResponse.redirect(new URL(returnTo, authConfig.publicUrl));
}

function redirectToError(_request: NextRequest, reason: string) {
  const url = new URL('/login', authConfig.publicUrl);
  url.searchParams.set('auth_error', reason);
  return NextResponse.redirect(url);
}

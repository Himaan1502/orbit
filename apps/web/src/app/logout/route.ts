import { NextRequest, NextResponse } from 'next/server';
import { readSession, clearSessionCookie } from '@/lib/auth/session';
import { endSessionUrl } from '@/lib/auth/oidc';
import { isSameOrigin } from '@/lib/auth/request';

export const dynamic = 'force-dynamic';

/**
 * Clears the local session and hands off to the auth server's OIDC end-session
 * endpoint (ADR-013 §2). POST only + same-origin check — CSRF-safe.
 */
export async function POST(request: NextRequest) {
  if (!isSameOrigin(request)) {
    return new NextResponse('cross-origin', { status: 403 });
  }
  const session = await readSession();
  clearSessionCookie();
  return NextResponse.redirect(new URL(endSessionUrl(session?.idToken)), { status: 303 });
}

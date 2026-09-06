import { NextRequest, NextResponse } from 'next/server';
import {
  readSession,
  writeSessionCookie,
  clearSessionCookie,
  type Session,
} from '@/lib/auth/session';
import { refreshTokens } from '@/lib/auth/oidc';
import { authConfig } from '@/lib/auth/config';
import { isSameOrigin } from '@/lib/auth/request';

/**
 * Backend-for-Frontend proxy (ADR-013 §3). The browser only ever calls this
 * same-origin endpoint; the OIDC access token is attached here, server-side,
 * and refreshed transparently. `apps/api` never sees a browser cookie.
 */
export const dynamic = 'force-dynamic';

async function handler(request: NextRequest, ctx: { params: { path: string[] } }) {
  if (!isSameOrigin(request)) {
    return NextResponse.json(
      { error: { code: 'FORBIDDEN', message: 'cross-origin' } },
      { status: 403 }
    );
  }

  let session = await readSession();
  if (!session) {
    return NextResponse.json(
      { error: { code: 'UNAUTHENTICATED', message: 'No session.' } },
      { status: 401 }
    );
  }

  // Refresh proactively when the access token is about to expire.
  if (session.refreshToken && session.expiresAt - Math.floor(Date.now() / 1000) < 60) {
    const refreshed = await tryRefresh(session);
    if (!refreshed) {
      clearSessionCookie();
      return NextResponse.json(
        { error: { code: 'SESSION_EXPIRED', message: 'Please sign in again.' } },
        { status: 401 }
      );
    }
    session = refreshed;
  }

  const upstream = await forward(request, ctx.params.path, session.accessToken);

  // A 401 the refresh could not prevent — drop the session so the client re-auths.
  if (upstream.status === 401) {
    clearSessionCookie();
  }
  return upstream;
}

async function forward(
  request: NextRequest,
  path: string[],
  accessToken: string
): Promise<NextResponse> {
  const target = `${authConfig.apiBaseUrl}/api/v1/${path.join('/')}${request.nextUrl.search}`;
  const headers = new Headers();
  const contentType = request.headers.get('content-type');
  if (contentType) headers.set('content-type', contentType);
  headers.set('authorization', `Bearer ${accessToken}`);
  headers.set('x-request-id', request.headers.get('x-request-id') ?? crypto.randomUUID());
  headers.set('accept', request.headers.get('accept') ?? 'application/json');

  const hasBody = !['GET', 'HEAD'].includes(request.method);
  const res = await fetch(target, {
    method: request.method,
    headers,
    body: hasBody ? await request.text() : undefined,
    cache: 'no-store',
    redirect: 'manual',
  });

  const body = await res.text();
  const out = new NextResponse(body, { status: res.status });
  const resType = res.headers.get('content-type');
  if (resType) out.headers.set('content-type', resType);
  return out;
}

async function tryRefresh(session: Session): Promise<Session | null> {
  try {
    const tokens = await refreshTokens(session.refreshToken!);
    const next: Session = {
      ...session,
      accessToken: tokens.access_token,
      refreshToken: tokens.refresh_token ?? session.refreshToken,
      idToken: tokens.id_token ?? session.idToken,
      expiresAt: Math.floor(Date.now() / 1000) + tokens.expires_in,
    };
    await writeSessionCookie(next);
    return next;
  } catch {
    return null;
  }
}

export { handler as GET, handler as POST, handler as PUT, handler as PATCH, handler as DELETE };

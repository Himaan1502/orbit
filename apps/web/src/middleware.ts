import { NextRequest, NextResponse } from 'next/server';
import { SESSION_COOKIE } from '@/lib/auth/config';

/**
 * Route protection for UX (ADR-013 §5). A missing session cookie bounces the
 * user to sign-in. This is defence-in-depth only — real authorization is
 * `apps/api` rejecting un-bearer'd requests and enforcing ownership, independent
 * of anything the UI shows (Tech Arch §9). Cookie *validity* is checked by the
 * BFF proxy / route handlers, not here (keeps middleware edge-light).
 */
// `/api/` is excluded so the BFF proxy returns a proper 401 JSON instead of an
// HTML redirect; it enforces its own session check.
const PUBLIC_PREFIXES = ['/login', '/signup', '/logout', '/auth/', '/api/'];

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  if (PUBLIC_PREFIXES.some((p) => pathname === p || pathname.startsWith(p))) {
    return NextResponse.next();
  }

  if (request.cookies.has(SESSION_COOKIE)) {
    return NextResponse.next();
  }

  const loginUrl = new URL('/login', request.nextUrl.origin);
  loginUrl.searchParams.set('returnTo', pathname + request.nextUrl.search);
  return NextResponse.redirect(loginUrl);
}

export const config = {
  // Everything except Next internals and common static assets.
  matcher: [
    '/((?!_next/static|_next/image|favicon.ico|.*\\.(?:svg|png|jpg|jpeg|gif|webp|ico)$).*)',
  ],
};

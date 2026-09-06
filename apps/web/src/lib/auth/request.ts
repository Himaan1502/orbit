import 'server-only';
import type { NextRequest } from 'next/server';

/**
 * Same-origin guard for state-changing BFF routes (ADR-013 §4). Compares the
 * `Origin` header's host to the request's `Host` header rather than
 * `request.nextUrl.origin`, which is unreliable behind the Next standalone
 * server. A missing `Origin` (common on top-level form POSTs) falls back to
 * `Sec-Fetch-Site`.
 */
export function isSameOrigin(request: NextRequest): boolean {
  const origin = request.headers.get('origin');
  if (!origin) {
    const site = request.headers.get('sec-fetch-site');
    return site === null || site === 'same-origin' || site === 'same-site' || site === 'none';
  }
  const host = request.headers.get('host');
  try {
    return new URL(origin).host === host;
  } catch {
    return false;
  }
}

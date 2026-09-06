import { NextResponse } from 'next/server';
import { authConfig } from '@/lib/auth/config';

// Read OIDC config at request time, not build time.
export const dynamic = 'force-dynamic';

/**
 * Sends the user to the auth server's server-rendered signup page. Password
 * entry never happens in the SPA (UX §15.1, ADR-013 §2). After registering,
 * that page links to sign-in.
 */
export async function GET() {
  return NextResponse.redirect(`${authConfig.issuer}/signup`);
}

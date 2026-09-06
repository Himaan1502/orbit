import { NextResponse } from 'next/server';
import { readSession } from '@/lib/auth/session';

/** Lightweight session probe for client components (ADR-013 §2). */
export async function GET() {
  const session = await readSession();
  if (!session) {
    return NextResponse.json({ authenticated: false }, { status: 200 });
  }
  return NextResponse.json({ authenticated: true, email: session.email, sub: session.sub });
}

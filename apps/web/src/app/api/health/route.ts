import { NextResponse } from 'next/server';

/** Liveness probe for the web container. Not behind auth middleware (`/api/*`). */
export function GET() {
  return NextResponse.json({ status: 'UP', service: 'orbit-web' });
}

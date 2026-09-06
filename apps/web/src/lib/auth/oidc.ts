/**
 * Minimal OIDC client for the BFF (ADR-013 §2). One provider, hand-rolled and
 * fully legible — no third-party auth framework.
 */
import 'server-only';
import { createRemoteJWKSet, jwtVerify } from 'jose';
import { authConfig } from './config';

const jwks = createRemoteJWKSet(new URL(`${authConfig.internalIssuer}/oauth2/jwks`));

function base64url(bytes: Uint8Array): string {
  // Route handlers run in the Node.js runtime — Buffer is available.
  return Buffer.from(bytes).toString('base64url');
}

function randomString(byteLength = 32): string {
  return base64url(crypto.getRandomValues(new Uint8Array(byteLength)));
}

export interface PkceTxn {
  verifier: string;
  state: string;
  nonce: string;
  returnTo: string;
}

export async function createAuthTransaction(returnTo: string): Promise<{
  txn: PkceTxn;
  authorizeUrl: string;
}> {
  const verifier = randomString(48);
  const challenge = base64url(
    new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier)))
  );
  const state = randomString(16);
  const nonce = randomString(16);

  const params = new URLSearchParams({
    response_type: 'code',
    client_id: authConfig.clientId,
    redirect_uri: authConfig.redirectUri,
    scope: authConfig.scope,
    state,
    nonce,
    code_challenge: challenge,
    code_challenge_method: 'S256',
  });

  return {
    txn: { verifier, state, nonce, returnTo },
    authorizeUrl: `${authConfig.issuer}/oauth2/authorize?${params.toString()}`,
  };
}

interface TokenResponse {
  access_token: string;
  refresh_token?: string;
  id_token?: string;
  expires_in: number;
  token_type: string;
}

function basicAuthHeader(): string {
  return 'Basic ' + btoa(`${authConfig.clientId}:${authConfig.clientSecret}`);
}

async function tokenRequest(body: URLSearchParams): Promise<TokenResponse> {
  const res = await fetch(`${authConfig.internalIssuer}/oauth2/token`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
      Authorization: basicAuthHeader(),
    },
    body,
    cache: 'no-store',
  });
  if (!res.ok) {
    throw new Error(`token endpoint ${res.status}: ${await res.text()}`);
  }
  return (await res.json()) as TokenResponse;
}

export async function exchangeCode(code: string, verifier: string): Promise<TokenResponse> {
  return tokenRequest(
    new URLSearchParams({
      grant_type: 'authorization_code',
      code,
      redirect_uri: authConfig.redirectUri,
      client_id: authConfig.clientId,
      code_verifier: verifier,
    })
  );
}

export async function refreshTokens(refreshToken: string): Promise<TokenResponse> {
  return tokenRequest(
    new URLSearchParams({ grant_type: 'refresh_token', refresh_token: refreshToken })
  );
}

/** Verifies an ID token's signature and core claims; returns `sub` and `email`. */
export async function verifyIdToken(
  idToken: string,
  expectedNonce: string
): Promise<{ sub: string; email: string }> {
  const { payload } = await jwtVerify(idToken, jwks, {
    issuer: authConfig.issuer,
    audience: authConfig.clientId,
  });
  if (payload.nonce !== expectedNonce) {
    throw new Error('ID token nonce mismatch');
  }
  return { sub: String(payload.sub), email: String(payload.email ?? '') };
}

/** Reads `sub`/`email` from an access token after verifying its signature. */
export async function verifyAccessToken(
  accessToken: string
): Promise<{ sub: string; email: string; exp: number }> {
  const { payload } = await jwtVerify(accessToken, jwks, { issuer: authConfig.issuer });
  return {
    sub: String(payload.sub),
    email: String(payload.email ?? ''),
    exp: Number(payload.exp ?? 0),
  };
}

export function endSessionUrl(idToken?: string): string {
  const params = new URLSearchParams({
    post_logout_redirect_uri: authConfig.postLogoutRedirectUri,
    client_id: authConfig.clientId,
  });
  if (idToken) {
    params.set('id_token_hint', idToken);
  }
  return `${authConfig.issuer}/connect/logout?${params.toString()}`;
}

---
status: accepted
supersedes: password-auth-with-hmac-cookie (originally M001)
---

# WorkOS passkey-only auth via `@workos-inc/node` with manual OAuth/PKCE

Authentication uses WorkOS AuthKit with passkey-only login. There is no `@workos-inc/authkit-sveltekit` package — only the Next.js variant exists — so we use the generic `@workos-inc/node` SDK and implement the authorization-code/PKCE exchange, encrypted session cookie, and refresh flow ourselves in SvelteKit hooks and routes.

Passkeys are more secure and convenient than passwords for an app with a small known user set. WorkOS's free tier handles the WebAuthn ceremony, credential storage, and session lifetime, removing the need to maintain custom WebAuthn code.

## Consequences

- The OAuth/PKCE flow is hand-rolled in SvelteKit and must be re-checked when WorkOS SDK majors bump.
- Replaces the earlier M001 design (password + bcrypt + HMAC-SHA256 signed cookie); none of that code remains.
- If WorkOS publishes an official SvelteKit AuthKit package, revisit this decision — the manual integration could be swapped out.

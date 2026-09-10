# Multi-stage build for the ORBIT web BFF (Next.js standalone).
#
# The `build` stage is pinned to the *build* platform (the CI runner, amd64).
# The web app has no native runtime dependencies (no sharp / no native addons —
# see apps/web/package.json), so `next build`'s `.next/standalone` output is
# portable JavaScript and runs unchanged on the linux/arm64 target (the OCI
# Ampere A1 host). The runtime stage resolves to the target platform and only
# copies build output in. See ADR-035 §3.
FROM --platform=$BUILDPLATFORM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM node:20-alpine
LABEL org.opencontainers.image.source="https://github.com/Himaan1502/orbit"
WORKDIR /app
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
COPY --from=build /app/public ./public
EXPOSE 3000
ENV PORT=3000
# Bind to all interfaces. Next's standalone server.js uses $HOSTNAME as the bind
# address, and Docker sets $HOSTNAME to the container id (which resolves to the
# container's eth0 IP only) — that leaves localhost/127.0.0.1 refused inside the
# container and breaks the healthcheck. Pin it to 0.0.0.0.
ENV HOSTNAME=0.0.0.0
CMD ["node", "server.js"]

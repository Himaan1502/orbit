FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM node:20-alpine
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

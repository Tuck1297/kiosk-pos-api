# syntax=docker/dockerfile:1
# Gateway: the cashier register (static files) plus the load balancer in front of the
# API replicas, both served by nginx. Build context is the repository root, because the
# register's TypeScript types are generated from ../openapi.yaml.

FROM node:22-alpine AS build
WORKDIR /repo/web
COPY web/package.json ./
RUN --mount=type=cache,target=/root/.npm npm install --no-audit --no-fund
COPY openapi.yaml /repo/openapi.yaml
COPY web/ ./
RUN npm run build

# nginx 1.27.3+ re-resolves upstream DNS (server ... resolve), so scaling the API
# up or down is picked up without restarting the gateway.
FROM nginx:1.29-alpine
COPY web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /repo/web/dist /usr/share/nginx/html
HEALTHCHECK --interval=5s --timeout=3s --retries=6 CMD wget -qO- http://127.0.0.1/healthz >/dev/null || exit 1

# =============================================================================
# FinPlay frontend — Vite + React, served by nginx with SPA fallback routing.
# Portable: builds identically on Mac and Windows.
# =============================================================================

# ---------- 1. Build stage --------------------------------------------------
FROM node:20-alpine AS build
WORKDIR /app

# Cache deps layer
COPY package.json package-lock.json* ./
RUN npm ci --no-audit --no-fund

# Bring in the rest of the source
COPY . .

# Vite reads VITE_* env vars at build time. Pass via --build-arg.
ARG VITE_API_URL=http://localhost:8080
ENV VITE_API_URL=$VITE_API_URL

RUN npm run build

# ---------- 2. Runtime stage ------------------------------------------------
FROM nginx:1.27-alpine AS runtime

# Replace default config with SPA-aware one.
RUN rm /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy compiled static assets.
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=3 \
    CMD wget -qO- http://localhost:80/ >/dev/null || exit 1

CMD ["nginx", "-g", "daemon off;"]

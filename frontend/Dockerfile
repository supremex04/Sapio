# Sapio web app: the built frontend served by nginx, which also proxies /api to the backend (same origin, so the
# refresh cookie and CORS need nothing special). Build: docker build -t sapio-web frontend/
FROM node:22-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
# Hosts with a request size cap (Cloud Run: 32 MiB) lower the recording limit; matches SAPIO_MAX_MEDIA_BYTES.
ARG VITE_MAX_MEDIA_BYTES=
ENV VITE_MAX_MEDIA_BYTES=$VITE_MAX_MEDIA_BYTES
RUN npm run build

FROM nginx:1.27-alpine
# The official image fills ${...} in templates from the environment at start-up.
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /src/dist /usr/share/nginx/html
ENV PORT=8080 \
    BACKEND_URL=http://localhost:8080
EXPOSE 8080

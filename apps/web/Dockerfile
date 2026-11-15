# syntax=docker/dockerfile:1
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY docker/40-runtime-config.sh /docker-entrypoint.d/40-runtime-config.sh
RUN sed -i 's/\r$//' /docker-entrypoint.d/40-runtime-config.sh \
    && sed -i 's#pid .*#pid /tmp/nginx.pid;#' /etc/nginx/nginx.conf \
    && chmod +x /docker-entrypoint.d/40-runtime-config.sh \
    && chown -R nginx:nginx /docker-entrypoint.d /etc/nginx/conf.d /var/cache/nginx /var/run
COPY --from=build --chown=nginx:nginx /app/dist /usr/share/nginx/html
USER nginx:nginx
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --retries=5 CMD wget -qO- http://127.0.0.1:8080/healthz || exit 1

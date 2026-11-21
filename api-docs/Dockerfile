# syntax=docker/dockerfile:1
FROM nginx:1.30.6-alpine AS swagger-ui
ADD --checksum=sha256:d0aefadc276b19faff1e58bc1777e03667f7cb82fc89b273d209c0287865f415 \
    https://registry.npmjs.org/swagger-ui-dist/-/swagger-ui-dist-5.35.1.tgz /swagger-ui.tgz
RUN mkdir /swagger-ui && tar -xzf /swagger-ui.tgz -C /swagger-ui --strip-components=1

FROM nginx:1.30.6-alpine
RUN rm /usr/share/nginx/html/*
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=swagger-ui /swagger-ui/swagger-ui.css /swagger-ui/index.css /swagger-ui/swagger-ui-bundle.js \
    /swagger-ui/favicon-16x16.png /swagger-ui/favicon-32x32.png /usr/share/nginx/html/
COPY index.html swagger-initializer.js /usr/share/nginx/html/

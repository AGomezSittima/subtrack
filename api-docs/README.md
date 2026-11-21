# Subtrack API docs

Swagger UI for `contract/openapi.yaml`, for local development only. Design: `docs/sdd.md`, Architecture and deployment, API docs (dev profile).

```sh
just dev fastapi    # stack with one backend plus the docs at http://127.0.0.1:8081
just test-docs      # page lists every operation, /api reaches the backend
```

| File                     | What                                                                                |
| ------------------------ | ----------------------------------------------------------------------------------- |
| `Dockerfile`             | nginx plus the Swagger UI assets from `swagger-ui-dist`, pinned version and SHA-256 |
| `nginx.conf`             | serves the UI, `/openapi.yaml` from the read-only `contract/` mount, proxies `/api` |
| `index.html`             | page shell; loads only the bundled assets                                           |
| `swagger-initializer.js` | Swagger UI options                                                                  |

To update Swagger UI, change the version in the `ADD` URL and its `--checksum` (`sha256sum` of the new tarball from the npm registry).

The port is `SUBTRACK_DOCS_PORT` (default 8081), set in the shell or in `.env` (see `.env.example`).

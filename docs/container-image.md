# Container image

Multi-stage [`Dockerfile`](https://github.com/platform-boutique/app-ad-service/tree/main/Dockerfile):

| Stage | Base | What happens |
|---|---|---|
| builder | `eclipse-temurin:21-jdk-alpine` | installs `libc6-compat` (needed by `protoc-gen-grpc-java` on musl), resolves deps, `./gradlew installDist` |
| runtime | `eclipse-temurin:21-jre-alpine` | copies `build/install/hipstershop`, runs as non-root |

- **Entrypoint:** `bin/AdService` (working dir `/app`). Honours `JAVA_OPTS` / `JAVA_TOOL_OPTIONS`.
- **User:** `appuser` — UID `100`, GID `101` (verified on the built image). Not a numeric `USER` in the Dockerfile, so Kubernetes `runAsNonRoot` needs `runAsUser: 100` set explicitly.
- **EXPOSE:** `9555`.
- **Docker `HEALTHCHECK`:** none (the old one hit the removed `/metrics` endpoint). Kubernetes uses gRPC probes (below).
- **Image size:** ~107 MB.
- **Build context:** the service root. No `.dockerignore` is present, so `build/`, `.gradle/` and `.git` are sent to the daemon — add one if builds feel slow.

# Testing branch: credential-free access

The `testing` branch enables Vite's guest-session bootstrap in development and provides `POST /api/v1/auth/guest` only when Spring runs with the `testing` profile. Each browser gets a separate server-issued `ROLE_USER` guest account. API authentication and per-user data ownership remain active.

Start PostgreSQL with the project's schema migrations available. In PowerShell, run the backend from the repository root with `$env:SPRING_PROFILES_ACTIVE='testing'; .\mvnw.cmd -pl backend/api spring-boot:run`. Then run `npm run dev` in `frontend`. The frontend's `.env.development` enables automatic guest bootstrap and Vite proxies API, share, and Swagger requests to port 8080. The API's testing profile uses local-only cookie and signing/encryption values; never deploy that profile to a public environment.

Swagger UI: `http://localhost:8080/swagger-ui/index.html`  
OpenAPI JSON: `http://localhost:8080/v3/api-docs`

In Swagger UI, call `POST /api/v1/auth/guest`, copy `accessToken` from its response, choose **Authorize**, and paste the token. Then use **Try it out** on protected endpoints. APIs still require authorization; guest bootstrap simply removes the interactive sign-in step for this testing setup.

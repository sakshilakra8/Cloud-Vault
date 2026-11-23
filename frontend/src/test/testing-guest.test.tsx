import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { AuthProvider, useAuth } from "@/components/auth-provider";
import { tokenStore } from "@/lib/api";

function SessionProbe() {
  const { user, loading } = useAuth();
  return <output>{loading ? "loading" : user?.email ?? "signed out"}</output>;
}

afterEach(() => {
  tokenStore.set(null);
  vi.unstubAllEnvs();
});

describe("testing guest access", () => {
  it("bootstraps a guest when there is no refresh cookie", async () => {
    vi.stubEnv("VITE_TESTING_ACCESS", "true");
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input);
      if (path.endsWith("/auth/refresh")) return { ok: false, status: 401, json: async () => ({}) } as Response;
      if (path.endsWith("/auth/guest")) return { ok: true, status: 201, json: async () => ({ accessToken: "guest-access", tokenType: "Bearer", expiresIn: 900, user: { id: "guest-1", email: "guest@testing.invalid", role: "ROLE_USER" } }) } as Response;
      if (path.endsWith("/auth/me")) return { ok: true, status: 200, json: async () => ({ id: "guest-1", email: "guest@testing.invalid", role: "ROLE_USER" }) } as Response;
      throw new Error(`Unexpected request ${path}`);
    });
    vi.stubGlobal("fetch", fetchMock);
    render(<AuthProvider><SessionProbe /></AuthProvider>);
    await waitFor(() => expect(screen.getByText("guest@testing.invalid")).toBeInTheDocument());
    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining("/auth/guest"), expect.objectContaining({ method: "POST" }));
    expect(tokenStore.get()).toBe("guest-access");
  });
});

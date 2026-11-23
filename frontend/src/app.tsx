import { lazy, Suspense, type ReactNode } from "react";
import {
  createBrowserRouter,
  Navigate,
  RouterProvider,
} from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AuthProvider, useAuth } from "@/components/auth-provider";
import { ErrorBoundary } from "@/components/error-boundary";
import { ThemeProvider } from "@/components/theme-provider";
import { Toaster } from "@/components/ui/sonner";
import { TooltipProvider } from "@/components/ui/tooltip";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, refetchOnWindowFocus: false, staleTime: 30_000 },
  },
});
const StyleguidePage = lazy(() =>
  import("@/pages/styleguide").then((module) => ({
    default: module.StyleguidePage,
  })),
);
const AppShell = lazy(() =>
  import("@/components/app-shell").then((module) => ({
    default: module.AppShell,
  })),
);
const LoginPage = lazy(() =>
  import("@/pages/auth-pages").then((module) => ({
    default: module.LoginPage,
  })),
);
const RegisterPage = lazy(() =>
  import("@/pages/auth-pages").then((module) => ({
    default: module.RegisterPage,
  })),
);
const WorkspacePage = lazy(() =>
  import("@/pages/workspace-pages").then((module) => ({
    default: module.WorkspacePage,
  })),
);
const FileManagerPage = lazy(() =>
  import("@/pages/file-manager-page").then((module) => ({
    default: module.FileManagerPage,
  })),
);
const StoragePage = lazy(() => import("@/pages/step-three-pages").then((m) => ({ default: m.StoragePage })));
const ActivityPage = lazy(() => import("@/pages/step-three-pages").then((m) => ({ default: m.ActivityPage })));
const SettingsPage = lazy(() => import("@/pages/step-three-pages").then((m) => ({ default: m.SettingsPage })));
const PublicSharePage = lazy(() => import("@/pages/step-three-pages").then((m) => ({ default: m.PublicSharePage })));
const NotFoundPage = lazy(() =>
  import("@/pages/workspace-pages").then((module) => ({
    default: module.NotFoundPage,
  })),
);
function ProtectedLayout() {
  const { user, loading } = useAuth();
  if (loading)
    return (
      <main className="grid min-h-screen place-items-center">
        <p className="text-sm text-muted-foreground" role="status">
          Opening your vault…
        </p>
      </main>
    );
  return user ? (
    <Suspense fallback={<LoadingPage />}>
      <AppShell />
    </Suspense>
  ) : (
    <Navigate to="/login" replace />
  );
}
function LoadingPage() {
  return (
    <main className="grid min-h-screen place-items-center">
      <p className="text-sm text-muted-foreground" role="status">
        Loading…
      </p>
    </main>
  );
}
function GuestOnly({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();
  if (loading)
    return (
      <main className="grid min-h-screen place-items-center">
        <p role="status" className="text-sm text-muted-foreground">
          Loading…
        </p>
      </main>
    );
  return user ? <Navigate to="/" replace /> : children;
}
const router = createBrowserRouter([
  {
    path: "/login",
    element: (
      <GuestOnly>
        <LoginPage />
      </GuestOnly>
    ),
  },
  {
    path: "/register",
    element: (
      <GuestOnly>
        <RegisterPage />
      </GuestOnly>
    ),
  },
  { path: "/s/:token", element: <Suspense fallback={<LoadingPage />}><PublicSharePage /></Suspense> },
  {
    path: "/styleguide",
    element: (
      <Suspense
        fallback={
          <p className="p-8" role="status">
            Loading styleguide…
          </p>
        }
      >
        <StyleguidePage />
      </Suspense>
    ),
  },
  {
    element: <ProtectedLayout />,
    children: [
      { path: "/", element: <Navigate to="/files/root" replace /> },
      { path: "/files/:folderId", element: <FileManagerPage /> },
      { path: "/recent", element: <FileManagerPage /> },
      { path: "/trash", element: <FileManagerPage /> },
      { path: "/storage", element: <StoragePage /> },
      { path: "/activity", element: <ActivityPage /> },
      { path: "/settings", element: <SettingsPage /> },
      ...["shared"].map((path) => ({
        path,
        element: <WorkspacePage />,
      })),
    ],
  },
  { path: "*", element: <NotFoundPage /> },
]);

export function App() {
  return (
    <ErrorBoundary>
      <ThemeProvider>
        <QueryClientProvider client={queryClient}>
          <AuthProvider>
            <TooltipProvider delayDuration={300}>
              <Suspense fallback={<LoadingPage />}>
                <RouterProvider router={router} />
              </Suspense>
              <Toaster />
            </TooltipProvider>
          </AuthProvider>
        </QueryClientProvider>
      </ThemeProvider>
    </ErrorBoundary>
  );
}

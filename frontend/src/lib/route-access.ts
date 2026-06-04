export type RouteAccess = "admin" | "auth" | "organizer" | "public" | "public-content" | "transition";

export function routeAccessForPath(pathname: string): RouteAccess {
  if (pathname === "/" || pathname === "/login" || pathname === "/register") {
    return "public";
  }

  if (pathname === "/portal-entry") {
    return "transition";
  }

  if (pathname === "/turnirji" || pathname.startsWith("/turnirji/")) {
    return "public-content";
  }

  if (pathname === "/draft-simulation") {
    return "public-content";
  }

  if (pathname === "/organizator" || pathname.startsWith("/organizator/")) {
    return "organizer";
  }

  if (pathname === "/admin" || pathname.startsWith("/admin/")) {
    return "admin";
  }

  return "auth";
}

export function isAdminRole(role?: string | null) {
  return role === "admin";
}

export function isOrganizerRole(role?: string | null) {
  return role === "organizer" || role === "admin";
}

export function isPublicShellRoute(pathname: string) {
  return routeAccessForPath(pathname) === "public";
}

export function safeLocalRedirectPath(value: string | null | undefined, fallback = "/dashboard") {
  const trimmed = value?.trim();

  if (!trimmed || trimmed.startsWith("//")) {
    return fallback;
  }

  try {
    const url = new URL(trimmed, "http://dotaops.local");

    if (url.origin !== "http://dotaops.local") {
      return fallback;
    }

    const target = `${url.pathname}${url.search}${url.hash}`;

    if (
      target === "/login" ||
      target.startsWith("/login?") ||
      target === "/register" ||
      url.pathname === "/portal-entry" ||
      url.pathname.startsWith("/portal-entry/")
    ) {
      return fallback;
    }

    return target.startsWith("/") ? target : fallback;
  } catch {
    return fallback;
  }
}

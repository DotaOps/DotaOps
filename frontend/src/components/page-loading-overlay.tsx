"use client";

import { usePathname } from "next/navigation";

export function PageLoadingOverlay({
  label = "Loading page"
}: {
  label?: string;
}) {
  const pathname = usePathname();

  if (pathname === "/portal-entry") {
    return null;
  }

  return (
    <section
      aria-busy="true"
      aria-label={label}
      className="page-loading-overlay"
      role="status"
    >
      <div aria-hidden="true" className="page-loading-radar">
        <span />
        <span />
        <span />
      </div>
    </section>
  );
}

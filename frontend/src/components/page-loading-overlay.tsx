export function PageLoadingOverlay({
  label = "Loading page"
}: {
  label?: string;
}) {
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

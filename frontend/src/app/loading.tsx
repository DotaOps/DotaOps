export default function Loading() {
  return (
    <section
      aria-busy="true"
      aria-label="Loading page"
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

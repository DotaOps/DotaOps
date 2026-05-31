export function OrganizerWorkspaceLoading() {
  return (
    <section
      aria-busy="true"
      aria-label="Opening organizer workspace"
      className="org-tournament-state ops-panel"
      role="status"
    >
      <p className="ops-label">Organizer tournament access</p>
      <h1>Opening organizer workspace</h1>
      <p>Checking your session and loading tournament records.</p>
    </section>
  );
}

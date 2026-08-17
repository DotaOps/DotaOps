export function AnalyticsEmptyBlock({
  detail,
  title
}: Readonly<{
  detail: string;
  title: string;
}>) {
  return (
    <div className="analytics-empty-block">
      <span className="ops-label">Awaiting analytics data</span>
      <strong>{title}</strong>
      <p>{detail}</p>
    </div>
  );
}

import Link from "next/link";

import { UserAvatar } from "@/components/user-avatar";

interface HeaderProfileLinkProps {
  avatarUrl?: string | null;
  displayName: string;
}

export function HeaderProfileLink({ avatarUrl, displayName }: HeaderProfileLinkProps) {
  return (
    <Link
      aria-label={`User profile: ${displayName}`}
      className="topbar-profile"
      href="/profile"
      title="Profile"
    >
      <UserAvatar avatarUrl={avatarUrl} className="topbar-avatar" size={16} />
      <span className="topbar-profile-label">
        <strong>{displayName}</strong>
      </span>
    </Link>
  );
}

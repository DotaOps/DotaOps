import { UserRound } from "lucide-react";

import { classNames } from "@/lib/utils";

interface UserAvatarProps {
  avatarUrl?: string | null;
  className?: string;
  size?: number;
}

export function UserAvatar({ avatarUrl, className, size = 18 }: UserAvatarProps) {
  return (
    <span aria-hidden="true" className={classNames("user-avatar", className)}>
      {avatarUrl ? (
        <span
          className="user-avatar-image"
          style={{ backgroundImage: `url(${avatarUrl})` }}
        />
      ) : (
        <UserRound size={size} />
      )}
    </span>
  );
}

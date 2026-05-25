"use client";

import { createContext, useContext } from "react";
import type { ReactNode } from "react";

import type { CurrentUserProfile } from "@/lib/auth";

const CurrentUserProfileContext = createContext<CurrentUserProfile | null | undefined>(undefined);

export function CurrentUserProfileProvider({
  children,
  profile
}: {
  children: ReactNode;
  profile: CurrentUserProfile | null;
}) {
  return (
    <CurrentUserProfileContext.Provider value={profile}>
      {children}
    </CurrentUserProfileContext.Provider>
  );
}

export function useCurrentUserProfile() {
  return useContext(CurrentUserProfileContext);
}

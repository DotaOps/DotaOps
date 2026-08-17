import { PublicHomepage } from "@/components/home/public-homepage";
import { getApiAuthenticated } from "@/lib/api";
import { getPublicHomepageData } from "@/lib/homepage-data";
import { getSupabaseServerClient } from "@/lib/supabase/server";
import { cookies } from "next/headers";
import { redirect } from "next/navigation";

interface HomepageProfile {
  avatarUrl?: string | null;
  displayName?: string | null;
  nickname?: string | null;
}

export default async function Home() {
  const supabase = await getSupabaseServerClient();
  const [{ data: claimsData }, { data: sessionData }] = supabase
    ? await Promise.all([supabase.auth.getClaims(), supabase.auth.getSession()])
    : [{ data: null }, { data: null }];
  const cookieStore = await cookies();
  const shouldRememberSession = cookieStore.get("dotaops_remember")?.value === "1";

  if (claimsData?.claims && shouldRememberSession) {
    redirect("/dashboard");
  }

  const authUserId =
    typeof claimsData?.claims?.sub === "string" ? claimsData.claims.sub : null;
  const accessToken = sessionData?.session?.access_token;
  let avatarUrl: string | null = null;
  let displayName = "Profile";

  if (authUserId && accessToken) {
    try {
      const profile = await getApiAuthenticated<HomepageProfile>("/me/profile", accessToken);

      avatarUrl = profile.avatarUrl ?? null;
      displayName = profile.displayName || profile.nickname || displayName;
    } catch {
      // Homepage personalization is optional; the backend remains the only profile data source.
    }
  }

  const homepageData = await getPublicHomepageData();

  return (
    <PublicHomepage
      avatarUrl={avatarUrl}
      displayName={displayName}
      homepageData={homepageData}
      isAuthenticated={Boolean(claimsData?.claims)}
    />
  );
}

import { PublicHomepage } from "@/components/home/public-homepage";
import { getPublicHomepageData } from "@/lib/homepage-data";
import { getSupabaseServerClient } from "@/lib/supabase/server";
import { cookies } from "next/headers";
import { redirect } from "next/navigation";

export default async function Home() {
  const supabase = await getSupabaseServerClient();
  const { data } = supabase
    ? await supabase.auth.getClaims()
    : { data: null };
  const cookieStore = await cookies();
  const shouldRememberSession = cookieStore.get("dotaops_remember")?.value === "1";

  if (data?.claims && shouldRememberSession) {
    redirect("/dashboard");
  }

  const authUserId = typeof data?.claims?.sub === "string" ? data.claims.sub : null;
  let avatarUrl: string | null = null;
  let displayName = "Profile";

  if (supabase && authUserId) {
    const { data: profile } = await supabase
      .from("profiles")
      .select("avatar_url,display_name,nickname")
      .eq("auth_user_id", authUserId)
      .maybeSingle();

    avatarUrl = profile?.avatar_url ?? null;
    displayName = profile?.display_name || profile?.nickname || displayName;
  }

  const homepageData = await getPublicHomepageData();

  return (
    <PublicHomepage
      avatarUrl={avatarUrl}
      displayName={displayName}
      homepageData={homepageData}
      isAuthenticated={Boolean(data?.claims)}
    />
  );
}

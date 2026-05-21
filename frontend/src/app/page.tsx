import { PublicHomepage } from "@/components/home/public-homepage";
import { getSupabaseServerClient } from "@/lib/supabase/server";

export default async function Home() {
  const supabase = await getSupabaseServerClient();
  const { data } = supabase
    ? await supabase.auth.getClaims()
    : { data: null };

  return <PublicHomepage isAuthenticated={Boolean(data?.claims)} />;
}

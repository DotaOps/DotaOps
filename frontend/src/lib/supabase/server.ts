import { createServerClient } from "@supabase/ssr";
import { cookies } from "next/headers";

function getSupabaseConfig() {
  const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const supabaseKey =
    process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY ||
    process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;

  if (!supabaseUrl || !supabaseKey) {
    return null;
  }

  return { supabaseUrl, supabaseKey };
}

async function createServerSupabaseClient({
  supabaseKey,
  supabaseUrl
}: NonNullable<ReturnType<typeof getSupabaseConfig>>) {
  const cookieStore = await cookies();

  return createServerClient(supabaseUrl, supabaseKey, {
    cookies: {
      getAll() {
        return cookieStore.getAll();
      },
      setAll(cookiesToSet) {
        try {
          cookiesToSet.forEach(({ name, value, options }) => {
            cookieStore.set(name, value, options);
          });
        } catch {
          // Server Components cannot write cookies directly.
        }
      }
    }
  });
}

export async function createClient() {
  const config = getSupabaseConfig();

  if (!config) {
    throw new Error("Missing Supabase frontend environment variables.");
  }

  return createServerSupabaseClient(config);
}

export async function getSupabaseServerClient() {
  const config = getSupabaseConfig();

  return config ? createServerSupabaseClient(config) : null;
}

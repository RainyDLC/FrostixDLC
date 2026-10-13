import { createClient, createServiceClient } from "./supabase/server";

export interface AdminUser {
  id: string;
  email: string;
}

/**
 * Строгая проверка админа: сессия + флаг is_admin в profiles,
 * прочитанный СЕРВИСНЫМ ключом (минуя RLS). Флаг ставится только
 * SQL-запросом в Supabase Dashboard — через сайт его получить нельзя.
 */
export async function getAdmin(): Promise<AdminUser | null> {
  try {
    const supabase = createClient();
    const {
      data: { user },
    } = await supabase.auth.getUser();
    if (!user?.id || !user.email) return null;

    const service = createServiceClient();
    const { data: profile } = await service
      .from("profiles")
      .select("is_admin")
      .eq("id", user.id)
      .single();

    if (!profile?.is_admin) return null;
    return { id: user.id, email: user.email };
  } catch {
    return null;
  }
}

export async function requireAdmin(): Promise<AdminUser> {
  const admin = await getAdmin();
  if (!admin) throw new Error("forbidden");
  return admin;
}

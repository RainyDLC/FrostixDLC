import { notFound } from "next/navigation";
import { getAdmin } from "@/lib/admin";
import { createServiceClient } from "@/lib/supabase/server";
import AdminPanel from "./admin-panel";

export interface AdminUserRow {
  id: string;
  email: string;
  created_at: string;
  banned: boolean;
}

export default async function AdminPage() {
  // Не-админ видит обычный 404 — самого факта существования панели не раскрываем
  let admin = null;
  try {
    admin = await getAdmin();
  } catch {
    /* Supabase не настроен */
  }
  if (!admin) notFound();

  const service = createServiceClient();

  const { data: usersData } = await service.auth.admin.listUsers({ perPage: 200 });
  const users: AdminUserRow[] = (usersData?.users ?? []).map((u) => ({
    id: u.id,
    email: u.email ?? "—",
    created_at: u.created_at,
    banned: !!u.banned_until && new Date(u.banned_until) > new Date(),
  }));

  const { data: settings } = await service
    .from("site_settings")
    .select("maintenance, maintenance_message")
    .eq("id", 1)
    .single();

  return (
    <AdminPanel
      me={admin.email}
      users={users}
      initialMaintenance={!!settings?.maintenance}
      initialMessage={settings?.maintenance_message ?? ""}
    />
  );
}

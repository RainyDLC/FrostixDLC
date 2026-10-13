import { redirect } from "next/navigation";
import { createClient } from "@/lib/supabase/server";
import AccountClient from "./account-client";

export default async function AccountPage() {
  let user = null;
  try {
    const supabase = createClient();
    const { data } = await supabase.auth.getUser();
    user = data.user;
  } catch {
    /* Supabase не настроен — отправляем на вход */
  }

  if (!user) redirect("/auth");

  return (
    <AccountClient
      email={user.email ?? ""}
      createdAt={user.created_at}
    />
  );
}

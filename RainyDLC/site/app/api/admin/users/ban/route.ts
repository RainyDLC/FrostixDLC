import { NextResponse } from "next/server";
import { requireAdmin } from "@/lib/admin";
import { createServiceClient } from "@/lib/supabase/server";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export async function POST(req: Request) {
  try {
    const admin = await requireAdmin();
    const { id, banned } = await req.json();

    // Строгая валидация входа: UUID + boolean, ничего лишнего
    if (!UUID.test(String(id)) || typeof banned !== "boolean") {
      return NextResponse.json({ error: "bad_request" }, { status: 400 });
    }
    // Себя банить нельзя
    if (id === admin.id) {
      return NextResponse.json({ error: "forbidden" }, { status: 403 });
    }

    const service = createServiceClient();
    const { error } = await service.auth.admin.updateUserById(id, {
      ban_duration: banned ? "876000h" : "none", // ~100 лет или разбан
    });
    if (error) return NextResponse.json({ error: "failed" }, { status: 500 });

    return NextResponse.json({ ok: true });
  } catch {
    return NextResponse.json({ error: "forbidden" }, { status: 403 });
  }
}

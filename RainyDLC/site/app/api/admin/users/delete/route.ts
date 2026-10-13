import { NextResponse } from "next/server";
import { requireAdmin } from "@/lib/admin";
import { createServiceClient } from "@/lib/supabase/server";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export async function POST(req: Request) {
  try {
    const admin = await requireAdmin();
    const { id } = await req.json();

    if (!UUID.test(String(id))) {
      return NextResponse.json({ error: "bad_request" }, { status: 400 });
    }
    if (id === admin.id) {
      return NextResponse.json({ error: "forbidden" }, { status: 403 });
    }

    const service = createServiceClient();
    const { error } = await service.auth.admin.deleteUser(id);
    if (error) return NextResponse.json({ error: "failed" }, { status: 500 });

    return NextResponse.json({ ok: true });
  } catch {
    return NextResponse.json({ error: "forbidden" }, { status: 403 });
  }
}

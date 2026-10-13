import { NextResponse } from "next/server";
import { requireAdmin } from "@/lib/admin";
import { createServiceClient } from "@/lib/supabase/server";

export async function POST(req: Request) {
  try {
    await requireAdmin();
    const { maintenance, message } = await req.json();

    if (typeof maintenance !== "boolean") {
      return NextResponse.json({ error: "bad_request" }, { status: 400 });
    }
    const cleanMessage = String(message ?? "").slice(0, 200);

    const service = createServiceClient();
    const { error } = await service
      .from("site_settings")
      .upsert({ id: 1, maintenance, maintenance_message: cleanMessage });

    if (error) return NextResponse.json({ error: "failed" }, { status: 500 });
    return NextResponse.json({ ok: true });
  } catch {
    return NextResponse.json({ error: "forbidden" }, { status: 403 });
  }
}

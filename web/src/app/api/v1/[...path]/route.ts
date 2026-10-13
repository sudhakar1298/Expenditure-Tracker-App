import { NextRequest } from "next/server";

export const dynamic = "force-dynamic";

async function proxy(request: NextRequest) {
  const target = new URL(process.env.BACKEND_URL || "http://127.0.0.1:8000");
  target.pathname = request.nextUrl.pathname;
  target.search = request.nextUrl.search;
  try {
    const headers = new Headers();
    for (const name of ["content-type", "authorization"]) {
      const value = request.headers.get(name);
      if (value) headers.set(name, value);
    }
    const upstream = await fetch(target, {
      method: request.method,
      headers,
      body: ["GET", "HEAD"].includes(request.method) ? undefined : await request.text(),
      cache: "no-store",
      signal: AbortSignal.timeout(15000),
    });
    return new Response(upstream.body, {
      status: upstream.status,
      headers: { "Content-Type": upstream.headers.get("content-type") || "application/json" },
    });
  } catch {
    return Response.json({ detail: "Expense backend unavailable" }, { status: 502 });
  }
}

export { proxy as GET, proxy as POST, proxy as PUT, proxy as DELETE };

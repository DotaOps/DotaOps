import { NextResponse } from "next/server";

import { predictDraft } from "@/lib/draft-prediction";

export const runtime = "nodejs";

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

export async function POST(request: Request) {
  let payload: unknown;

  try {
    payload = await request.json();
  } catch {
    return NextResponse.json({ message: "Prediction request must be valid JSON." }, { status: 400 });
  }

  if (!isRecord(payload)) {
    return NextResponse.json({ message: "Prediction request must be an object." }, { status: 400 });
  }

  try {
    return NextResponse.json(
      predictDraft({
        teamAHeroIds: payload.teamAHeroIds as number[],
        teamASide: payload.teamASide as "radiant" | "dire" | "neutral" | undefined,
        teamBHeroIds: payload.teamBHeroIds as number[]
      })
    );
  } catch (error) {
    return NextResponse.json(
      {
        message: error instanceof Error ? error.message : "Draft prediction failed."
      },
      { status: 400 }
    );
  }
}

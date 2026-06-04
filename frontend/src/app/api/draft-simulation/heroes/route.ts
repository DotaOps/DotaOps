import { NextResponse } from "next/server";

import { getDraftModelMetadata, listDraftHeroes } from "@/lib/draft-prediction";

export const runtime = "nodejs";

export function GET() {
  try {
    return NextResponse.json({
      heroes: listDraftHeroes(),
      model: getDraftModelMetadata()
    });
  } catch (error) {
    return NextResponse.json(
      {
        message: error instanceof Error ? error.message : "Draft heroes are unavailable."
      },
      { status: 500 }
    );
  }
}

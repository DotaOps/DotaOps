import { ApiRequestError } from "@/lib/api";

export function analyticsErrorMessage(error: unknown) {
  if (error instanceof ApiRequestError) {
    if (error.status === 401) {
      return "Login session expired. Please log in again to view analytics.";
    }

    if (error.status === 403) {
      return "You do not have permission to view this analytics workspace.";
    }

    return error.message;
  }

  return error instanceof Error ? error.message : "Analytics unavailable.";
}

import { Link } from "react-router-dom";
import { ShieldAlert } from "lucide-react";
import { Button } from "@/components/ui/button";

/**
 * Shown when an authenticated user lacks the permission/feature for a route — instead of the old
 * silent redirect to home. The sidebar already hides items the user can't access; this covers direct
 * navigation, a bookmarked URL, or a permission that was revoked mid-session.
 */
export function AccessDenied() {
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center gap-4 px-6 text-center">
      <div className="flex h-14 w-14 items-center justify-center rounded-full bg-muted">
        <ShieldAlert className="h-7 w-7 text-muted-foreground" />
      </div>
      <div className="space-y-1">
        <h1 className="text-xl font-semibold">You don't have access to this page</h1>
        <p className="max-w-md text-sm text-muted-foreground">
          Your account doesn't have permission to view this. If you think this is a mistake, ask your
          administrator to update your access.
        </p>
      </div>
      <Button asChild variant="outline">
        <Link to="/">Back to home</Link>
      </Button>
    </div>
  );
}

export default AccessDenied;

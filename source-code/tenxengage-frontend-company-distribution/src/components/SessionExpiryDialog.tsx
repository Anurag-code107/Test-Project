import {
  AlertDialog,
  AlertDialogContent,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogAction,
  AlertDialogCancel,
} from "@/components/ui/alert-dialog";
import { Clock } from "lucide-react";

interface SessionExpiryDialogProps {
  open: boolean;
  remainingSeconds: number;
  onStayActive: () => void;
  onLogout: () => void;
}

export function SessionExpiryDialog({
  open,
  remainingSeconds,
  onStayActive,
  onLogout,
}: SessionExpiryDialogProps) {
  const minutes = Math.floor(remainingSeconds / 60);
  const seconds = remainingSeconds % 60;
  const timeDisplay =
    minutes > 0
      ? `${minutes}:${seconds.toString().padStart(2, "0")}`
      : `${seconds}s`;

  return (
    <AlertDialog open={open}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <div className="flex items-center gap-3">
            <div className="flex items-center justify-center h-10 w-10 rounded-full bg-warning/10">
              <Clock className="h-5 w-5 text-warning" />
            </div>
            <AlertDialogTitle>Session Expiring</AlertDialogTitle>
          </div>
          <AlertDialogDescription className="pt-2">
            Your session will expire in{" "}
            <span className="font-semibold text-foreground">{timeDisplay}</span>{" "}
            due to inactivity. Would you like to stay logged in?
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel onClick={onLogout}>Log Out</AlertDialogCancel>
          <AlertDialogAction onClick={onStayActive}>
            Stay Logged In
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}

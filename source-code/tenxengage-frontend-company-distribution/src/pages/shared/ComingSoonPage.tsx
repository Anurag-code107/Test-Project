import { Construction } from "lucide-react";

function ComingSoonPage() {
  return (
    <div className="flex flex-col items-center justify-center py-32 text-center">
      <div className="bg-muted/50 rounded-full p-6 mb-6">
        <Construction className="h-12 w-12 text-muted-foreground" />
      </div>
      <h1 className="text-3xl font-bold text-foreground mb-3">Coming Soon</h1>
      <p className="text-muted-foreground max-w-md">
        This feature is currently under development and will be available
        shortly. Stay tuned!
      </p>
    </div>
  );
}

export default ComingSoonPage;

import { useState, useEffect } from "react";
import { useBuilder } from "@/contexts/BuilderContext";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Switch } from "@/components/ui/switch";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Plus, Trash2, Mail, ShieldCheck } from "lucide-react";
import { cn } from "@/lib/utils";

const approverCategories = [
  "Budget Approver",
  "Terms & Conditions Approver",
  "Compliance Approver",
  "Legal Approver",
  "Overall Incentive Approver",
  "Finance Approver",
  "Marketing Approver",
];

const categoryColors: Record<string, string> = {
  "Budget Approver": "bg-emerald-500/10 text-emerald-600 border-emerald-500/30",
  "Terms & Conditions Approver":
    "bg-blue-500/10 text-blue-600 border-blue-500/30",
  "Compliance Approver":
    "bg-purple-500/10 text-purple-600 border-purple-500/30",
  "Legal Approver": "bg-amber-500/10 text-amber-600 border-amber-500/30",
  "Overall Incentive Approver": "bg-primary/10 text-primary border-primary/30",
  "Finance Approver": "bg-rose-500/10 text-rose-600 border-rose-500/30",
  "Marketing Approver": "bg-cyan-500/10 text-cyan-600 border-cyan-500/30",
};

export function Step6Approval() {
  const { state, dispatch } = useBuilder();
  const { approval } = state;
  const [newEmail, setNewEmail] = useState("");
  const [newCategory, setNewCategory] = useState("");

  // Auto-mark step complete/incomplete
  useEffect(() => {
    const isComplete =
      !approval.requiresApproval ||
      (approval.approvers.length >= 1 && approval.requiredApprovals >= 1);

    if (isComplete && !state.completedSteps.includes("approval")) {
      dispatch({ type: "MARK_STEP_COMPLETE", payload: "approval" });
    } else if (!isComplete && state.completedSteps.includes("approval")) {
      dispatch({ type: "MARK_STEP_INCOMPLETE", payload: "approval" });
    }
  }, [
    approval.requiresApproval,
    approval.approvers.length,
    approval.requiredApprovals,
    state.completedSteps,
    dispatch,
  ]);

  function handleToggle(checked: boolean) {
    dispatch({
      type: "UPDATE_APPROVAL",
      payload: { requiresApproval: checked },
    });
  }

  function handleAddApprover() {
    if (!newEmail.trim() || !newCategory) return;
    const newApprover = {
      id: crypto.randomUUID(),
      email: newEmail.trim(),
      category: newCategory,
    };
    const updatedApprovers = [...approval.approvers, newApprover];
    dispatch({
      type: "UPDATE_APPROVAL",
      payload: {
        approvers: updatedApprovers,
        requiredApprovals: Math.max(approval.requiredApprovals, 1),
      },
    });
    setNewEmail("");
    setNewCategory("");
  }

  function handleRemoveApprover(id: string) {
    const updatedApprovers = approval.approvers.filter((a) => a.id !== id);
    dispatch({
      type: "UPDATE_APPROVAL",
      payload: {
        approvers: updatedApprovers,
        requiredApprovals: Math.min(
          approval.requiredApprovals,
          updatedApprovers.length,
        ),
      },
    });
  }

  function handleRequiredChange(val: string) {
    dispatch({
      type: "UPDATE_APPROVAL",
      payload: { requiredApprovals: Number(val) },
    });
  }

  return (
    <div className="space-y-5">
      {/* Toggle */}
      <div className="flex items-center justify-between">
        <div className="space-y-0.5">
          <Label className="text-sm font-medium">
            Require Approval Before Activation
          </Label>
          <p className="text-xs text-muted-foreground">
            When enabled, this incentive will need approval from designated
            approvers before it can go live.
          </p>
        </div>
        <Switch
          checked={approval.requiresApproval}
          onCheckedChange={handleToggle}
        />
      </div>

      {approval.requiresApproval && (
        <>
          {/* Add Approver Form */}
          <div className="space-y-3 rounded-lg border border-border p-4 bg-muted/20">
            <Label className="text-xs font-semibold text-muted-foreground uppercase tracking-wide">
              Add Approver
            </Label>
            <div className="flex gap-2">
              <div className="flex-1">
                <Input
                  placeholder="approver@company.com"
                  type="email"
                  value={newEmail}
                  onChange={(e) => setNewEmail(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") {
                      e.preventDefault();
                      handleAddApprover();
                    }
                  }}
                />
              </div>
              <Select value={newCategory} onValueChange={setNewCategory}>
                <SelectTrigger className="w-[220px]">
                  <SelectValue placeholder="Approver category" />
                </SelectTrigger>
                <SelectContent>
                  {approverCategories.map((cat) => (
                    <SelectItem key={cat} value={cat}>
                      {cat}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Button
                size="icon"
                onClick={handleAddApprover}
                disabled={!newEmail.trim() || !newCategory}
              >
                <Plus className="h-4 w-4" />
              </Button>
            </div>
          </div>

          {/* Approver List */}
          {approval.approvers.length > 0 && (
            <div className="space-y-2">
              <Label className="text-xs font-semibold text-muted-foreground uppercase tracking-wide">
                Approvers ({approval.approvers.length})
              </Label>
              <div className="space-y-2">
                {approval.approvers.map((approver) => (
                  <div
                    key={approver.id}
                    className="flex items-center gap-3 rounded-lg border bg-card p-3"
                  >
                    <div className="h-8 w-8 rounded-full bg-primary/10 flex items-center justify-center shrink-0">
                      <Mail className="h-4 w-4 text-primary" />
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium truncate">
                        {approver.email}
                      </p>
                    </div>
                    <Badge
                      variant="outline"
                      className={cn(
                        "text-xs shrink-0",
                        categoryColors[approver.category] ||
                          "bg-muted text-muted-foreground",
                      )}
                    >
                      {approver.category}
                    </Badge>
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-7 w-7 shrink-0 text-muted-foreground hover:text-destructive"
                      onClick={() => handleRemoveApprover(approver.id)}
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </Button>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Required Approvals Threshold */}
          {approval.approvers.length > 0 && (
            <div className="space-y-2 rounded-lg border border-border p-4 bg-muted/20">
              <div className="flex items-center gap-2">
                <ShieldCheck className="h-4 w-4 text-primary" />
                <Label className="text-sm font-medium">
                  Required Approvals
                </Label>
              </div>
              <p className="text-xs text-muted-foreground">
                How many approvers need to approve for the incentive to be
                activated?
              </p>
              <Select
                value={String(approval.requiredApprovals)}
                onValueChange={handleRequiredChange}
              >
                <SelectTrigger className="w-[180px]">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {Array.from(
                    { length: approval.approvers.length },
                    (_, i) => i + 1,
                  ).map((n) => (
                    <SelectItem key={n} value={String(n)}>
                      {n} of {approval.approvers.length} approver
                      {approval.approvers.length > 1 ? "s" : ""}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          )}
        </>
      )}
    </div>
  );
}

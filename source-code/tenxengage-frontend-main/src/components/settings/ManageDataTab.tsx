import { useState } from "react";
import {
  Card,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from "@/components/ui/dialog";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import {
  Plus,
  Database,
  Loader2,
  Trash2,
  ChevronRight,
  Link2,
  FileSpreadsheet,
  Upload,
  Shield,
} from "lucide-react";
import { toast } from "sonner";
import { Textarea } from "@/components/ui/textarea";
import {
  useDataObjects,
  useCreateDataObject,
  useDeleteDataObject,
} from "@/hooks/useDataObjectApi";
import { FeatureGate } from "@/components/FeatureGate";
import { DataObjectDetail } from "./DataObjectDetail";
import type { ExpandedSection } from "./DataOperationsPanel";

export function ManageDataTab() {
  const { data: dataObjects, isLoading } = useDataObjects();
  const createMutation = useCreateDataObject();
  const deleteMutation = useDeleteDataObject();

  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [expandedSection, setExpandedSection] = useState<ExpandedSection>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [deleteId, setDeleteId] = useState<string | null>(null);
  const [newName, setNewName] = useState("");
  const [newDescription, setNewDescription] = useState("");

  // Show detail view if a data object is selected
  if (selectedId) {
    return (
      <DataObjectDetail
        dataObjectId={selectedId}
        onBack={() => setSelectedId(null)}
        expandedSection={expandedSection}
        onExpandedChange={setExpandedSection}
      />
    );
  }

  function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    if (!newName.trim()) {
      toast.error("Name is required");
      return;
    }
    createMutation.mutate(
      { name: newName.trim(), description: newDescription.trim() || undefined },
      {
        onSuccess: () => {
          toast.success(`Created data object "${newName}"`);
          setCreateOpen(false);
          setNewName("");
          setNewDescription("");
        },
        onError: () => toast.error("Failed to create data object"),
      },
    );
  }

  function handleDelete() {
    if (!deleteId) return;
    const obj = dataObjects?.find((d) => d.id === deleteId);
    deleteMutation.mutate(deleteId, {
      onSuccess: () => {
        toast.success(`Deleted "${obj?.name}"`);
        setDeleteId(null);
      },
      onError: () => toast.error("Cannot delete default data objects"),
    });
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-12">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  const deleteTarget = dataObjects?.find((d) => d.id === deleteId);

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h3 className="text-lg font-semibold text-foreground">
            Data Objects
          </h3>
          <p className="text-sm text-muted-foreground">
            Define and manage the data structures used across the platform
          </p>
        </div>
        <Button onClick={() => setCreateOpen(true)} className="gap-2">
          <Plus className="h-4 w-4" />
          New Data Object
        </Button>
      </div>

      {dataObjects && dataObjects.length > 0 ? (
        <div className="grid gap-3">
          {dataObjects.map((obj) => {
            const isSales = obj.name === "Sales Data";
            return (
              <Card
                key={obj.id}
                className="cursor-pointer hover:shadow-md transition-shadow border"
                onClick={() => {
                  setSelectedId(obj.id);
                  setExpandedSection(null);
                }}
              >
                <CardHeader className="py-4 px-5">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10">
                        <Database className="h-5 w-5 text-primary" />
                      </div>
                      <div>
                        <div className="flex items-center gap-2">
                          <CardTitle className="text-base text-foreground">
                            {obj.name}
                          </CardTitle>
                          {obj.isDefault && (
                            <Badge variant="secondary" className="text-xs">
                              Default
                            </Badge>
                          )}
                          {obj.connectorName && (
                            <Badge variant="outline" className="text-xs gap-1">
                              <Link2 className="h-2.5 w-2.5" />
                              {obj.connectorName}
                            </Badge>
                          )}
                        </div>
                        <CardDescription className="text-xs mt-0.5">
                          {obj.fieldCount} field
                          {obj.fieldCount !== 1 ? "s" : ""}
                        </CardDescription>
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      <div
                        className="flex items-center gap-1.5"
                        onClick={(e) => e.stopPropagation()}
                      >
                        <FeatureGate feature="bulk_import">
                          <TooltipProvider>
                            <Tooltip>
                              <TooltipTrigger asChild>
                                <Button
                                  variant="outline"
                                  size="sm"
                                  className="gap-1.5 text-xs h-7"
                                  onClick={() => {
                                    setSelectedId(obj.id);
                                    setExpandedSection("operations");
                                  }}
                                >
                                  <Upload className="h-3 w-3" />
                                  Manual Upload
                                </Button>
                              </TooltipTrigger>
                              <TooltipContent
                                side="bottom"
                                className="text-xs max-w-[220px]"
                              >
                                Upload data files manually or pull from a
                                connected data source
                              </TooltipContent>
                            </Tooltip>
                          </TooltipProvider>
                        </FeatureGate>
                        {isSales && (
                          <TooltipProvider>
                            <Tooltip>
                              <TooltipTrigger asChild>
                                <Button
                                  variant="outline"
                                  size="sm"
                                  className="gap-1.5 text-xs h-7 border-primary/30 text-primary hover:bg-primary/10 hover:text-primary"
                                  onClick={() => {
                                    setSelectedId(obj.id);
                                    setExpandedSection("tagging");
                                  }}
                                >
                                  <Shield className="h-3 w-3" />
                                  Tag Deals
                                </Button>
                              </TooltipTrigger>
                              <TooltipContent
                                side="bottom"
                                className="text-xs max-w-[220px]"
                              >
                                Run a job to analyze sales POs and tag which
                                deals are eligible for incentive rewards
                              </TooltipContent>
                            </Tooltip>
                          </TooltipProvider>
                        )}
                      </div>
                      {!obj.isDefault && (
                        <Button
                          variant="ghost"
                          size="icon"
                          className="h-8 w-8 text-destructive hover:text-destructive"
                          onClick={(e) => {
                            e.stopPropagation();
                            setDeleteId(obj.id);
                          }}
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      )}
                      <ChevronRight className="h-4 w-4 text-muted-foreground" />
                    </div>
                  </div>
                </CardHeader>
              </Card>
            );
          })}
        </div>
      ) : (
        <Card className="border-dashed">
          <CardHeader className="text-center py-12">
            <div className="flex justify-center mb-3">
              <div className="p-3 rounded-full bg-muted">
                <FileSpreadsheet className="h-8 w-8 text-muted-foreground" />
              </div>
            </div>
            <CardTitle className="text-lg text-foreground">
              No Data Objects
            </CardTitle>
            <CardDescription>
              Create a data object to define the structure of your data
            </CardDescription>
          </CardHeader>
        </Card>
      )}

      {/* Create Dialog */}
      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Create Data Object</DialogTitle>
            <DialogDescription>
              Define a new data structure for the platform
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={handleCreate} className="space-y-4 py-2">
            <div className="space-y-2">
              <Label htmlFor="obj-name">Name</Label>
              <Input
                id="obj-name"
                value={newName}
                onChange={(e) => setNewName(e.target.value)}
                placeholder="e.g. Sales Data, Partner Data"
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="obj-desc">Description</Label>
              <Textarea
                id="obj-desc"
                value={newDescription}
                onChange={(e) => setNewDescription(e.target.value)}
                placeholder="Describe the purpose of this data object..."
                rows={3}
              />
            </div>
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => setCreateOpen(false)}
              >
                Cancel
              </Button>
              <Button
                type="submit"
                disabled={createMutation.isPending || !newName.trim()}
              >
                {createMutation.isPending && (
                  <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                )}
                Create
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* Delete Confirm */}
      <AlertDialog
        open={!!deleteId}
        onOpenChange={(open) => !open && setDeleteId(null)}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete Data Object</AlertDialogTitle>
            <AlertDialogDescription>
              Are you sure you want to delete "{deleteTarget?.name}"? All fields
              and mappings will be removed.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              onClick={handleDelete}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            >
              {deleteMutation.isPending && (
                <Loader2 className="h-4 w-4 mr-2 animate-spin" />
              )}
              Delete
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}

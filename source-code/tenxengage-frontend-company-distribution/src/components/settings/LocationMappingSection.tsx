import { useState } from "react";
import {
  Plus,
  Trash2,
  ChevronRight,
  ChevronDown,
  MapPin,
  Globe,
  Loader2,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useToast } from "@/hooks/use-toast";
import {
  useLocationHierarchy,
  useCreateLocationLevel,
  useDeleteLocationLevel,
  useCreateLocationValue,
  useDeleteLocationValue,
  useUpdateLocationLevelSettings,
} from "@/hooks/useLocationApi";
import { Switch } from "@/components/ui/switch";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import type {
  LocationValueResponse,
  LocationLevelResponse,
} from "@/types/location.types";

function LocationTreeNode({
  node,
  depth = 0,
  levels,
  onAddChild,
  onDelete,
}: {
  node: LocationValueResponse;
  depth?: number;
  levels: LocationLevelResponse[];
  onAddChild: (parentId: string, levelId: string) => void;
  onDelete: (id: string, name: string) => void;
}) {
  const [expanded, setExpanded] = useState(true);
  const hasChildren = node.children.length > 0;

  const currentLevelIndex = levels.findIndex((l) => l.id === node.levelId);
  const nextLevel =
    currentLevelIndex >= 0 && currentLevelIndex + 1 < levels.length
      ? levels[currentLevelIndex + 1]
      : null;

  return (
    <div>
      <div
        className="flex items-center gap-2 py-2 px-3 rounded-md hover:bg-primary/5 group transition-colors"
        style={{ paddingLeft: `${depth * 24 + 12}px` }}
      >
        <button
          className="flex items-center"
          onClick={() => setExpanded(!expanded)}
          disabled={!hasChildren}
        >
          {hasChildren ? (
            expanded ? (
              <ChevronDown className="h-3.5 w-3.5 text-muted-foreground" />
            ) : (
              <ChevronRight className="h-3.5 w-3.5 text-muted-foreground" />
            )
          ) : (
            <span className="w-3.5" />
          )}
        </button>
        {depth === 0 ? (
          <Globe className="h-3.5 w-3.5 text-primary" />
        ) : (
          <MapPin className="h-3.5 w-3.5 text-muted-foreground" />
        )}
        <span className="flex-1 min-w-0 text-sm font-medium text-foreground">
          {node.name}
          {node.code && node.code !== node.name && (
            <span className="text-xs text-muted-foreground ml-1">
              ({node.code})
            </span>
          )}
        </span>
        <span className="text-xs text-muted-foreground shrink-0 w-16 text-right">
          {node.levelName}
        </span>
        {hasChildren && (
          <span className="text-xs text-muted-foreground tabular-nums shrink-0 w-6 text-right">
            {node.children.length}
          </span>
        )}
        <div className="flex items-center gap-0.5 opacity-0 group-hover:opacity-100 transition-opacity shrink-0">
          {nextLevel && (
            <button
              className="w-6 h-6 flex items-center justify-center rounded text-muted-foreground hover:text-primary hover:bg-primary/10"
              title={`Add ${nextLevel.name}`}
              onClick={(e) => {
                e.stopPropagation();
                onAddChild(node.id, nextLevel.id);
              }}
            >
              <Plus className="h-3 w-3" />
            </button>
          )}
          <AlertDialog>
            <AlertDialogTrigger asChild>
              <button
                className="w-6 h-6 flex items-center justify-center rounded text-muted-foreground hover:text-destructive hover:bg-destructive/10"
                onClick={(e) => e.stopPropagation()}
              >
                <Trash2 className="h-3 w-3" />
              </button>
            </AlertDialogTrigger>
            <AlertDialogContent>
              <AlertDialogHeader>
                <AlertDialogTitle>
                  Delete &ldquo;{node.name}&rdquo;?
                </AlertDialogTitle>
                <AlertDialogDescription>
                  This will permanently delete this location
                  {hasChildren ? " and all its children" : ""}. This action
                  cannot be undone.
                </AlertDialogDescription>
              </AlertDialogHeader>
              <AlertDialogFooter>
                <AlertDialogCancel>Cancel</AlertDialogCancel>
                <AlertDialogAction
                  className="bg-red-600 text-white hover:bg-red-700"
                  onClick={() => onDelete(node.id, node.name)}
                >
                  Delete
                </AlertDialogAction>
              </AlertDialogFooter>
            </AlertDialogContent>
          </AlertDialog>
        </div>
      </div>
      {expanded && hasChildren && (
        <div>
          {node.children.map((child) => (
            <LocationTreeNode
              key={child.id}
              node={child}
              depth={depth + 1}
              levels={levels}
              onAddChild={onAddChild}
              onDelete={onDelete}
            />
          ))}
        </div>
      )}
    </div>
  );
}

export function LocationMappingSection() {
  const { toast } = useToast();
  const { data, isLoading, isError } = useLocationHierarchy();
  const createLevel = useCreateLocationLevel();
  const deleteLevel = useDeleteLocationLevel();
  const createValue = useCreateLocationValue();
  const deleteValue = useDeleteLocationValue();
  const updateSettings = useUpdateLocationLevelSettings();

  const [newLevelName, setNewLevelName] = useState("");

  const [addValueDialog, setAddValueDialog] = useState<{
    open: boolean;
    levelId: string;
    parentId: string | null;
    levelName: string;
  }>({ open: false, levelId: "", parentId: null, levelName: "" });
  const [newValueName, setNewValueName] = useState("");
  const [newValueCode, setNewValueCode] = useState("");

  const levels = data?.levels ?? [];
  const tree = data?.tree ?? [];

  const handleAddLevel = () => {
    const name = newLevelName.trim();
    if (!name) return;
    createLevel.mutate(
      { name },
      {
        onSuccess: () => {
          setNewLevelName("");
          toast({
            title: "Level created",
            description: `"${name}" has been added.`,
          });
        },
        onError: () => {
          toast({
            title: "Error",
            description: "Failed to create level",
            variant: "destructive",
          });
        },
      },
    );
  };

  const handleDeleteLevel = (id: string, name: string) => {
    deleteLevel.mutate(id, {
      onSuccess: () => {
        toast({
          title: "Level deleted",
          description: `"${name}" has been removed.`,
        });
      },
      onError: () => {
        toast({
          title: "Error",
          description: "Failed to delete level",
          variant: "destructive",
        });
      },
    });
  };

  const handleOpenAddValue = (parentId: string | null, levelId: string) => {
    const level = levels.find((l) => l.id === levelId);
    setAddValueDialog({
      open: true,
      levelId,
      parentId,
      levelName: level?.name ?? "Value",
    });
    setNewValueName("");
    setNewValueCode("");
  };

  const handleAddValue = () => {
    const name = newValueName.trim();
    if (!name) return;
    createValue.mutate(
      {
        levelId: addValueDialog.levelId,
        parentId: addValueDialog.parentId,
        name,
        code: newValueCode.trim() || null,
      },
      {
        onSuccess: () => {
          setAddValueDialog((prev) => ({ ...prev, open: false }));
          toast({
            title: "Location added",
            description: `"${name}" has been added.`,
          });
        },
        onError: () => {
          toast({
            title: "Error",
            description: "Failed to create value",
            variant: "destructive",
          });
        },
      },
    );
  };

  const handleDeleteValue = (id: string, name: string) => {
    deleteValue.mutate(id, {
      onSuccess: () => {
        toast({
          title: "Location deleted",
          description: `"${name}" has been removed.`,
        });
      },
      onError: () => {
        toast({
          title: "Error",
          description: "Failed to delete value",
          variant: "destructive",
        });
      },
    });
  };

  const rootLevel = levels.length > 0 ? levels[0] : null;

  if (isLoading) {
    return (
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        <div className="rounded-xl border border-border p-4 space-y-3">
          <div className="h-4 w-32 rounded bg-muted animate-pulse" />
          <div className="space-y-1.5">
            {[1, 2].map((i) => (
              <div
                key={i}
                className="h-9 rounded bg-muted animate-pulse"
              />
            ))}
          </div>
        </div>
        <div className="lg:col-span-2 rounded-xl border border-border p-4 space-y-3">
          <div className="h-4 w-40 rounded bg-muted animate-pulse" />
          {[1, 2, 3, 4].map((i) => (
            <div
              key={i}
              className="h-9 rounded bg-muted animate-pulse"
            />
          ))}
        </div>
      </div>
    );
  }

  if (isError) {
    return (
      <div className="rounded-xl border border-border p-8 text-center text-muted-foreground text-sm">
        Failed to load location hierarchy. Please try again later.
      </div>
    );
  }

  return (
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
      {/* Hierarchy Levels */}
      <div className="rounded-xl border border-border p-4 space-y-3">
        <div>
          <span className="text-xs font-semibold text-muted-foreground tracking-[0.06em] uppercase">
            Hierarchy Levels
          </span>
          <p className="text-xs text-muted-foreground mt-0.5">
            Define the types of location groupings.
          </p>
        </div>
        <div className="space-y-1.5">
          {levels.map((level, idx) => {
            const hasNoValues = level.valueCount === 0;
            const isLastLevel = idx === levels.length - 1;
            const nextLevelName = !isLastLevel ? levels[idx + 1]?.name : null;
            return (
            <div
              key={level.id}
              className="flex items-center justify-between py-1.5 px-2.5 rounded-md bg-muted/30"
            >
              <div className="flex items-center gap-2 min-w-0">
                <span className="text-xs font-mono text-muted-foreground w-4 tabular-nums shrink-0">
                  {idx + 1}
                </span>
                <span className="text-sm font-medium text-foreground truncate">
                  {level.name}
                </span>
                {level.valueCount > 0 && (
                  <span className="text-xs text-muted-foreground tabular-nums shrink-0">
                    ({level.valueCount})
                  </span>
                )}
              </div>
              <div className="flex items-center gap-2 shrink-0">
                <TooltipProvider>
                  <Tooltip>
                    <TooltipTrigger asChild>
                      <div className="flex items-center gap-1">
                        <span className="text-[10px] text-muted-foreground font-medium">
                          B
                        </span>
                        <Switch
                          className="scale-75"
                          checked={level.useInBuilder}
                          disabled={hasNoValues}
                          onCheckedChange={(checked) =>
                            updateSettings.mutate({
                              id: level.id,
                              data: { useInBuilder: checked },
                            })
                          }
                        />
                      </div>
                    </TooltipTrigger>
                    <TooltipContent side="top" className="text-xs">
                      {hasNoValues
                        ? `Add a ${level.name.toLowerCase()} to enable this`
                        : "Show in incentive builder eligibility section"}
                    </TooltipContent>
                  </Tooltip>
                </TooltipProvider>
                <TooltipProvider>
                  <Tooltip>
                    <TooltipTrigger asChild>
                      <div className="flex items-center gap-1">
                        <span className="text-[10px] text-muted-foreground font-medium">
                          F
                        </span>
                        <Switch
                          className="scale-75"
                          checked={level.useInFilters}
                          disabled={hasNoValues}
                          onCheckedChange={(checked) =>
                            updateSettings.mutate({
                              id: level.id,
                              data: { useInFilters: checked },
                            })
                          }
                        />
                      </div>
                    </TooltipTrigger>
                    <TooltipContent side="top" className="text-xs">
                      {hasNoValues
                        ? `Add a ${level.name.toLowerCase()} to enable this`
                        : "Show in admin page filters"}
                    </TooltipContent>
                  </Tooltip>
                </TooltipProvider>
                <TooltipProvider>
                  <Tooltip>
                    <TooltipTrigger asChild>
                      <div className="flex items-center gap-1">
                        <span className="text-[10px] text-muted-foreground font-medium">
                          R
                        </span>
                        <Switch
                          className="scale-75"
                          checked={level.isRequired}
                          disabled={idx === 0 || hasNoValues}
                          onCheckedChange={(checked) =>
                            updateSettings.mutate({
                              id: level.id,
                              data: { isRequired: checked },
                            })
                          }
                        />
                      </div>
                    </TooltipTrigger>
                    <TooltipContent side="top" className="text-xs">
                      {idx === 0
                        ? "Top level is always required"
                        : hasNoValues
                          ? `Add a ${level.name.toLowerCase()} to enable this`
                          : "Require a value at this level in incentive builder Participant Eligibility"}
                    </TooltipContent>
                  </Tooltip>
                </TooltipProvider>
                {isLastLevel ? (
                  <AlertDialog>
                    <AlertDialogTrigger asChild>
                      <button className="w-6 h-6 flex items-center justify-center rounded text-muted-foreground hover:text-destructive hover:bg-destructive/10">
                        <Trash2 className="h-3 w-3" />
                      </button>
                    </AlertDialogTrigger>
                    <AlertDialogContent>
                      <AlertDialogHeader>
                        <AlertDialogTitle>
                          Delete &ldquo;{level.name}&rdquo; level?
                        </AlertDialogTitle>
                        <AlertDialogDescription>
                          This will permanently delete this level and all its
                          location values. This action cannot be undone.
                        </AlertDialogDescription>
                      </AlertDialogHeader>
                      <AlertDialogFooter>
                        <AlertDialogCancel>Cancel</AlertDialogCancel>
                        <AlertDialogAction
                          className="bg-red-600 text-white hover:bg-red-700"
                          onClick={() => handleDeleteLevel(level.id, level.name)}
                        >
                          Delete
                        </AlertDialogAction>
                      </AlertDialogFooter>
                    </AlertDialogContent>
                  </AlertDialog>
                ) : (
                  <TooltipProvider>
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <button
                          disabled
                          className="w-6 h-6 flex items-center justify-center rounded text-muted-foreground/40 cursor-not-allowed"
                        >
                          <Trash2 className="h-3 w-3" />
                        </button>
                      </TooltipTrigger>
                      <TooltipContent side="top" className="text-xs">
                        Delete the &ldquo;{nextLevelName}&rdquo; level first
                      </TooltipContent>
                    </Tooltip>
                  </TooltipProvider>
                )}
              </div>
            </div>
            );
          })}
        </div>
        <div className="flex items-center gap-3 text-[10px] text-muted-foreground px-1">
          <span>
            <strong>B</strong> = Show in Builder
          </span>
          <span>
            <strong>F</strong> = Show in Filters
          </span>
          <span>
            <strong>R</strong> = Required
          </span>
        </div>
        <div className="flex gap-2 pt-2 border-t border-border">
          <Input
            placeholder="New level name..."
            value={newLevelName}
            onChange={(e) => setNewLevelName(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && handleAddLevel()}
            className="text-sm h-8 border-border"
          />
          <Button
            size="sm"
            variant="outline"
            className="h-8 w-8 p-0 border-border"
            onClick={handleAddLevel}
            disabled={!newLevelName.trim() || createLevel.isPending}
          >
            {createLevel.isPending ? (
              <Loader2 className="h-3.5 w-3.5 animate-spin" />
            ) : (
              <Plus className="h-3.5 w-3.5" />
            )}
          </Button>
        </div>
      </div>

      {/* Location Tree */}
      <div className="lg:col-span-2 rounded-xl border border-border p-4 space-y-3">
        <div className="flex items-center justify-between">
          <div>
            <span className="text-xs font-semibold text-muted-foreground tracking-[0.06em] uppercase">
              Location Hierarchy
            </span>
            <p className="text-xs text-muted-foreground mt-0.5">
              View and manage your location structure.
            </p>
          </div>
          {rootLevel && (
            <Button
              size="sm"
              className="h-7 text-xs gap-1 bg-primary hover:bg-primary/90"
              onClick={() => handleOpenAddValue(null, rootLevel.id)}
            >
              <Plus className="h-3 w-3" />
              Add {rootLevel.name}
            </Button>
          )}
        </div>
        {tree.length === 0 ? (
          <div className="rounded-lg border border-border p-8 text-center text-muted-foreground text-sm">
            No locations defined yet.{" "}
            {rootLevel
              ? `Click "Add ${rootLevel.name}" to get started.`
              : "Create a hierarchy level first."}
          </div>
        ) : (
          <div className="rounded-lg border border-border divide-y divide-border">
            {tree.map((node) => (
              <LocationTreeNode
                key={node.id}
                node={node}
                levels={levels}
                onAddChild={handleOpenAddValue}
                onDelete={handleDeleteValue}
              />
            ))}
          </div>
        )}
      </div>

      {/* Add Value Dialog */}
      <Dialog
        open={addValueDialog.open}
        onOpenChange={(open) =>
          setAddValueDialog((prev) => ({ ...prev, open }))
        }
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Add {addValueDialog.levelName}</DialogTitle>
            <DialogDescription>
              Enter the details for the new{" "}
              {addValueDialog.levelName.toLowerCase()}.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <div className="space-y-2">
              <Label htmlFor="value-name">Name</Label>
              <Input
                id="value-name"
                placeholder={`${addValueDialog.levelName} name...`}
                value={newValueName}
                onChange={(e) => setNewValueName(e.target.value)}
                onKeyDown={(e) => e.key === "Enter" && handleAddValue()}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="value-code">Code (optional)</Label>
              <Input
                id="value-code"
                placeholder="Short code..."
                value={newValueCode}
                onChange={(e) => setNewValueCode(e.target.value)}
                onKeyDown={(e) => e.key === "Enter" && handleAddValue()}
              />
            </div>
          </div>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() =>
                setAddValueDialog((prev) => ({ ...prev, open: false }))
              }
            >
              Cancel
            </Button>
            <Button
              onClick={handleAddValue}
              disabled={!newValueName.trim() || createValue.isPending}
            >
              {createValue.isPending && (
                <Loader2 className="h-4 w-4 animate-spin mr-2" />
              )}
              Add {addValueDialog.levelName}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

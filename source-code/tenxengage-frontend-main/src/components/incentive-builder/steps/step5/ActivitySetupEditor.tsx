import { useState } from "react";
import { useBuilder } from "@/contexts/BuilderContext";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import {
  Plus,
  Trash2,
  GripVertical,
  X,
  FileText,
  CheckCircle2,
  Info,
  Search,
  FileCheck,
} from "lucide-react";
import type {
  ActivityDefinition,
  ActivityDocumentRequirement,
} from "@/types/incentive.types";
import { useActivityCategories } from "@/hooks/useBuilderConfig";

interface DocField {
  name: string;
  description: string;
}

export function ActivitySetupEditor() {
  const { state, dispatch } = useBuilder();
  const activities = state.criteria.activityDefinitions;

  // Fetch activity categories from the database
  const { data: dbCategories = [] } = useActivityCategories();
  const activityTypeCategories = dbCategories.map((cat) => ({
    id: cat.name,
    name: cat.name.replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase()),
    description: cat.description ?? "",
  }));

  // Form state
  const [showAddForm, setShowAddForm] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [newCategory, setNewCategory] = useState("");
  const [newName, setNewName] = useState("");
  const [newDescription, setNewDescription] = useState("");
  const [newDocuments, setNewDocuments] = useState<DocField[]>([
    { name: "", description: "" },
  ]);

  // Filter state
  const [categoryFilter, setCategoryFilter] = useState("all");
  const [search, setSearch] = useState("");

  function setActivities(
    updater: (prev: ActivityDefinition[]) => ActivityDefinition[],
  ) {
    dispatch({
      type: "UPDATE_CRITERIA",
      payload: { activityDefinitions: updater(activities) },
    });
  }

  function resetForm() {
    setNewCategory("");
    setNewName("");
    setNewDescription("");
    setNewDocuments([{ name: "", description: "" }]);
    setShowAddForm(false);
    setEditingId(null);
  }

  function handleAddActivity() {
    const docs: ActivityDocumentRequirement[] = newDocuments
      .filter((d) => d.name.trim() !== "")
      .map((d) => ({
        name: d.name.trim(),
        description: d.description.trim() || undefined,
        required: true,
      }));
    if (!newName.trim() || !newCategory || docs.length === 0) return;

    if (editingId) {
      setActivities((prev) =>
        prev.map((a) =>
          a.id === editingId
            ? {
                ...a,
                name: newName.trim(),
                description: newDescription.trim(),
                categoryId: newCategory,
                requiredDocuments: docs,
              }
            : a,
        ),
      );
    } else {
      const newActivity: ActivityDefinition = {
        id: `act-def-${Date.now()}`,
        name: newName.trim(),
        description: newDescription.trim(),
        categoryId: newCategory,
        sortOrder: activities.length + 1,
        requiredDocuments: docs,
      };
      setActivities((prev) => [...prev, newActivity]);
    }
    resetForm();
  }

  function handleEditActivity(activity: ActivityDefinition) {
    setEditingId(activity.id ?? null);
    setNewCategory(activity.categoryId);
    setNewName(activity.name);
    setNewDescription(activity.description ?? "");
    setNewDocuments(
      activity.requiredDocuments.length > 0
        ? activity.requiredDocuments.map((d) => ({
            name: d.name,
            description: d.description ?? "",
          }))
        : [{ name: "", description: "" }],
    );
    setShowAddForm(true);
  }

  function handleRemoveActivity(id: string) {
    setActivities((prev) => {
      const updated = prev.filter((a) => a.id !== id);
      return updated.map((a, i) => ({ ...a, sortOrder: i + 1 }));
    });
  }

  function getCategoryName(id: string) {
    return activityTypeCategories.find((c) => c.id === id)?.name ?? id;
  }

  const filteredActivities = activities.filter((a) => {
    const matchesCategory =
      categoryFilter === "all" || a.categoryId === categoryFilter;
    const matchesSearch =
      !search ||
      a.name.toLowerCase().includes(search.toLowerCase()) ||
      (a.description ?? "").toLowerCase().includes(search.toLowerCase());
    return matchesCategory && matchesSearch;
  });

  const usedCategories = [...new Set(activities.map((a) => a.categoryId))];
  const isFormValid =
    newName.trim() && newCategory && newDocuments.some((d) => d.name.trim());

  return (
    <div className="space-y-4">
      {/* Activities Summary */}
      {activities.length > 0 && (
        <Card className="border-primary/30 bg-primary/5">
          <CardContent className="py-4 space-y-3">
            <Label className="text-sm font-semibold flex items-center gap-2">
              <CheckCircle2 className="h-4 w-4 text-primary" />
              Defined Activities ({activities.length})
            </Label>

            {/* Filter bar */}
            {activities.length > 2 && (
              <div className="flex gap-2">
                <div className="relative flex-1">
                  <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
                  <Input
                    placeholder="Search activities..."
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                    className="pl-8 h-8 text-sm"
                  />
                </div>
                <Select
                  value={categoryFilter}
                  onValueChange={setCategoryFilter}
                >
                  <SelectTrigger className="w-[170px] h-8 text-sm">
                    <SelectValue placeholder="All Categories" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">All Categories</SelectItem>
                    {usedCategories.map((catId) => (
                      <SelectItem key={catId} value={catId}>
                        {getCategoryName(catId)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            )}

            {/* Activity cards */}
            <div className="space-y-2">
              {filteredActivities.map((activity) => (
                <div
                  key={activity.id}
                  className="flex items-start gap-2 rounded-lg border bg-card p-3 group"
                >
                  <div className="flex items-center gap-1 pt-0.5 shrink-0 text-muted-foreground">
                    <GripVertical className="h-3.5 w-3.5" />
                    <span className="text-xs font-semibold w-4">
                      {activity.sortOrder}
                    </span>
                  </div>
                  <div className="flex-1 min-w-0 space-y-1">
                    <div className="flex items-start justify-between gap-2">
                      <p className="text-sm font-medium text-foreground">
                        {activity.name}
                      </p>
                      <Badge
                        variant="outline"
                        className="text-xs shrink-0 px-1.5 py-0"
                      >
                        {getCategoryName(activity.categoryId)}
                      </Badge>
                    </div>
                    {activity.description && (
                      <p className="text-xs text-muted-foreground line-clamp-2">
                        {activity.description}
                      </p>
                    )}
                    <div className="flex flex-wrap gap-1 pt-1">
                      {activity.requiredDocuments.map((doc, i) => (
                        <TooltipProvider key={i}>
                          <Tooltip>
                            <TooltipTrigger asChild>
                              <Badge
                                variant="secondary"
                                className="text-xs gap-1 py-0 cursor-help"
                              >
                                <FileText className="h-2.5 w-2.5" />
                                {doc.name}
                              </Badge>
                            </TooltipTrigger>
                            {doc.description && (
                              <TooltipContent
                                side="top"
                                className="max-w-[200px]"
                              >
                                <p className="text-xs">{doc.description}</p>
                              </TooltipContent>
                            )}
                          </Tooltip>
                        </TooltipProvider>
                      ))}
                    </div>
                  </div>
                  <div className="flex gap-1 shrink-0 opacity-0 group-hover:opacity-100 transition-opacity">
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-7 w-7"
                      onClick={() => handleEditActivity(activity)}
                    >
                      <FileCheck className="h-3.5 w-3.5" />
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-7 w-7 text-destructive hover:text-destructive"
                      onClick={() => handleRemoveActivity(activity.id ?? "")}
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </Button>
                  </div>
                </div>
              ))}
              {filteredActivities.length === 0 && activities.length > 0 && (
                <p className="text-xs text-muted-foreground text-center py-4">
                  No activities match your filter.
                </p>
              )}
            </div>
          </CardContent>
        </Card>
      )}

      {/* Add Activity Form */}
      {showAddForm ? (
        <Card className="border-dashed border border-primary/30">
          <CardContent className="py-4 space-y-4">
            <div className="flex items-center justify-between">
              <Label className="text-sm font-semibold">
                {editingId ? "Edit Activity" : "New Activity"}
              </Label>
              <Button
                variant="ghost"
                size="icon"
                className="h-7 w-7"
                onClick={resetForm}
              >
                <X className="h-4 w-4" />
              </Button>
            </div>

            {/* Category */}
            <div className="space-y-2">
              <Label className="text-sm">Activity Category *</Label>
              <Select value={newCategory} onValueChange={setNewCategory}>
                <SelectTrigger className="h-9">
                  <SelectValue placeholder="Select a category..." />
                </SelectTrigger>
                <SelectContent>
                  {activityTypeCategories.map((cat) => (
                    <SelectItem key={cat.id} value={cat.id}>
                      {cat.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {newCategory && (
                <div className="text-xs text-muted-foreground px-1">
                  <span className="font-medium">Examples: </span>
                  {(
                    activityTypeCategories.find((c) => c.id === newCategory) as
                      | {
                          id: string;
                          name: string;
                          description: string;
                          examples?: string[];
                        }
                      | undefined
                  )?.examples?.join(", ") ?? ""}
                </div>
              )}
            </div>

            {/* Name */}
            <div className="space-y-2">
              <Label className="text-sm">Activity Name *</Label>
              <Input
                placeholder="e.g., Customer Interview Recording"
                value={newName}
                onChange={(e) => setNewName(e.target.value)}
                className="h-9"
              />
            </div>

            {/* Description */}
            <div className="space-y-2">
              <Label className="text-sm">Description</Label>
              <Textarea
                placeholder="Describe what the partner needs to do for this activity..."
                value={newDescription}
                onChange={(e) => setNewDescription(e.target.value)}
                rows={2}
                className="text-sm"
              />
            </div>

            {/* Required Documents */}
            <div className="space-y-2">
              <div className="flex items-center gap-2">
                <Label className="text-sm">Required Documents *</Label>
                <TooltipProvider>
                  <Tooltip>
                    <TooltipTrigger asChild>
                      <Info className="h-3.5 w-3.5 text-muted-foreground cursor-help" />
                    </TooltipTrigger>
                    <TooltipContent side="top" className="max-w-[250px]">
                      <p className="text-xs">
                        Name each document the partner must upload to complete
                        this activity. Partners will see these as required
                        uploads.
                      </p>
                    </TooltipContent>
                  </Tooltip>
                </TooltipProvider>
              </div>
              <div className="space-y-2">
                {newDocuments.map((doc, index) => (
                  <div
                    key={index}
                    className="space-y-1.5 rounded-md border border-border/50 p-2.5 bg-muted/20"
                  >
                    <div className="flex gap-2">
                      <div className="relative flex-1">
                        <FileText className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
                        <Input
                          placeholder={
                            index === 0
                              ? "e.g., Interview Recording"
                              : index === 1
                                ? "e.g., Signed Consent Form"
                                : "e.g., Supporting Documentation"
                          }
                          value={doc.name}
                          onChange={(e) =>
                            setNewDocuments((prev) =>
                              prev.map((d, i) =>
                                i === index
                                  ? { ...d, name: e.target.value }
                                  : d,
                              ),
                            )
                          }
                          className="h-8 pl-8 text-sm"
                        />
                      </div>
                      {newDocuments.length > 1 && (
                        <Button
                          variant="ghost"
                          size="icon"
                          className="h-8 w-8 shrink-0 text-muted-foreground hover:text-destructive"
                          onClick={() =>
                            setNewDocuments((prev) =>
                              prev.filter((_, i) => i !== index),
                            )
                          }
                        >
                          <X className="h-3.5 w-3.5" />
                        </Button>
                      )}
                    </div>
                    <Input
                      placeholder="Description (optional) — e.g., Must include customer consent and be at least 10 minutes"
                      value={doc.description}
                      onChange={(e) =>
                        setNewDocuments((prev) =>
                          prev.map((d, i) =>
                            i === index
                              ? { ...d, description: e.target.value }
                              : d,
                          ),
                        )
                      }
                      className="h-7 text-xs text-muted-foreground"
                    />
                  </div>
                ))}
                <Button
                  variant="outline"
                  size="sm"
                  className="h-7 text-xs gap-1"
                  onClick={() =>
                    setNewDocuments((prev) => [
                      ...prev,
                      { name: "", description: "" },
                    ])
                  }
                >
                  <Plus className="h-3 w-3" />
                  Add Document
                </Button>
              </div>
            </div>

            <Button
              className="w-full"
              size="sm"
              onClick={handleAddActivity}
              disabled={!isFormValid}
            >
              {editingId ? "Update Activity" : "Add Activity"}
            </Button>
          </CardContent>
        </Card>
      ) : (
        <Button
          variant="outline"
          className="w-full border-dashed gap-2"
          onClick={() => setShowAddForm(true)}
        >
          <Plus className="h-4 w-4" />
          Add Activity
        </Button>
      )}

      {/* Category templates (only when empty and no form) */}
      {!showAddForm && activities.length === 0 && (
        <div className="space-y-3">
          <Label className="text-xs text-muted-foreground uppercase tracking-wider">
            Or start from a category template
          </Label>
          <div className="grid grid-cols-2 gap-2">
            {activityTypeCategories.map((cat) => (
              <button
                key={cat.id}
                type="button"
                className="text-left rounded-lg border border-border bg-card p-3 hover:border-primary/40 hover:bg-primary/5 transition-[border-color,background-color] space-y-1"
                onClick={() => {
                  setNewCategory(cat.id);
                  setShowAddForm(true);
                }}
              >
                <p className="text-sm font-medium text-foreground">
                  {cat.name}
                </p>
                <p className="text-xs text-muted-foreground line-clamp-1">
                  {cat.description}
                </p>
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

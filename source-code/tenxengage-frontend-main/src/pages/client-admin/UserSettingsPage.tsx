import { useState, useEffect, useMemo, useRef, useCallback } from "react";
import {
  Users,
  Shield,
  UserCog,
  Plus,
  User,
  Building2,
  ChevronRight,
  ChevronLeft,
  Search,
  Save,
  Lock,
  Trash2,
  Eye,
  Pencil,
  Loader2,
  ChevronsUpDown,
  Check,
  Copy,
} from "lucide-react";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Alert, AlertDescription } from "@/components/ui/alert";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { FlipTransition } from "@/components/FlipTransition";
import { useToast } from "@/hooks/use-toast";
import { PageBanner } from "@/components/PageBanner";
import {
  usePermissionCatalog,
  useClientRoles,
  useCreateClientRole,
  useCloneClientRole,
  useUpdateClientRole,
  useUpdateRolePermissions,
  useDeleteClientRole,
  useCompanyOverrides,
  useUpdateCompanyOverrides,
  useUserOverrides,
  useUpdateUserOverrides,
} from "@/hooks/usePermissionApi";
import {
  useHomeDashboardTemplates,
  useAssignHomeDashboardTemplate,
  useClearHomeDashboardTemplate,
} from "@/hooks/useHomeDashboardTemplateApi";
import { usePermissions } from "@/hooks/usePermissions";
import { useAuth } from "@/hooks/useAuth";
import type {
  PermissionDef as PermissionDefType,
  ClientRole,
} from "@/types/permission.types";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import {
  Command,
  CommandInput,
  CommandList,
  CommandEmpty,
  CommandGroup,
  CommandItem,
} from "@/components/ui/command";
import { useQuery } from "@tanstack/react-query";
import { getUsers } from "@/services/user.service";
import { cn } from "@/lib/utils";
import { toast } from "sonner";
import {
  useUsers,
  useCreateUser,
  useUpdateUser,
  useDeleteUser,
} from "@/hooks/useApi";
import {
  usePartnerCompanies,
  useCreatePartnerCompany,
  useUpdatePartnerCompany,
  useDeletePartnerCompany,
} from "@/hooks/usePartnerCompanyApi";
import { useLocationHierarchy } from "@/hooks/useLocationApi";
import type {
  LocationValueResponse,
  LocationHierarchyResponse,
} from "@/types/location.types";
import { useDataObjectByName } from "@/hooks/useDataObjectApi";
import { DynamicFieldRenderer } from "@/components/DynamicFieldRenderer";
import type { DynamicField } from "@/components/DynamicFieldRenderer";
import type {
  User as ApiUser,
  CreateUserRequest,
  UpdateUserRequest,
  UserStatus as ApiUserStatusType,
} from "@/types/user.types";
import type {
  PartnerCompany as ApiPartnerCompany,
  PartnerCompanyLocationAssignment,
  CreatePartnerCompanyRequest,
  UpdatePartnerCompanyRequest,
} from "@/types/partner-company.types";
import type { DataObjectFieldResponse } from "@/types/data-object.types";

// ─── Types (Users tab) ────────────────────────────────────────────────────────

type ApiUserStatus = "ACTIVE" | "INACTIVE" | "SUSPENDED" | "PENDING";

// ─── Helpers ─────────────────────────────────────────────────────────────────

function formatDate(dateString: string) {
  return new Date(dateString).toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

function getStatusBadgeClass(status: ApiUserStatus | string) {
  switch (status) {
    case "ACTIVE":
      return "bg-emerald-500/10 text-emerald-600 border-emerald-500/20";
    case "INACTIVE":
      return "bg-amber-500/10 text-amber-600 border-amber-500/20";
    case "SUSPENDED":
      return "bg-red-500/10 text-red-600 border-red-500/20";
    case "PENDING":
      return "bg-blue-500/10 text-blue-600 border-blue-500/20";
    default:
      return "bg-muted text-muted-foreground border-border";
  }
}

function getCompanyStatusBadgeClass(status: string) {
  switch (status) {
    case "ACTIVE":
      return "bg-emerald-500/10 text-emerald-600 border-emerald-500/20";
    case "INACTIVE":
      return "bg-amber-500/10 text-amber-600 border-amber-500/20";
    default:
      return "bg-muted text-muted-foreground border-border";
  }
}

function flattenLocationTree(
  nodes: LocationValueResponse[],
): Map<string, LocationValueResponse> {
  const out = new Map<string, LocationValueResponse>();
  const walk = (list: LocationValueResponse[]) => {
    for (const node of list) {
      out.set(node.id, node);
      if (node.children?.length) walk(node.children);
    }
  };
  walk(nodes);
  return out;
}

function getTopLevelLocationNames(
  assignments: PartnerCompanyLocationAssignment[] | undefined,
  hierarchy: LocationHierarchyResponse | undefined,
): string[] {
  if (!assignments?.length || !hierarchy) return [];
  const flat = flattenLocationTree(hierarchy.tree);
  const names = new Set<string>();
  for (const assignment of assignments) {
    let current = flat.get(assignment.locationValueId);
    while (current?.parentId) {
      const parent = flat.get(current.parentId);
      if (!parent) break;
      current = parent;
    }
    if (current) names.add(current.name);
  }
  return Array.from(names);
}

function getBaseRoleBadgeClass(baseRoleName: string | null) {
  switch (baseRoleName) {
    case "CLIENT_ADMIN":
      return "bg-primary/10 text-primary border-primary/20";
    case "ACTIVITY_APPROVER":
      return "bg-amber-500/10 text-amber-600 border-amber-500/20";
    case "PARTNER_ADMIN":
      return "bg-blue-500/10 text-blue-600 border-blue-500/20";
    case "PARTNER_SELLER":
      return "bg-emerald-500/10 text-emerald-600 border-emerald-500/20";
    default:
      return "bg-muted text-muted-foreground border-border";
  }
}

function formatBaseRoleName(baseRoleName: string | null) {
  switch (baseRoleName) {
    case "CLIENT_ADMIN":
      return "Client Admin";
    case "ACTIVITY_APPROVER":
      return "Activity Approver";
    case "PARTNER_ADMIN":
      return "Partner Admin";
    case "PARTNER_SELLER":
      return "Partner Seller";
    default:
      return baseRoleName;
  }
}

function formatCategoryLabel(category: string) {
  return category.replace(/_/g, " ");
}

function getRoleTypeBadge(roleType: string) {
  if (roleType === "EXTERNAL") {
    return {
      label: "External",
      className: "bg-blue-500/10 text-blue-600 border-blue-500/20",
    };
  }
  return {
    label: "Internal",
    className: "bg-slate-500/10 text-slate-600 border-slate-500/20",
  };
}

// ─── Role Edit Sheet ─────────────────────────────────────────────────────────

interface RoleEditSheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  role: ClientRole | null;
  catalog: PermissionDefType[];
  readOnly: boolean;
}

function RoleEditSheet({
  open,
  onOpenChange,
  role,
  catalog,
  readOnly,
}: RoleEditSheetProps) {
  const { toast } = useToast();
  const { can } = usePermissions();
  const { user, refreshUser } = useAuth();
  const canAssignTemplate = can("action.roles.assign_dashboard_template");
  const updateRole = useUpdateClientRole();
  const updatePerms = useUpdateRolePermissions();
  const assignTemplate = useAssignHomeDashboardTemplate();
  const clearTemplate = useClearHomeDashboardTemplate();
  const [description, setDescription] = useState("");
  const [localPerms, setLocalPerms] = useState<Record<string, boolean>>({});
  const [searchQuery, setSearchQuery] = useState("");
  const [templateId, setTemplateId] = useState<string>("");
  const scrollRef = useRef<HTMLDivElement>(null);

  const roleType = role?.roleType as "INTERNAL" | "EXTERNAL" | undefined;
  const { data: templates = [] } = useHomeDashboardTemplates(roleType);

  useEffect(() => {
    if (role) {
      setDescription(role.description);
      setLocalPerms({ ...role.permissions });
      setSearchQuery("");
      setTemplateId(role.homeDashboardTemplateId ?? "");
    }
  }, [role]);

  const applicablePermissions = useMemo(() => {
    if (!role) return [];
    return catalog.filter(
      (p) => p.scope === "ALL" || p.scope === role.roleType,
    );
  }, [catalog, role]);

  const filteredPermissions = useMemo(() => {
    if (!searchQuery) return applicablePermissions;
    const q = searchQuery.toLowerCase();
    return applicablePermissions.filter(
      (p) =>
        p.displayName.toLowerCase().includes(q) ||
        p.description.toLowerCase().includes(q),
    );
  }, [applicablePermissions, searchQuery]);

  const categories = useMemo(() => {
    const cats = new Map<string, PermissionDefType[]>();
    filteredPermissions
      .sort((a, b) => a.sortOrder - b.sortOrder)
      .forEach((p) => {
        if (!cats.has(p.category)) cats.set(p.category, []);
        cats.get(p.category)!.push(p);
      });
    return cats;
  }, [filteredPermissions]);

  const handleSave = async () => {
    if (!role) return;

    try {
      // Save description if changed and role is not system
      if (!role.isSystem && description !== role.description) {
        await updateRole.mutateAsync({
          id: role.id,
          data: { description },
        });
      }

      // Save permissions (if editable)
      if (!readOnly) {
        await updatePerms.mutateAsync({
          id: role.id,
          data: { permissions: localPerms },
        });
      }

      // Save home dashboard template assignment if changed and the user can
      const originalTemplateId = role.homeDashboardTemplateId ?? "";
      const templateChanged =
        canAssignTemplate && templateId !== originalTemplateId;
      if (templateChanged) {
        if (templateId) {
          await assignTemplate.mutateAsync({
            roleId: role.id,
            templateId,
          });
        } else {
          await clearTemplate.mutateAsync(role.id);
        }
      }

      // If the template change affected the logged-in user's own role, refresh
      // their session so /home re-renders with the new widgets immediately.
      if (templateChanged && user?.clientRoleId === role.id) {
        await refreshUser();
      }

      toast({
        title: "Role Updated",
        description: `Changes to "${role.name}" have been saved.`,
      });
      onOpenChange(false);
    } catch {
      toast({
        title: "Error",
        description: "Failed to update role.",
        variant: "destructive",
      });
    }
  };

  const togglePermission = (key: string) => {
    setLocalPerms((prev) => ({ ...prev, [key]: !prev[key] }));
  };

  const permCount = applicablePermissions.length;
  const enabledCount = applicablePermissions.filter(
    (p) => localPerms[p.permissionKey],
  ).length;

  if (!role) return null;

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="sm:max-w-2xl flex flex-col h-full">
        <SheetHeader className="pb-4 shrink-0">
          <SheetTitle className="flex items-center gap-3">
            <div className="h-10 w-10 rounded-full bg-primary/10 flex items-center justify-center">
              <UserCog className="h-5 w-5 text-primary" />
            </div>
            <div>
              <div className="text-lg font-semibold">
                {readOnly ? "View Role" : "Edit Role"}
              </div>
              <div className="text-sm font-normal text-muted-foreground">
                {formatBaseRoleName(role.baseRoleName)}
                {role.isSystem ? " (System)" : " (Custom)"}
              </div>
            </div>
          </SheetTitle>
          <SheetDescription className="sr-only">
            {readOnly ? "View" : "Edit"} permissions for {role.name}
          </SheetDescription>
        </SheetHeader>

        <div className="flex-1 min-h-0 flex flex-col space-y-4">
          {/* Role info */}
          <div className="shrink-0 rounded-xl border border-border p-4 space-y-3">
            <div className="flex items-start justify-between gap-4">
              <div className="flex-1 min-w-0">
                <h3 className="text-xl font-semibold text-foreground">
                  {role.name}
                </h3>
                <div className="flex items-center gap-2 mt-2">
                  <Badge
                    variant="outline"
                    className={getBaseRoleBadgeClass(role.baseRoleName)}
                  >
                    {formatBaseRoleName(role.baseRoleName)}
                  </Badge>
                  {role.isSystem && (
                    <Badge
                      variant="outline"
                      className="bg-muted text-muted-foreground border-border"
                    >
                      System
                    </Badge>
                  )}
                  <span className="text-xs text-muted-foreground">
                    {enabledCount}/{permCount} enabled
                  </span>
                </div>
              </div>
              <div className="relative w-[220px] shrink-0">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                <Input
                  placeholder="Search permissions..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="pl-10 h-9"
                />
              </div>
            </div>

            {/* Description (editable for custom roles) */}
            {!role.isSystem && !readOnly ? (
              <div className="space-y-1">
                <Label
                  htmlFor="role-desc"
                  className="text-xs text-muted-foreground"
                >
                  Description
                </Label>
                <Input
                  id="role-desc"
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  className="h-9"
                  placeholder="Role description..."
                />
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">
                {role.description}
              </p>
            )}

            {/* Home Dashboard Template picker (gated by permission; locked when sheet is readOnly) */}
            <div className="space-y-1">
              <Label
                htmlFor="role-home-dashboard-template"
                className="text-xs text-muted-foreground"
              >
                Home Dashboard Template
              </Label>
              <Select
                value={templateId || "__unassigned__"}
                onValueChange={(v) =>
                  setTemplateId(v === "__unassigned__" ? "" : v)
                }
                disabled={readOnly || !canAssignTemplate}
              >
                <SelectTrigger
                  id="role-home-dashboard-template"
                  className="h-9"
                  title={
                    readOnly
                      ? "This role is locked. Template assignment is read-only."
                      : canAssignTemplate
                        ? undefined
                        : "You do not have permission to assign dashboard templates."
                  }
                >
                  <SelectValue placeholder="Default for role type" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="__unassigned__">
                    <span className="text-muted-foreground">
                      Default for role type
                    </span>
                  </SelectItem>
                  {templates.map((t) => (
                    <SelectItem key={t.id} value={t.id}>
                      {t.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {templateId &&
                (() => {
                  const selected = templates.find((t) => t.id === templateId);
                  if (!selected) return null;
                  const totalSlots = selected.layout.rows.reduce(
                    (sum, r) => sum + r.slots.length,
                    0,
                  );
                  return (
                    <p className="text-xs text-muted-foreground">
                      {selected.layout.rows.length}{" "}
                      {selected.layout.rows.length === 1 ? "row" : "rows"} ·{" "}
                      {totalSlots} {totalSlots === 1 ? "widget" : "widgets"} ·{" "}
                      {selected.roleType.toLowerCase()}
                    </p>
                  );
                })()}
            </div>
          </div>

          {readOnly && (
            <Alert className="border-blue-500/30 bg-blue-500/5 shrink-0">
              <Lock className="h-4 w-4 text-blue-600" />
              <AlertDescription className="text-blue-700 dark:text-blue-400">
                This is a protected system role. Permissions are read-only.
              </AlertDescription>
            </Alert>
          )}

          {/* Permissions grid */}
          <div className="relative flex-1 min-h-0">
            <div
              ref={scrollRef}
              className="absolute inset-0 overflow-y-auto space-y-4 pr-1"
            >
              {Array.from(categories.entries()).map(([category, perms]) => (
                <div
                  key={category}
                  className="rounded-xl border border-border p-4 space-y-3"
                >
                  <h5 className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                    {formatCategoryLabel(category)}
                  </h5>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    {perms.map((perm) => (
                      <div
                        key={perm.permissionKey}
                        className="flex items-center justify-between gap-3 p-3 rounded-md border border-border hover:bg-muted/50 transition-colors"
                      >
                        <div className="flex-1 min-w-0">
                          <div className="text-sm font-medium text-foreground leading-tight">
                            {perm.displayName}
                          </div>
                          <div className="text-xs text-muted-foreground mt-0.5 leading-snug">
                            {perm.description}
                          </div>
                        </div>
                        <Switch
                          checked={!!localPerms[perm.permissionKey]}
                          onCheckedChange={() =>
                            togglePermission(perm.permissionKey)
                          }
                          disabled={readOnly}
                        />
                      </div>
                    ))}
                  </div>
                </div>
              ))}

              {filteredPermissions.length === 0 && (
                <p className="text-sm text-muted-foreground text-center py-4">
                  No permissions match your search.
                </p>
              )}
            </div>
          </div>

          {/* Action buttons */}
          <div className="flex gap-3 pt-4 border-t border-border shrink-0">
            <Button
              variant="outline"
              className="flex-1"
              onClick={() => onOpenChange(false)}
            >
              {readOnly ? "Close" : "Cancel"}
            </Button>
            {!readOnly && (
              <Button
                className="flex-1 gap-2"
                onClick={handleSave}
                disabled={
                  updatePerms.isPending ||
                  updateRole.isPending ||
                  assignTemplate.isPending ||
                  clearTemplate.isPending
                }
              >
                {updatePerms.isPending ||
                updateRole.isPending ||
                assignTemplate.isPending ||
                clearTemplate.isPending ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <Save className="h-4 w-4" />
                )}
                Save Changes
              </Button>
            )}
          </div>
        </div>
      </SheetContent>
    </Sheet>
  );
}

// ─── Create Role Sheet ──────────────────────────────────────────────────────

interface CreateRoleSheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  catalog: PermissionDefType[];
  existingRoles: ClientRole[];
}

function CreateRoleSheet({
  open,
  onOpenChange,
  catalog,
  existingRoles,
}: CreateRoleSheetProps) {
  const { toast } = useToast();
  const { can } = usePermissions();
  const canAssignTemplate = can("action.roles.assign_dashboard_template");
  const createRole = useCreateClientRole();
  const cloneRole = useCloneClientRole();
  const updatePerms = useUpdateRolePermissions();
  const assignTemplate = useAssignHomeDashboardTemplate();
  const scrollRef = useRef<HTMLDivElement>(null);

  const [mode, setMode] = useState<"scratch" | "clone">("scratch");
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [roleType, setRoleType] = useState<"INTERNAL" | "EXTERNAL">("EXTERNAL");
  const [cloneSourceId, setCloneSourceId] = useState("");
  const [cloneComboboxOpen, setCloneComboboxOpen] = useState(false);
  const [localPerms, setLocalPerms] = useState<Record<string, boolean>>({});
  const [searchQuery, setSearchQuery] = useState("");
  const [templateId, setTemplateId] = useState<string>("");

  const { data: templates = [] } = useHomeDashboardTemplates(roleType);

  // Reset all state when sheet opens
  useEffect(() => {
    if (open) {
      setMode("scratch");
      setName("");
      setDescription("");
      setRoleType("EXTERNAL");
      setCloneSourceId("");
      setLocalPerms({});
      setSearchQuery("");
      setTemplateId("");
    }
  }, [open]);

  // Clear the template selection if the role type changes so we don't carry
  // a mismatched (e.g. INTERNAL) template into an EXTERNAL role.
  useEffect(() => {
    setTemplateId("");
  }, [roleType]);

  // When clone source changes, populate permissions from source role
  const cloneSourceRole = useMemo(
    () => existingRoles.find((r) => r.id === cloneSourceId) ?? null,
    [existingRoles, cloneSourceId],
  );

  useEffect(() => {
    if (cloneSourceRole) {
      setLocalPerms({ ...cloneSourceRole.permissions });
      setRoleType(cloneSourceRole.roleType as "INTERNAL" | "EXTERNAL");
    }
  }, [cloneSourceRole]);

  // Prune out-of-scope permissions when roleType changes
  useEffect(() => {
    const applicableKeys = new Set(
      catalog
        .filter((p) => p.scope === "ALL" || p.scope === roleType)
        .map((p) => p.permissionKey),
    );
    setLocalPerms((prev) => {
      const pruned: Record<string, boolean> = {};
      for (const [key, val] of Object.entries(prev)) {
        if (applicableKeys.has(key)) pruned[key] = val;
      }
      return pruned;
    });
  }, [roleType, catalog]);

  const applicablePermissions = useMemo(() => {
    return catalog.filter((p) => p.scope === "ALL" || p.scope === roleType);
  }, [catalog, roleType]);

  const filteredPermissions = useMemo(() => {
    if (!searchQuery) return applicablePermissions;
    const q = searchQuery.toLowerCase();
    return applicablePermissions.filter(
      (p) =>
        p.displayName.toLowerCase().includes(q) ||
        p.description.toLowerCase().includes(q),
    );
  }, [applicablePermissions, searchQuery]);

  const categories = useMemo(() => {
    const cats = new Map<string, PermissionDefType[]>();
    filteredPermissions
      .sort((a, b) => a.sortOrder - b.sortOrder)
      .forEach((p) => {
        if (!cats.has(p.category)) cats.set(p.category, []);
        cats.get(p.category)!.push(p);
      });
    return cats;
  }, [filteredPermissions]);

  const permCount = applicablePermissions.length;
  const enabledCount = applicablePermissions.filter(
    (p) => localPerms[p.permissionKey],
  ).length;

  // Detect whether user modified permissions from clone source
  const permsModifiedFromSource = useMemo(() => {
    if (!cloneSourceRole) return false;
    const sourcePerms = cloneSourceRole.permissions;
    for (const p of applicablePermissions) {
      const srcVal = !!sourcePerms[p.permissionKey];
      const localVal = !!localPerms[p.permissionKey];
      if (srcVal !== localVal) return true;
    }
    return false;
  }, [cloneSourceRole, localPerms, applicablePermissions]);

  const togglePermission = (key: string) => {
    setLocalPerms((prev) => ({ ...prev, [key]: !prev[key] }));
  };

  const handleModeChange = (newMode: "scratch" | "clone") => {
    setMode(newMode);
    if (newMode === "scratch") {
      setCloneSourceId("");
      setLocalPerms({});
      setRoleType("EXTERNAL");
    } else {
      setLocalPerms({});
    }
  };

  const isPending =
    createRole.isPending ||
    cloneRole.isPending ||
    updatePerms.isPending ||
    assignTemplate.isPending;

  const canSubmit =
    name.trim().length > 0 &&
    (mode === "scratch" || cloneSourceId !== "") &&
    !isPending;

  const handleCreate = async () => {
    if (!canSubmit) return;

    try {
      let createdId: string;
      if (mode === "clone" && cloneSourceId) {
        const cloned = await cloneRole.mutateAsync({
          id: cloneSourceId,
          data: {
            name: name.trim(),
            description: description.trim() || undefined,
          },
        });
        createdId = cloned.id;
        if (permsModifiedFromSource) {
          await updatePerms.mutateAsync({
            id: cloned.id,
            data: { permissions: localPerms },
          });
        }
      } else {
        const created = await createRole.mutateAsync({
          name: name.trim(),
          description: description.trim() || undefined,
          roleType,
          permissions: localPerms,
        });
        createdId = created.id;
      }

      if (canAssignTemplate && templateId) {
        await assignTemplate.mutateAsync({
          roleId: createdId,
          templateId,
        });
      }

      toast({
        title: "Role Created",
        description: `"${name}" has been created successfully.`,
      });
      onOpenChange(false);
    } catch {
      toast({
        title: "Error",
        description: "Failed to create role.",
        variant: "destructive",
      });
    }
  };

  // Group existing roles by type for the clone combobox
  const internalRoles = existingRoles.filter((r) => r.roleType === "INTERNAL");
  const externalRoles = existingRoles.filter((r) => r.roleType === "EXTERNAL");

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="sm:max-w-2xl flex flex-col h-full">
        <SheetHeader className="pb-4 shrink-0">
          <SheetTitle className="flex items-center gap-3">
            <div className="h-10 w-10 rounded-full bg-primary/10 flex items-center justify-center">
              <Plus className="h-5 w-5 text-primary" />
            </div>
            <div>
              <div className="text-lg font-semibold">Create Custom Role</div>
              <div className="text-sm font-normal text-muted-foreground">
                Define a new role with specific permissions
              </div>
            </div>
          </SheetTitle>
          <SheetDescription className="sr-only">
            Create a new custom role from scratch or by cloning an existing role
          </SheetDescription>
        </SheetHeader>

        <div className="flex-1 min-h-0 flex flex-col space-y-4">
          {/* Mode toggle */}
          <div className="shrink-0 grid grid-cols-2 gap-3">
            <button
              type="button"
              className={cn(
                "rounded-xl border p-3 text-left transition-all",
                mode === "scratch"
                  ? "border-primary bg-primary/5 ring-1 ring-primary/20"
                  : "border-border hover:border-muted-foreground/30",
              )}
              onClick={() => handleModeChange("scratch")}
            >
              <div
                className={cn(
                  "h-8 w-8 rounded-lg flex items-center justify-center mb-2",
                  mode === "scratch"
                    ? "bg-primary/10 text-primary"
                    : "bg-muted text-muted-foreground",
                )}
              >
                <Plus className="h-4 w-4" />
              </div>
              <div
                className={cn(
                  "text-sm font-medium",
                  mode === "scratch"
                    ? "text-foreground"
                    : "text-muted-foreground",
                )}
              >
                Start from Scratch
              </div>
              <div className="text-xs text-muted-foreground mt-0.5">
                Begin with no permissions
              </div>
            </button>
            <button
              type="button"
              className={cn(
                "rounded-xl border p-3 text-left transition-all",
                mode === "clone"
                  ? "border-primary bg-primary/5 ring-1 ring-primary/20"
                  : "border-border hover:border-muted-foreground/30",
              )}
              onClick={() => handleModeChange("clone")}
            >
              <div
                className={cn(
                  "h-8 w-8 rounded-lg flex items-center justify-center mb-2",
                  mode === "clone"
                    ? "bg-primary/10 text-primary"
                    : "bg-muted text-muted-foreground",
                )}
              >
                <Copy className="h-4 w-4" />
              </div>
              <div
                className={cn(
                  "text-sm font-medium",
                  mode === "clone"
                    ? "text-foreground"
                    : "text-muted-foreground",
                )}
              >
                Clone Existing Role
              </div>
              <div className="text-xs text-muted-foreground mt-0.5">
                Copy permissions from a role
              </div>
            </button>
          </div>

          {/* Role info card */}
          <div className="shrink-0 rounded-xl border border-border p-4 space-y-3">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div className="space-y-1">
                <Label
                  htmlFor="create-role-name"
                  className="text-xs text-muted-foreground"
                >
                  Role Name *
                </Label>
                <Input
                  id="create-role-name"
                  placeholder="e.g. Senior Partner Admin"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  className="h-9"
                />
              </div>
              <div className="space-y-1">
                <Label
                  htmlFor="create-role-desc"
                  className="text-xs text-muted-foreground"
                >
                  Description
                </Label>
                <Input
                  id="create-role-desc"
                  placeholder="Brief description..."
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  className="h-9"
                />
              </div>
            </div>

            {mode === "scratch" ? (
              <div className="space-y-1">
                <Label
                  htmlFor="create-role-type"
                  className="text-xs text-muted-foreground"
                >
                  Role Type *
                </Label>
                <Select
                  value={roleType}
                  onValueChange={(v) =>
                    setRoleType(v as "INTERNAL" | "EXTERNAL")
                  }
                >
                  <SelectTrigger id="create-role-type" className="h-9">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="INTERNAL">Internal</SelectItem>
                    <SelectItem value="EXTERNAL">External</SelectItem>
                  </SelectContent>
                </Select>
                <p className="text-xs text-muted-foreground">
                  Internal roles are for client admins. External roles are for
                  partner users.
                </p>
              </div>
            ) : (
              <div className="space-y-1">
                <Label className="text-xs text-muted-foreground">
                  Clone From *
                </Label>
                <Popover
                  open={cloneComboboxOpen}
                  onOpenChange={setCloneComboboxOpen}
                >
                  <PopoverTrigger asChild>
                    <Button
                      variant="outline"
                      role="combobox"
                      aria-expanded={cloneComboboxOpen}
                      className="w-full justify-between h-9 font-normal"
                    >
                      {cloneSourceRole
                        ? cloneSourceRole.name
                        : "Select a role to clone..."}
                      <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
                    </Button>
                  </PopoverTrigger>
                  <PopoverContent
                    className="w-[--radix-popover-trigger-width] p-0"
                    align="start"
                  >
                    <Command>
                      <CommandInput placeholder="Search roles..." />
                      <CommandList>
                        <CommandEmpty>No roles found.</CommandEmpty>
                        {internalRoles.length > 0 && (
                          <CommandGroup heading="Internal">
                            {internalRoles.map((role) => (
                              <CommandItem
                                key={role.id}
                                value={role.name}
                                onSelect={() => {
                                  setCloneSourceId(role.id);
                                  setCloneComboboxOpen(false);
                                }}
                              >
                                <Check
                                  className={cn(
                                    "mr-2 h-4 w-4",
                                    cloneSourceId === role.id
                                      ? "opacity-100"
                                      : "opacity-0",
                                  )}
                                />
                                {role.name}
                              </CommandItem>
                            ))}
                          </CommandGroup>
                        )}
                        {externalRoles.length > 0 && (
                          <CommandGroup heading="External">
                            {externalRoles.map((role) => (
                              <CommandItem
                                key={role.id}
                                value={role.name}
                                onSelect={() => {
                                  setCloneSourceId(role.id);
                                  setCloneComboboxOpen(false);
                                }}
                              >
                                <Check
                                  className={cn(
                                    "mr-2 h-4 w-4",
                                    cloneSourceId === role.id
                                      ? "opacity-100"
                                      : "opacity-0",
                                  )}
                                />
                                {role.name}
                              </CommandItem>
                            ))}
                          </CommandGroup>
                        )}
                      </CommandList>
                    </Command>
                  </PopoverContent>
                </Popover>
                {cloneSourceRole && (
                  <p className="text-xs text-muted-foreground">
                    Role type:{" "}
                    <span className="font-medium">
                      {cloneSourceRole.roleType === "INTERNAL"
                        ? "Internal"
                        : "External"}
                    </span>{" "}
                    — inherited from source role
                  </p>
                )}
              </div>
            )}

            {/* Home Dashboard Template (optional, gated by permission) */}
            <div className="space-y-1">
              <Label
                htmlFor="create-role-home-dashboard-template"
                className="text-xs text-muted-foreground"
              >
                Home Dashboard Template
              </Label>
              <Select
                value={templateId || "__unassigned__"}
                onValueChange={(v) =>
                  setTemplateId(v === "__unassigned__" ? "" : v)
                }
                disabled={!canAssignTemplate}
              >
                <SelectTrigger
                  id="create-role-home-dashboard-template"
                  className="h-9"
                  title={
                    canAssignTemplate
                      ? undefined
                      : "You do not have permission to assign dashboard templates."
                  }
                >
                  <SelectValue placeholder="Default for role type" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="__unassigned__">
                    <span className="text-muted-foreground">
                      Default for role type
                    </span>
                  </SelectItem>
                  {templates.map((t) => (
                    <SelectItem key={t.id} value={t.id}>
                      {t.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <p className="text-xs text-muted-foreground">
                Leave on &quot;Default for role type&quot; to inherit the tenant
                default. Can be changed later when editing the role.
              </p>
            </div>
          </div>

          {/* Permissions header */}
          <div className="shrink-0 flex items-center justify-between gap-3">
            <div className="flex items-center gap-2">
              <Shield className="h-4 w-4 text-muted-foreground" />
              <span className="text-sm font-medium text-foreground">
                Permissions
              </span>
              <span className="text-xs text-muted-foreground">
                {enabledCount}/{permCount} enabled
              </span>
            </div>
            <div className="relative w-[200px]">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
              <Input
                placeholder="Search permissions..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="pl-10 h-9"
              />
            </div>
          </div>

          {/* Permissions grid */}
          <div className="relative flex-1 min-h-0">
            <div
              ref={scrollRef}
              className="absolute inset-0 overflow-y-auto space-y-4 pr-1"
            >
              {Array.from(categories.entries()).map(([category, perms]) => (
                <div
                  key={category}
                  className="rounded-xl border border-border p-4 space-y-3"
                >
                  <h5 className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                    {formatCategoryLabel(category)}
                  </h5>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    {perms.map((perm) => (
                      <div
                        key={perm.permissionKey}
                        className="flex items-center justify-between gap-3 p-3 rounded-md border border-border hover:bg-muted/50 transition-colors"
                      >
                        <div className="flex-1 min-w-0">
                          <div className="text-sm font-medium text-foreground leading-tight">
                            {perm.displayName}
                          </div>
                          <div className="text-xs text-muted-foreground mt-0.5 leading-snug">
                            {perm.description}
                          </div>
                        </div>
                        <Switch
                          checked={!!localPerms[perm.permissionKey]}
                          onCheckedChange={() =>
                            togglePermission(perm.permissionKey)
                          }
                        />
                      </div>
                    ))}
                  </div>
                </div>
              ))}

              {filteredPermissions.length === 0 && (
                <p className="text-sm text-muted-foreground text-center py-4">
                  No permissions match your search.
                </p>
              )}
            </div>
          </div>

          {/* Action buttons */}
          <div className="flex gap-3 pt-4 border-t border-border shrink-0">
            <Button
              variant="outline"
              className="flex-1"
              onClick={() => onOpenChange(false)}
            >
              Cancel
            </Button>
            <Button
              className="flex-1 gap-2"
              onClick={handleCreate}
              disabled={!canSubmit}
            >
              {isPending ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <Plus className="h-4 w-4" />
              )}
              Create Role
            </Button>
          </div>
        </div>
      </SheetContent>
    </Sheet>
  );
}

// ─── Permissions Override Section ─────────────────────────────────────────────

interface PermissionOverrideSectionProps {
  title: string;
  icon: React.ReactNode;
  description: string;
  overrides: Record<string, boolean>;
  onOverridesChange: (overrides: Record<string, boolean>) => void;
  catalog: PermissionDefType[];
  isSaving: boolean;
  onSave: () => void;
}

function PermissionOverrideSection({
  title,
  icon,
  description,
  overrides,
  onOverridesChange,
  catalog,
  isSaving,
  onSave,
}: PermissionOverrideSectionProps) {
  const [searchQuery, setSearchQuery] = useState("");

  // Filter to partner-applicable permissions only (EXTERNAL or ALL scope)
  const applicablePermissions = useMemo(() => {
    return catalog.filter((p) => p.scope === "EXTERNAL" || p.scope === "ALL");
  }, [catalog]);

  const filteredPermissions = useMemo(() => {
    if (!searchQuery) return applicablePermissions;
    const q = searchQuery.toLowerCase();
    return applicablePermissions.filter(
      (p) =>
        p.displayName.toLowerCase().includes(q) ||
        p.description.toLowerCase().includes(q),
    );
  }, [applicablePermissions, searchQuery]);

  const categories = useMemo(() => {
    const cats = new Map<string, PermissionDefType[]>();
    filteredPermissions
      .sort((a, b) => a.sortOrder - b.sortOrder)
      .forEach((p) => {
        if (!cats.has(p.category)) cats.set(p.category, []);
        cats.get(p.category)!.push(p);
      });
    return cats;
  }, [filteredPermissions]);

  const togglePermission = (key: string) => {
    const current = overrides[key];
    const updated = { ...overrides };
    if (current === undefined) {
      // No override exists -> set to denied (false)
      updated[key] = false;
    } else if (current === false) {
      // Currently denied -> remove override (inherit)
      delete updated[key];
    } else {
      // Currently granted -> set to denied
      updated[key] = false;
    }
    onOverridesChange(updated);
  };

  const getEffectiveState = (key: string): boolean => {
    if (key in overrides) return overrides[key] ?? true;
    return true; // inherit = enabled by default
  };

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <div className="space-y-1">
            <CardTitle className="text-foreground flex items-center gap-2">
              {icon}
              {title}
            </CardTitle>
            <CardDescription>{description}</CardDescription>
          </div>
          <div className="flex items-center gap-3">
            <div className="relative w-[220px]">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
              <Input
                placeholder="Search permissions..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="pl-10 h-9"
              />
            </div>
            <Button onClick={onSave} disabled={isSaving} className="gap-2">
              {isSaving ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <Save className="h-4 w-4" />
              )}
              Save
            </Button>
          </div>
        </div>
      </CardHeader>
      <CardContent>
        <div className="space-y-4">
          {Array.from(categories.entries()).map(([category, perms]) => (
            <div
              key={category}
              className="rounded-xl border border-border p-4 space-y-3"
            >
              <h5 className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                {formatCategoryLabel(category)}
              </h5>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                {perms.map((perm) => {
                  const isEnabled = getEffectiveState(perm.permissionKey);
                  const hasOverride = perm.permissionKey in overrides;
                  return (
                    <div
                      key={perm.permissionKey}
                      className={`flex items-center justify-between gap-3 p-3 rounded-md border transition-colors ${
                        hasOverride && !isEnabled
                          ? "border-red-500/30 bg-red-500/5"
                          : "border-border hover:bg-muted/50"
                      }`}
                    >
                      <div className="flex-1 min-w-0">
                        <div className="text-sm font-medium text-foreground leading-tight flex items-center gap-1.5">
                          {perm.displayName}
                          {hasOverride && (
                            <Badge
                              variant="outline"
                              className="text-[10px] px-1 py-0 h-4"
                            >
                              Override
                            </Badge>
                          )}
                        </div>
                        <div className="text-xs text-muted-foreground mt-0.5 leading-snug">
                          {perm.description}
                        </div>
                      </div>
                      <Switch
                        checked={isEnabled}
                        onCheckedChange={() =>
                          togglePermission(perm.permissionKey)
                        }
                      />
                    </div>
                  );
                })}
              </div>
            </div>
          ))}

          {filteredPermissions.length === 0 && (
            <p className="text-sm text-muted-foreground text-center py-4">
              No permissions match your search.
            </p>
          )}
        </div>
      </CardContent>
    </Card>
  );
}

// ─── Component ───────────────────────────────────────────────────────────────

// ─── Helper: map DataObjectFieldResponse to DynamicField ──────────────────

function mapToDynamicFields(fields: DataObjectFieldResponse[]): DynamicField[] {
  return [...fields]
    .sort((a, b) => a.sortOrder - b.sortOrder)
    .map((f) => ({
      id: f.id,
      name: f.name,
      dataType: f.dataType,
      mandatory: f.mandatory,
      sampleValues: f.sampleValues ? JSON.stringify(f.sampleValues) : undefined,
      sortOrder: f.sortOrder,
    }));
}

// ─── Partner company field mapping ─────────────────────────────────────────

const PARTNER_FIELD_MAP: Record<string, string> = {
  "Partner Name": "name",
  Region: "region",
  "Partner Type": "partnerType",
  "Contact Email": "contactEmail",
  "Partner Status": "status",
  Website: "website",
  "Contact Phone": "contactPhone",
  "External Partner ID": "externalPartnerId",
};

function partnerFormToRequest(
  values: Record<string, unknown>,
): CreatePartnerCompanyRequest {
  const req: Record<string, unknown> = {};
  const meta: Record<string, unknown> = {};
  for (const [key, val] of Object.entries(values)) {
    const apiField = PARTNER_FIELD_MAP[key];
    if (apiField) {
      req[apiField] = val;
    } else {
      meta[key] = val;
    }
  }
  return {
    name: (req.name as string) ?? "",
    region: (req.region as string) ?? "",
    partnerType: (req.partnerType as string) ?? "",
    status: (req.status as "ACTIVE" | "INACTIVE") ?? undefined,
    contactEmail: (req.contactEmail as string) ?? undefined,
    website: (req.website as string) ?? undefined,
    contactPhone: (req.contactPhone as string) ?? undefined,
    externalPartnerId: (req.externalPartnerId as string) ?? undefined,
    metadata: Object.keys(meta).length > 0 ? JSON.stringify(meta) : undefined,
  };
}

function partnerToFormValues(
  company: ApiPartnerCompany,
): Record<string, unknown> {
  const vals: Record<string, unknown> = {};
  // Reverse map: API field -> form field name
  const reverseMap: Record<string, string> = {};
  for (const [formName, apiName] of Object.entries(PARTNER_FIELD_MAP)) {
    reverseMap[apiName] = formName;
  }
  // Populate known fields
  const apiObj: Record<string, unknown> = {
    name: company.name,
    region: company.region,
    partnerType: company.partnerType,
    contactEmail: company.contactEmail,
    status: company.status,
    website: company.website,
    contactPhone: company.contactPhone,
    externalPartnerId: company.externalPartnerId,
  };
  for (const [apiName, formName] of Object.entries(reverseMap)) {
    if (apiObj[apiName] !== undefined && apiObj[apiName] !== null) {
      vals[formName] = apiObj[apiName];
    }
  }
  // Populate metadata fields
  if (company.metadata) {
    try {
      const meta = JSON.parse(company.metadata);
      for (const [k, v] of Object.entries(meta)) {
        vals[k] = v;
      }
    } catch {
      // ignore invalid JSON
    }
  }
  return vals;
}

// ─── Partner user field mapping ────────────────────────────────────────────

const PARTNER_USER_FIELD_MAP: Record<string, string> = {
  "Employee First Name": "firstName",
  "Employee Last Name": "lastName",
  "Employee Email": "email",
  Phone: "phone",
};

function partnerUserFormToCreateRequest(
  values: Record<string, unknown>,
  partnerCompanyId: string,
  clientRoleId: string,
): CreateUserRequest {
  const req: Record<string, unknown> = {};
  const meta: Record<string, unknown> = {};
  for (const [key, val] of Object.entries(values)) {
    const apiField = PARTNER_USER_FIELD_MAP[key];
    if (apiField) {
      req[apiField] = val;
    } else {
      meta[key] = val;
    }
  }
  return {
    email: (req.email as string) ?? "",
    firstName: (req.firstName as string) ?? "",
    lastName: (req.lastName as string) ?? "",
    phone: (req.phone as string) ?? undefined,
    roleIds: [],
    partnerCompanyId,
    clientRoleId: clientRoleId || undefined,
    metadata: Object.keys(meta).length > 0 ? JSON.stringify(meta) : undefined,
  };
}

function partnerUserToFormValues(user: ApiUser): Record<string, unknown> {
  const vals: Record<string, unknown> = {};
  const reverseMap: Record<string, string> = {};
  for (const [formName, apiName] of Object.entries(PARTNER_USER_FIELD_MAP)) {
    reverseMap[apiName] = formName;
  }
  const apiObj: Record<string, unknown> = {
    firstName: user.firstName,
    lastName: user.lastName,
    email: user.email,
    phone: user.phone,
  };
  for (const [apiName, formName] of Object.entries(reverseMap)) {
    if (apiObj[apiName] !== undefined && apiObj[apiName] !== null) {
      vals[formName] = apiObj[apiName];
    }
  }
  if (user.metadata) {
    try {
      const meta = JSON.parse(user.metadata);
      for (const [k, v] of Object.entries(meta)) {
        vals[k] = v;
      }
    } catch {
      // ignore invalid JSON
    }
  }
  return vals;
}

// ─── Component ───────────────────────────────────────────────────────────────

function UserSettingsPage() {
  // ─── Users tab: API queries & mutations ───────────────────────────
  const { data: internalUsersData, isLoading: internalUsersLoading } = useUsers(
    { internal: true, pageSize: 100 },
  );
  const { data: partnerCompaniesData, isLoading: partnerCompaniesLoading } =
    usePartnerCompanies({ pageSize: 50, sortBy: "name", sortDirection: "ASC" });
  const { data: locationHierarchy } = useLocationHierarchy();
  const [selectedPartnerId, setSelectedPartnerId] = useState<string | null>(
    null,
  );
  const { data: partnerUsersData, isLoading: partnerUsersLoading } = useUsers(
    selectedPartnerId
      ? { partnerCompanyId: selectedPartnerId, pageSize: 100 }
      : undefined,
  );
  const selectedPartnerCompany = useMemo(() => {
    if (!selectedPartnerId || !partnerCompaniesData?.data) return null;
    return (
      partnerCompaniesData.data.find((c) => c.id === selectedPartnerId) ?? null
    );
  }, [selectedPartnerId, partnerCompaniesData]);

  const createUser = useCreateUser();
  const updateUser = useUpdateUser();
  const deleteUser = useDeleteUser();
  const createPartnerCompanyMut = useCreatePartnerCompany();
  const updatePartnerCompanyMut = useUpdatePartnerCompany();
  const deletePartnerCompanyMut = useDeletePartnerCompany();

  // Client roles for role selectors
  const { data: allClientRoles } = useClientRoles();
  const internalRoles = useMemo(() => {
    if (!allClientRoles) return [];
    return allClientRoles.filter(
      (r: ClientRole) =>
        r.roleType === "INTERNAL" && r.baseRoleName !== "TENX_ADMIN",
    );
  }, [allClientRoles]);
  const externalRoles = useMemo(() => {
    if (!allClientRoles) return [];
    return allClientRoles.filter((r: ClientRole) => r.roleType === "EXTERNAL");
  }, [allClientRoles]);

  // Dynamic form fields
  const { data: partnerDataObject, isLoading: partnerFieldsLoading } =
    useDataObjectByName("Partner Data");
  const { data: partnerUserDataObject, isLoading: partnerUserFieldsLoading } =
    useDataObjectByName("Partner User Data");
  const partnerDynamicFields = useMemo(
    () =>
      partnerDataObject?.fields
        ? mapToDynamicFields(partnerDataObject.fields)
        : [],
    [partnerDataObject],
  );
  const partnerUserDynamicFields = useMemo(
    () =>
      partnerUserDataObject?.fields
        ? mapToDynamicFields(partnerUserDataObject.fields)
        : [],
    [partnerUserDataObject],
  );

  // ─── Users tab: sheet state ───────────────────────────────────────
  const [addInternalUserOpen, setAddInternalUserOpen] = useState(false);
  const [addInternalForm, setAddInternalForm] = useState({
    firstName: "",
    lastName: "",
    email: "",
    clientRoleId: "",
  });

  const [editUserSheetOpen, setEditUserSheetOpen] = useState(false);
  const [editingApiUser, setEditingApiUser] = useState<ApiUser | null>(null);
  const [editingUserType, setEditingUserType] = useState<
    "internal" | "partner"
  >("internal");
  const [editUserForm, setEditUserForm] = useState({
    firstName: "",
    lastName: "",
    email: "",
    clientRoleId: "",
    status: "" as ApiUserStatusType | "",
  });
  const [editPartnerUserDynValues, setEditPartnerUserDynValues] = useState<
    Record<string, unknown>
  >({});

  const [addPartnerCompanyOpen, setAddPartnerCompanyOpen] = useState(false);
  const [addPartnerFormValues, setAddPartnerFormValues] = useState<
    Record<string, unknown>
  >({});

  const [editPartnerCompanyOpen, setEditPartnerCompanyOpen] = useState(false);
  const [editingPartnerCompany, setEditingPartnerCompany] =
    useState<ApiPartnerCompany | null>(null);
  const [editPartnerFormValues, setEditPartnerFormValues] = useState<
    Record<string, unknown>
  >({});

  const [addPartnerUserOpen, setAddPartnerUserOpen] = useState(false);
  const [addPartnerUserFormValues, setAddPartnerUserFormValues] = useState<
    Record<string, unknown>
  >({});
  const [addPartnerUserRoleId, setAddPartnerUserRoleId] = useState("");

  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<{
    type: "user" | "company";
    id: string;
    name: string;
  } | null>(null);

  // Track flip direction for Users tab
  const [usersFlipReverse, setUsersFlipReverse] = useState(false);

  // ─── Roles tab state ───────────────────────────────────────────────
  const { data: clientRoles, isLoading: rolesLoading } = useClientRoles();
  const { data: permCatalog, isLoading: catalogLoading } =
    usePermissionCatalog();
  const deleteRole = useDeleteClientRole();
  const { toast: legacyToast } = useToast();

  const [roleSheetOpen, setRoleSheetOpen] = useState(false);
  const [roleSheetTarget, setRoleSheetTarget] = useState<ClientRole | null>(
    null,
  );
  const [roleSheetReadOnly, setRoleSheetReadOnly] = useState(false);
  const [createRoleSheetOpen, setCreateRoleSheetOpen] = useState(false);

  // Filter out TENX_ADMIN roles
  const visibleRoles = useMemo(() => {
    if (!clientRoles) return [];
    return clientRoles.filter(
      (r: ClientRole) => r.baseRoleName !== "TENX_ADMIN",
    );
  }, [clientRoles]);

  const openRoleSheet = useCallback((role: ClientRole, readOnly: boolean) => {
    setRoleSheetTarget(role);
    setRoleSheetReadOnly(readOnly);
    setRoleSheetOpen(true);
  }, []);

  const handleDeleteRole = useCallback(
    async (role: ClientRole) => {
      try {
        await deleteRole.mutateAsync(role.id);
        legacyToast({
          title: "Role Deleted",
          description: `"${role.name}" has been deleted.`,
        });
      } catch {
        legacyToast({
          title: "Error",
          description: "Failed to delete role.",
          variant: "destructive",
        });
      }
    },
    [deleteRole, legacyToast],
  );

  // ─── Permissions tab state ─────────────────────────────────────────
  const [permsSelectedCompanyId, setPermsSelectedCompanyId] =
    useState<string>("");
  const [permsSelectedCompanyName, setPermsSelectedCompanyName] =
    useState<string>("");
  const [permsSelectedUserId, setPermsSelectedUserId] = useState<string>("");
  const [companyPopoverOpen, setCompanyPopoverOpen] = useState(false);
  const [userPopoverOpen, setUserPopoverOpen] = useState(false);

  // Fetch partner companies from API (large page for combobox search)
  const { data: permsPartnerCompaniesData } = useQuery({
    queryKey: ["perms-partner-companies-all"],
    queryFn: async () => {
      const { default: api } = await import("@/lib/axios");
      const resp = await api.get("/partner-companies", {
        params: { page: 0, pageSize: 500, status: "ACTIVE", sort: "name,asc" },
      });
      return resp.data.data as {
        data: Array<{ id: string; name: string; region: string }>;
      };
    },
  });
  const partnerCompanyOptions = permsPartnerCompaniesData?.data ?? [];

  // Fetch users for selected company from API
  const { data: companyUsersData, isLoading: companyUsersLoading } = useQuery({
    queryKey: ["perms-company-users", permsSelectedCompanyId],
    queryFn: () =>
      getUsers({ partnerCompanyId: permsSelectedCompanyId, pageSize: 100 }),
    enabled: !!permsSelectedCompanyId,
  });
  const companyUserOptions = companyUsersData?.data ?? [];

  // Company overrides
  const { data: companyOverridesData } = useCompanyOverrides(
    permsSelectedCompanyId || undefined,
  );
  const updateCompanyOverrides = useUpdateCompanyOverrides();
  const [localCompanyOverrides, setLocalCompanyOverrides] = useState<
    Record<string, boolean>
  >({});

  // User overrides
  const { data: userOverridesData, isLoading: userOverridesLoading } =
    useUserOverrides(
      permsSelectedCompanyId || undefined,
      permsSelectedUserId || undefined,
    );
  const updateUserOverrides = useUpdateUserOverrides();
  const [localUserOverrides, setLocalUserOverrides] = useState<
    Record<string, boolean>
  >({});

  // Sync server data into local state
  useEffect(() => {
    if (companyOverridesData) {
      setLocalCompanyOverrides(
        (companyOverridesData.permissions ?? {}) as Record<string, boolean>,
      );
    } else {
      setLocalCompanyOverrides({});
    }
  }, [companyOverridesData]);

  useEffect(() => {
    if (userOverridesData) {
      setLocalUserOverrides(
        (userOverridesData.permissions ?? {}) as Record<string, boolean>,
      );
    } else {
      setLocalUserOverrides({});
    }
  }, [userOverridesData]);

  // Reset user when company changes
  useEffect(() => {
    setPermsSelectedUserId("");
    setLocalUserOverrides({});
  }, [permsSelectedCompanyId]);

  const handleSaveCompanyOverrides = async () => {
    if (!permsSelectedCompanyId) return;
    try {
      await updateCompanyOverrides.mutateAsync({
        companyId: permsSelectedCompanyId,
        data: { permissions: localCompanyOverrides },
      });
      legacyToast({
        title: "Company Permissions Saved",
        description: "Company-level permission overrides have been updated.",
      });
    } catch {
      legacyToast({
        title: "Error",
        description: "Failed to save company permissions.",
        variant: "destructive",
      });
    }
  };

  const handleSaveUserOverrides = async () => {
    if (!permsSelectedCompanyId || !permsSelectedUserId) return;
    try {
      await updateUserOverrides.mutateAsync({
        companyId: permsSelectedCompanyId,
        userId: permsSelectedUserId,
        data: { permissions: localUserOverrides },
      });
      legacyToast({
        title: "User Permissions Saved",
        description: "User-level permission overrides have been updated.",
      });
    } catch {
      legacyToast({
        title: "Error",
        description: "Failed to save user permissions.",
        variant: "destructive",
      });
    }
  };

  // ─── Users tab handlers ─────────────────────────────────────────

  const handleInlineStatusChange = useCallback(
    async (userId: string, newStatus: ApiUserStatusType) => {
      try {
        await updateUser.mutateAsync({
          id: userId,
          data: { status: newStatus },
        });
        toast.success("User status updated");
      } catch {
        toast.error("Failed to update user status");
      }
    },
    [updateUser],
  );

  const handleCreateInternalUser = useCallback(async () => {
    const { firstName, lastName, email, clientRoleId } = addInternalForm;
    if (!firstName.trim() || !lastName.trim() || !email.trim()) return;
    try {
      await createUser.mutateAsync({
        email: email.trim(),
        firstName: firstName.trim(),
        lastName: lastName.trim(),
        roleIds: [],
        clientRoleId: clientRoleId || undefined,
      });
      toast.success(
        "Internal user created. A temporary password will be emailed to the user.",
      );
      setAddInternalUserOpen(false);
      setAddInternalForm({
        firstName: "",
        lastName: "",
        email: "",
        clientRoleId: "",
      });
    } catch {
      toast.error("Failed to create internal user");
    }
  }, [addInternalForm, createUser]);

  const openEditUserSheet = useCallback(
    (user: ApiUser, type: "internal" | "partner") => {
      setEditingApiUser(user);
      setEditingUserType(type);
      setEditUserForm({
        firstName: user.firstName,
        lastName: user.lastName,
        email: user.email,
        clientRoleId: user.clientRoleId ?? "",
        status: user.status,
      });
      if (type === "partner" && partnerUserDataObject?.fields) {
        setEditPartnerUserDynValues(partnerUserToFormValues(user));
      }
      setEditUserSheetOpen(true);
    },
    [partnerUserDataObject],
  );

  const handleEditUserSave = useCallback(async () => {
    if (!editingApiUser) return;
    try {
      const data: UpdateUserRequest = {
        firstName: editUserForm.firstName.trim() || undefined,
        lastName: editUserForm.lastName.trim() || undefined,
        email: editUserForm.email.trim() || undefined,
        status: (editUserForm.status as ApiUserStatusType) || undefined,
        clientRoleId: editUserForm.clientRoleId || undefined,
      };
      // For partner users, include metadata from dynamic fields
      if (editingUserType === "partner") {
        const meta: Record<string, unknown> = {};
        for (const [key, val] of Object.entries(editPartnerUserDynValues)) {
          if (
            !Object.values(PARTNER_USER_FIELD_MAP).includes(key) &&
            !Object.keys(PARTNER_USER_FIELD_MAP).includes(key)
          ) {
            // This is a metadata field, not a mapped standard field
            const apiField = PARTNER_USER_FIELD_MAP[key];
            if (!apiField) {
              meta[key] = val;
            }
          }
        }
        if (Object.keys(meta).length > 0) {
          data.metadata = JSON.stringify(meta);
        }
      }
      await updateUser.mutateAsync({ id: editingApiUser.id, data });
      toast.success("User updated successfully");
      setEditUserSheetOpen(false);
    } catch {
      toast.error("Failed to update user");
    }
  }, [
    editingApiUser,
    editUserForm,
    editingUserType,
    editPartnerUserDynValues,
    updateUser,
  ]);

  const handleDeleteConfirm = useCallback(async () => {
    if (!deleteTarget) return;
    try {
      if (deleteTarget.type === "user") {
        await deleteUser.mutateAsync(deleteTarget.id);
        toast.success(`User "${deleteTarget.name}" deleted`);
      } else {
        await deletePartnerCompanyMut.mutateAsync(deleteTarget.id);
        toast.success(`Partner company "${deleteTarget.name}" deleted`);
        if (selectedPartnerId === deleteTarget.id) {
          setSelectedPartnerId(null);
        }
      }
      setDeleteConfirmOpen(false);
      setDeleteTarget(null);
    } catch {
      toast.error(`Failed to delete ${deleteTarget.type}`);
    }
  }, [deleteTarget, deleteUser, deletePartnerCompanyMut, selectedPartnerId]);

  const handleCreatePartnerCompany = useCallback(async () => {
    if (!partnerDataObject?.fields) return;
    try {
      const req = partnerFormToRequest(addPartnerFormValues);
      if (!req.name.trim()) {
        toast.error("Partner Name is required");
        return;
      }
      await createPartnerCompanyMut.mutateAsync(req);
      toast.success("Partner company created");
      setAddPartnerCompanyOpen(false);
      setAddPartnerFormValues({});
    } catch {
      toast.error("Failed to create partner company");
    }
  }, [addPartnerFormValues, partnerDataObject, createPartnerCompanyMut]);

  const openEditPartnerCompany = useCallback(
    (company: ApiPartnerCompany) => {
      setEditingPartnerCompany(company);
      if (partnerDataObject?.fields) {
        setEditPartnerFormValues(partnerToFormValues(company));
      }
      setEditPartnerCompanyOpen(true);
    },
    [partnerDataObject],
  );

  const handleEditPartnerCompanySave = useCallback(async () => {
    if (!editingPartnerCompany || !partnerDataObject?.fields) return;
    try {
      const fullReq = partnerFormToRequest(editPartnerFormValues);
      const updateReq: UpdatePartnerCompanyRequest = {
        name: fullReq.name || undefined,
        region: fullReq.region || undefined,
        partnerType: fullReq.partnerType || undefined,
        status: fullReq.status || undefined,
        contactEmail: fullReq.contactEmail || undefined,
        website: fullReq.website || undefined,
        contactPhone: fullReq.contactPhone || undefined,
        externalPartnerId: fullReq.externalPartnerId || undefined,
        metadata: fullReq.metadata || undefined,
      };
      await updatePartnerCompanyMut.mutateAsync({
        id: editingPartnerCompany.id,
        data: updateReq,
      });
      toast.success("Partner company updated");
      setEditPartnerCompanyOpen(false);
    } catch {
      toast.error("Failed to update partner company");
    }
  }, [
    editingPartnerCompany,
    editPartnerFormValues,
    partnerDataObject,
    updatePartnerCompanyMut,
  ]);

  const handleCreatePartnerUser = useCallback(async () => {
    if (!selectedPartnerId) return;
    try {
      const req = partnerUserFormToCreateRequest(
        addPartnerUserFormValues,
        selectedPartnerId,
        addPartnerUserRoleId,
      );
      if (!req.email.trim() || !req.firstName.trim() || !req.lastName.trim()) {
        toast.error("First Name, Last Name, and Email are required");
        return;
      }
      await createUser.mutateAsync(req);
      toast.success(
        "Partner user created. A temporary password will be emailed to the user.",
      );
      setAddPartnerUserOpen(false);
      setAddPartnerUserFormValues({});
      setAddPartnerUserRoleId("");
    } catch {
      toast.error("Failed to create partner user");
    }
  }, [
    addPartnerUserFormValues,
    addPartnerUserRoleId,
    selectedPartnerId,
    createUser,
  ]);

  return (
    <div className="space-y-6">
      <PageBanner
        theme="users"
        title="User Settings"
        subtitle="Manage internal users, partner companies, and role assignments"
      />

      <Tabs defaultValue="users" className="space-y-4">
        <TabsList>
          <TabsTrigger value="users" className="gap-2">
            <Users className="h-4 w-4" />
            Users
          </TabsTrigger>
          <TabsTrigger value="roles" className="gap-2" data-tour="tab-roles">
            <UserCog className="h-4 w-4" />
            Roles
          </TabsTrigger>
          <TabsTrigger
            value="permissions"
            className="gap-2"
            data-tour="tab-permissions"
          >
            <Shield className="h-4 w-4" />
            Permissions
          </TabsTrigger>
        </TabsList>

        {/* ─── Users Tab ─────────────────────────────────────────────── */}
        <TabsContent
          value="users"
          className="space-y-6"
          data-tour="users-tab-content"
        >
          {/* Internal Users */}
          <Card>
            <CardHeader>
              <div className="flex items-center justify-between">
                <div className="space-y-1">
                  <CardTitle className="text-foreground flex items-center gap-2">
                    <User className="h-5 w-5 text-muted-foreground" />
                    Internal Users
                  </CardTitle>
                  <CardDescription>
                    Manage Your Team's Access to the Platform
                  </CardDescription>
                </div>
                <Button
                  onClick={() => setAddInternalUserOpen(true)}
                  data-tour="add-user-button"
                >
                  <Plus className="h-4 w-4 mr-2" />
                  Add Internal User
                </Button>
              </div>
            </CardHeader>
            <CardContent>
              {internalUsersLoading ? (
                <div className="flex items-center justify-center py-12">
                  <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
                  <span className="ml-2 text-sm text-muted-foreground">
                    Loading users...
                  </span>
                </div>
              ) : !internalUsersData?.data?.length ? (
                <div className="text-center py-12">
                  <User className="h-10 w-10 text-muted-foreground mx-auto mb-3" />
                  <p className="text-sm text-muted-foreground">
                    No internal users found.
                  </p>
                </div>
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead className="w-[280px]">User</TableHead>
                      <TableHead className="w-[160px]">Role</TableHead>
                      <TableHead className="w-[140px]">Date Added</TableHead>
                      <TableHead className="w-[130px]">Status</TableHead>
                      <TableHead className="w-[120px]"></TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {internalUsersData.data.map((user) => (
                      <TableRow key={user.id}>
                        <TableCell className="w-[280px]">
                          <div className="flex items-center gap-3">
                            <div className="h-8 w-8 rounded-full bg-primary/10 flex items-center justify-center shrink-0">
                              <User className="h-4 w-4 text-primary" />
                            </div>
                            <div className="min-w-0">
                              <div className="font-medium text-foreground truncate">
                                {user.firstName} {user.lastName}
                              </div>
                              <div className="text-sm text-muted-foreground truncate">
                                {user.email}
                              </div>
                            </div>
                          </div>
                        </TableCell>
                        <TableCell className="w-[160px]">
                          {user.clientRoleName ? (
                            <Badge
                              variant="outline"
                              className={getBaseRoleBadgeClass(
                                allClientRoles?.find(
                                  (r: ClientRole) => r.id === user.clientRoleId,
                                )?.baseRoleName ?? "",
                              )}
                            >
                              <Shield className="h-3 w-3 mr-1" />
                              {user.clientRoleName}
                            </Badge>
                          ) : (
                            <span className="text-sm text-muted-foreground">
                              No role
                            </span>
                          )}
                        </TableCell>
                        <TableCell className="w-[140px] text-muted-foreground">
                          {formatDate(user.createdAt)}
                        </TableCell>
                        <TableCell className="w-[130px]">
                          <Select
                            value={user.status}
                            onValueChange={(value) =>
                              handleInlineStatusChange(
                                user.id,
                                value as ApiUserStatusType,
                              )
                            }
                          >
                            <SelectTrigger
                              className={`w-[120px] h-8 text-xs ${getStatusBadgeClass(user.status)}`}
                            >
                              <SelectValue />
                            </SelectTrigger>
                            <SelectContent>
                              <SelectItem value="ACTIVE">Active</SelectItem>
                              <SelectItem value="INACTIVE">Inactive</SelectItem>
                            </SelectContent>
                          </Select>
                        </TableCell>
                        <TableCell className="w-[120px]">
                          <div className="flex items-center gap-1 justify-end">
                            <Button
                              variant="ghost"
                              size="sm"
                              className="gap-1"
                              onClick={() =>
                                openEditUserSheet(user, "internal")
                              }
                            >
                              <Pencil className="h-3.5 w-3.5" />
                              Edit
                            </Button>
                            <Button
                              variant="ghost"
                              size="sm"
                              className="gap-1 text-red-600 hover:text-red-700 hover:bg-red-500/10"
                              onClick={() => {
                                setDeleteTarget({
                                  type: "user",
                                  id: user.id,
                                  name: `${user.firstName} ${user.lastName}`,
                                });
                                setDeleteConfirmOpen(true);
                              }}
                            >
                              <Trash2 className="h-3.5 w-3.5" />
                            </Button>
                          </div>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>

          {/* Partner Companies */}
          <Card data-tour="partner-companies-section">
            <CardHeader>
              <div className="flex items-center justify-between">
                <div className="space-y-1">
                  <CardTitle className="text-foreground flex items-center gap-2">
                    <Building2 className="h-5 w-5 text-muted-foreground" />
                    Partner Companies
                  </CardTitle>
                  <CardDescription>
                    Manage Partner Organizations and Their Users
                  </CardDescription>
                </div>
                {selectedPartnerId ? (
                  <div className="flex items-center gap-2">
                    <Button
                      variant="ghost"
                      onClick={() => {
                        setUsersFlipReverse(true);
                        setSelectedPartnerId(null);
                      }}
                      className="gap-2"
                    >
                      <ChevronLeft className="h-4 w-4" />
                      Back to Companies
                    </Button>
                    {selectedPartnerCompany && (
                      <Button
                        variant="outline"
                        size="sm"
                        className="gap-1"
                        onClick={() =>
                          openEditPartnerCompany(selectedPartnerCompany)
                        }
                      >
                        <Pencil className="h-3.5 w-3.5" />
                        Edit Company
                      </Button>
                    )}
                  </div>
                ) : (
                  <Button
                    onClick={() => {
                      setAddPartnerFormValues({});
                      setAddPartnerCompanyOpen(true);
                    }}
                  >
                    <Plus className="h-4 w-4 mr-2" />
                    Add Partner Company
                  </Button>
                )}
              </div>
            </CardHeader>
            <CardContent>
              {partnerCompaniesLoading ? (
                <div className="flex items-center justify-center py-12">
                  <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
                  <span className="ml-2 text-sm text-muted-foreground">
                    Loading partner companies...
                  </span>
                </div>
              ) : (
                <FlipTransition
                  transitionKey={selectedPartnerId ?? "list"}
                  reverse={usersFlipReverse}
                >
                  {selectedPartnerId && selectedPartnerCompany ? (
                    <div className="space-y-4">
                      <div className="flex items-center justify-between p-4 rounded-lg border border-border bg-primary/5">
                        <div className="flex items-center gap-4">
                          <div className="p-3 rounded-lg bg-primary/10">
                            <Building2 className="h-6 w-6 text-primary" />
                          </div>
                          <div>
                            <div className="font-semibold text-lg text-foreground">
                              {selectedPartnerCompany.name}
                            </div>
                            <div className="text-sm text-muted-foreground flex items-center gap-2">
                              {(() => {
                                const tops = getTopLevelLocationNames(
                                  selectedPartnerCompany.locations,
                                  locationHierarchy,
                                );
                                if (tops.length === 0) return null;
                                return (
                                  <>
                                    <span>{tops.join(", ")}</span>
                                    <span>&bull;</span>
                                  </>
                                );
                              })()}
                              <Badge
                                variant="outline"
                                className={getCompanyStatusBadgeClass(
                                  selectedPartnerCompany.status,
                                )}
                              >
                                {selectedPartnerCompany.status}
                              </Badge>
                              <span>&bull;</span>
                              <span>
                                {selectedPartnerCompany.activeUserCount} users
                              </span>
                            </div>
                          </div>
                        </div>
                        <Button
                          size="sm"
                          onClick={() => {
                            setAddPartnerUserFormValues({});
                            setAddPartnerUserRoleId("");
                            setAddPartnerUserOpen(true);
                          }}
                        >
                          <Plus className="h-4 w-4 mr-2" />
                          Add User
                        </Button>
                      </div>

                      {partnerUsersLoading ? (
                        <div className="flex items-center justify-center py-8">
                          <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
                          <span className="ml-2 text-sm text-muted-foreground">
                            Loading users...
                          </span>
                        </div>
                      ) : !partnerUsersData?.data?.length ? (
                        <div className="text-center py-8">
                          <Users className="h-8 w-8 text-muted-foreground mx-auto mb-2" />
                          <p className="text-sm text-muted-foreground">
                            No users in this company yet.
                          </p>
                        </div>
                      ) : (
                        <Table>
                          <TableHeader>
                            <TableRow>
                              <TableHead className="w-[280px]">User</TableHead>
                              <TableHead className="w-[160px]">Role</TableHead>
                              <TableHead className="w-[140px]">
                                Date Added
                              </TableHead>
                              <TableHead className="w-[130px]">
                                Status
                              </TableHead>
                              <TableHead className="w-[120px]"></TableHead>
                            </TableRow>
                          </TableHeader>
                          <TableBody>
                            {partnerUsersData.data.map((user) => (
                              <TableRow key={user.id}>
                                <TableCell className="w-[280px]">
                                  <div className="flex items-center gap-3">
                                    <div className="h-8 w-8 rounded-full bg-primary/10 flex items-center justify-center shrink-0">
                                      <User className="h-4 w-4 text-primary" />
                                    </div>
                                    <div className="min-w-0">
                                      <div className="font-medium text-foreground truncate">
                                        {user.firstName} {user.lastName}
                                      </div>
                                      <div className="text-sm text-muted-foreground truncate">
                                        {user.email}
                                      </div>
                                    </div>
                                  </div>
                                </TableCell>
                                <TableCell className="w-[160px]">
                                  {user.clientRoleName ? (
                                    <Badge
                                      variant="outline"
                                      className={getBaseRoleBadgeClass(
                                        allClientRoles?.find(
                                          (r: ClientRole) =>
                                            r.id === user.clientRoleId,
                                        )?.baseRoleName ?? "",
                                      )}
                                    >
                                      <Shield className="h-3 w-3 mr-1" />
                                      {user.clientRoleName}
                                    </Badge>
                                  ) : (
                                    <span className="text-sm text-muted-foreground">
                                      No role
                                    </span>
                                  )}
                                </TableCell>
                                <TableCell className="w-[140px] text-muted-foreground">
                                  {formatDate(user.createdAt)}
                                </TableCell>
                                <TableCell className="w-[130px]">
                                  <Select
                                    value={user.status}
                                    onValueChange={(value) =>
                                      handleInlineStatusChange(
                                        user.id,
                                        value as ApiUserStatusType,
                                      )
                                    }
                                  >
                                    <SelectTrigger
                                      className={`w-[120px] h-8 text-xs ${getStatusBadgeClass(user.status)}`}
                                    >
                                      <SelectValue />
                                    </SelectTrigger>
                                    <SelectContent>
                                      <SelectItem value="ACTIVE">
                                        Active
                                      </SelectItem>
                                      <SelectItem value="INACTIVE">
                                        Inactive
                                      </SelectItem>
                                    </SelectContent>
                                  </Select>
                                </TableCell>
                                <TableCell className="w-[120px]">
                                  <div className="flex items-center gap-1 justify-end">
                                    <Button
                                      variant="ghost"
                                      size="sm"
                                      className="gap-1"
                                      onClick={() =>
                                        openEditUserSheet(user, "partner")
                                      }
                                    >
                                      <Pencil className="h-3.5 w-3.5" />
                                      Edit
                                    </Button>
                                    <Button
                                      variant="ghost"
                                      size="sm"
                                      className="gap-1 text-red-600 hover:text-red-700 hover:bg-red-500/10"
                                      onClick={() => {
                                        setDeleteTarget({
                                          type: "user",
                                          id: user.id,
                                          name: `${user.firstName} ${user.lastName}`,
                                        });
                                        setDeleteConfirmOpen(true);
                                      }}
                                    >
                                      <Trash2 className="h-3.5 w-3.5" />
                                    </Button>
                                  </div>
                                </TableCell>
                              </TableRow>
                            ))}
                          </TableBody>
                        </Table>
                      )}
                    </div>
                  ) : (
                    <div className="space-y-3">
                      {!partnerCompaniesData?.data?.length ? (
                        <div className="text-center py-12">
                          <Building2 className="h-10 w-10 text-muted-foreground mx-auto mb-3" />
                          <p className="text-sm text-muted-foreground">
                            No partner companies found.
                          </p>
                        </div>
                      ) : (
                        partnerCompaniesData.data.map((partner) => (
                          <div
                            key={partner.id}
                            className="flex items-center justify-between p-4 rounded-lg border border-border bg-muted/30 hover:bg-muted/50 transition-colors cursor-pointer"
                            onClick={() => {
                              setUsersFlipReverse(false);
                              setSelectedPartnerId(partner.id);
                            }}
                          >
                            <div className="flex items-center gap-4">
                              <div className="p-2 rounded-lg bg-primary/10">
                                <Building2 className="h-5 w-5 text-primary" />
                              </div>
                              <div>
                                <div className="font-semibold text-foreground">
                                  {partner.name}
                                </div>
                                <div className="text-sm text-muted-foreground flex items-center gap-2">
                                  {(() => {
                                    const tops = getTopLevelLocationNames(
                                      partner.locations,
                                      locationHierarchy,
                                    );
                                    if (tops.length === 0) return null;
                                    return <span>{tops.join(", ")}</span>;
                                  })()}
                                </div>
                              </div>
                            </div>
                            <div className="flex items-center gap-3">
                              <Badge
                                variant="outline"
                                className={getCompanyStatusBadgeClass(
                                  partner.status,
                                )}
                              >
                                {partner.status}
                              </Badge>
                              <div className="flex items-center gap-1 text-sm text-muted-foreground">
                                <Users className="h-4 w-4" />
                                <span>{partner.activeUserCount} users</span>
                              </div>
                              <ChevronRight className="h-5 w-5 text-muted-foreground" />
                            </div>
                          </div>
                        ))
                      )}
                    </div>
                  )}
                </FlipTransition>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        {/* ─── Roles Tab ─────────────────────────────────────────────── */}
        <TabsContent
          value="roles"
          className="space-y-4"
          data-tour="roles-tab-content"
        >
          <Card>
            <CardHeader>
              <div className="flex items-center justify-between">
                <div className="space-y-1">
                  <CardTitle className="text-foreground flex items-center gap-2">
                    <UserCog className="h-5 w-5 text-muted-foreground" />
                    Roles
                  </CardTitle>
                  <CardDescription>
                    Define roles with module access and action permissions
                  </CardDescription>
                </div>
                <Button
                  className="gap-2"
                  onClick={() => setCreateRoleSheetOpen(true)}
                >
                  <Plus className="h-4 w-4" />
                  Create Role
                </Button>
              </div>
            </CardHeader>
            <CardContent>
              {rolesLoading || catalogLoading ? (
                <div className="flex items-center justify-center py-12">
                  <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
                  <span className="ml-2 text-sm text-muted-foreground">
                    Loading roles...
                  </span>
                </div>
              ) : visibleRoles.length === 0 ? (
                <div className="text-center py-12">
                  <UserCog className="h-10 w-10 text-muted-foreground mx-auto mb-3" />
                  <p className="text-sm text-muted-foreground">
                    No roles configured yet.
                  </p>
                </div>
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead className="w-[300px]">Role</TableHead>
                      <TableHead className="w-[140px]">Base Role</TableHead>
                      <TableHead className="w-[120px]">Permissions</TableHead>
                      <TableHead className="w-[100px]">Type</TableHead>
                      <TableHead className="w-[160px]"></TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {visibleRoles.map((role: ClientRole) => {
                      const isClientAdmin =
                        role.baseRoleName === "CLIENT_ADMIN";
                      const isCustom = !role.isSystem;
                      const permCount = permCatalog
                        ? permCatalog.filter(
                            (p: PermissionDefType) =>
                              p.scope === "ALL" || p.scope === role.roleType,
                          ).length
                        : 0;
                      const enabledCount = Object.values(
                        role.permissions,
                      ).filter(Boolean).length;

                      return (
                        <TableRow key={role.id}>
                          <TableCell>
                            <div className="flex items-center gap-3">
                              <div className="h-8 w-8 rounded-full bg-primary/10 flex items-center justify-center shrink-0">
                                {isClientAdmin ? (
                                  <Lock className="h-4 w-4 text-primary" />
                                ) : (
                                  <UserCog className="h-4 w-4 text-primary" />
                                )}
                              </div>
                              <div className="min-w-0">
                                <div className="font-medium text-foreground flex items-center gap-2">
                                  {role.name}
                                </div>
                                <div className="text-sm text-muted-foreground truncate">
                                  {role.description}
                                </div>
                              </div>
                            </div>
                          </TableCell>
                          <TableCell>
                            <Badge
                              variant="outline"
                              className={getBaseRoleBadgeClass(
                                role.baseRoleName,
                              )}
                            >
                              {formatBaseRoleName(role.baseRoleName)}
                            </Badge>
                          </TableCell>
                          <TableCell>
                            <span className="text-sm text-muted-foreground">
                              {enabledCount}/{permCount}
                            </span>
                          </TableCell>
                          <TableCell>
                            {(() => {
                              const typeBadge = getRoleTypeBadge(role.roleType);
                              return (
                                <Badge
                                  variant="outline"
                                  className={typeBadge.className}
                                >
                                  {typeBadge.label}
                                </Badge>
                              );
                            })()}
                          </TableCell>
                          <TableCell>
                            <div className="flex items-center gap-1 justify-end">
                              {isClientAdmin ? (
                                <Button
                                  variant="ghost"
                                  size="sm"
                                  className="gap-1"
                                  onClick={() => openRoleSheet(role, true)}
                                >
                                  <Eye className="h-3.5 w-3.5" />
                                  View
                                </Button>
                              ) : (
                                <>
                                  <Button
                                    variant="ghost"
                                    size="sm"
                                    className="gap-1"
                                    onClick={() => openRoleSheet(role, false)}
                                  >
                                    <Pencil className="h-3.5 w-3.5" />
                                    Edit
                                  </Button>
                                  {isCustom && (
                                    <Button
                                      variant="ghost"
                                      size="sm"
                                      className="gap-1 text-red-600 hover:text-red-700 hover:bg-red-500/10"
                                      onClick={() => handleDeleteRole(role)}
                                      disabled={deleteRole.isPending}
                                    >
                                      <Trash2 className="h-3.5 w-3.5" />
                                      Delete
                                    </Button>
                                  )}
                                </>
                              )}
                            </div>
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        {/* ─── Permissions Tab ───────────────────────────────────────── */}
        <TabsContent
          value="permissions"
          className="space-y-6"
          data-tour="permissions-tab-content"
        >
          {/* Company Selector */}
          <Card>
            <CardHeader>
              <div className="space-y-1">
                <CardTitle className="text-foreground flex items-center gap-2">
                  <Building2 className="h-5 w-5 text-muted-foreground" />
                  Partner Permission Overrides
                </CardTitle>
                <CardDescription>
                  Override default role permissions at the company or user level
                </CardDescription>
              </div>
            </CardHeader>
            <CardContent>
              <div className="flex flex-col gap-2">
                <Label>Select Partner Company</Label>
                <Popover
                  open={companyPopoverOpen}
                  onOpenChange={setCompanyPopoverOpen}
                >
                  <PopoverTrigger asChild>
                    <Button
                      variant="outline"
                      role="combobox"
                      aria-expanded={companyPopoverOpen}
                      className="w-full max-w-md justify-between"
                    >
                      {permsSelectedCompanyName ||
                        "Choose a partner company..."}
                      <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
                    </Button>
                  </PopoverTrigger>
                  <PopoverContent className="w-full max-w-md p-0" align="start">
                    <Command>
                      <CommandInput placeholder="Search companies..." />
                      <CommandList>
                        <CommandEmpty>No companies found.</CommandEmpty>
                        <CommandGroup>
                          {partnerCompanyOptions.map((company) => (
                            <CommandItem
                              key={company.id}
                              value={`${company.name} ${company.region}`}
                              onSelect={() => {
                                setPermsSelectedCompanyId(company.id);
                                setPermsSelectedCompanyName(company.name);
                                setCompanyPopoverOpen(false);
                              }}
                            >
                              <Check
                                className={cn(
                                  "mr-2 h-4 w-4",
                                  permsSelectedCompanyId === company.id
                                    ? "opacity-100"
                                    : "opacity-0",
                                )}
                              />
                              <Building2 className="mr-2 h-3.5 w-3.5 text-muted-foreground" />
                              {company.name}
                              <span className="ml-1 text-muted-foreground text-xs">
                                ({company.region})
                              </span>
                            </CommandItem>
                          ))}
                        </CommandGroup>
                      </CommandList>
                    </Command>
                  </PopoverContent>
                </Popover>
              </div>
            </CardContent>
          </Card>

          {permsSelectedCompanyId && permCatalog && (
            <>
              {/* Company-Level Permissions */}
              <PermissionOverrideSection
                title="Company-Level Permissions"
                icon={<Building2 className="h-5 w-5 text-muted-foreground" />}
                description={`Override permissions for all users at ${
                  permsSelectedCompanyName || "this company"
                }`}
                overrides={localCompanyOverrides}
                onOverridesChange={setLocalCompanyOverrides}
                catalog={permCatalog}
                isSaving={updateCompanyOverrides.isPending}
                onSave={handleSaveCompanyOverrides}
              />

              {/* User-Level Permissions */}
              <Card>
                <CardHeader>
                  <div className="flex items-center justify-between">
                    <div className="space-y-1">
                      <CardTitle className="text-foreground flex items-center gap-2">
                        <User className="h-5 w-5 text-muted-foreground" />
                        User-Level Permissions
                      </CardTitle>
                      <CardDescription>
                        Override permissions for a specific user (overrides
                        company-level settings)
                      </CardDescription>
                    </div>
                  </div>
                </CardHeader>
                <CardContent>
                  <div className="space-y-4">
                    <div className="flex flex-col gap-2">
                      <Label>Select User</Label>
                      {companyUsersLoading ? (
                        <div className="flex items-center py-2">
                          <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" />
                          <span className="ml-2 text-sm text-muted-foreground">
                            Loading users...
                          </span>
                        </div>
                      ) : (
                        <Popover
                          open={userPopoverOpen}
                          onOpenChange={setUserPopoverOpen}
                        >
                          <PopoverTrigger asChild>
                            <Button
                              variant="outline"
                              role="combobox"
                              aria-expanded={userPopoverOpen}
                              className="w-full max-w-md justify-between"
                            >
                              {permsSelectedUserId
                                ? (() => {
                                    const u = companyUserOptions.find(
                                      (u) => u.id === permsSelectedUserId,
                                    );
                                    return u
                                      ? `${u.firstName} ${u.lastName}`
                                      : "Choose a user...";
                                  })()
                                : "Choose a user..."}
                              <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
                            </Button>
                          </PopoverTrigger>
                          <PopoverContent
                            className="w-full max-w-md p-0"
                            align="start"
                          >
                            <Command>
                              <CommandInput placeholder="Search users..." />
                              <CommandList>
                                <CommandEmpty>No users found.</CommandEmpty>
                                <CommandGroup>
                                  {companyUserOptions.map((user) => (
                                    <CommandItem
                                      key={user.id}
                                      value={`${user.firstName} ${user.lastName} ${user.email}`}
                                      onSelect={() => {
                                        setPermsSelectedUserId(user.id);
                                        setUserPopoverOpen(false);
                                      }}
                                    >
                                      <Check
                                        className={cn(
                                          "mr-2 h-4 w-4",
                                          permsSelectedUserId === user.id
                                            ? "opacity-100"
                                            : "opacity-0",
                                        )}
                                      />
                                      <User className="mr-2 h-3.5 w-3.5 text-muted-foreground" />
                                      {user.firstName} {user.lastName}
                                      <span className="ml-1 text-muted-foreground text-xs">
                                        ({user.clientRoleName ?? "No role"})
                                      </span>
                                    </CommandItem>
                                  ))}
                                </CommandGroup>
                              </CommandList>
                            </Command>
                          </PopoverContent>
                        </Popover>
                      )}
                    </div>

                    {permsSelectedUserId && (
                      <div className="space-y-4 pt-2">
                        {userOverridesLoading ? (
                          <div className="flex items-center justify-center py-8">
                            <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
                            <span className="ml-2 text-sm text-muted-foreground">
                              Loading user permissions...
                            </span>
                          </div>
                        ) : (
                          <>
                            {/* Inline permission grid for user overrides */}
                            {(() => {
                              const applicablePerms = permCatalog.filter(
                                (p: PermissionDefType) =>
                                  p.scope === "EXTERNAL" || p.scope === "ALL",
                              );
                              const cats = new Map<
                                string,
                                PermissionDefType[]
                              >();
                              applicablePerms
                                .sort(
                                  (
                                    a: PermissionDefType,
                                    b: PermissionDefType,
                                  ) => a.sortOrder - b.sortOrder,
                                )
                                .forEach((p: PermissionDefType) => {
                                  if (!cats.has(p.category))
                                    cats.set(p.category, []);
                                  cats.get(p.category)!.push(p);
                                });

                              const getEffective = (key: string): boolean => {
                                if (key in localUserOverrides)
                                  return localUserOverrides[key] ?? true;
                                return true;
                              };

                              const toggleUserPerm = (key: string) => {
                                const current = localUserOverrides[key];
                                const updated = { ...localUserOverrides };
                                if (current === undefined) {
                                  updated[key] = false;
                                } else if (current === false) {
                                  delete updated[key];
                                } else {
                                  updated[key] = false;
                                }
                                setLocalUserOverrides(updated);
                              };

                              return (
                                <div className="space-y-4">
                                  {Array.from(cats.entries()).map(
                                    ([category, perms]) => (
                                      <div
                                        key={category}
                                        className="rounded-xl border border-border p-4 space-y-3"
                                      >
                                        <h5 className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                                          {formatCategoryLabel(category)}
                                        </h5>
                                        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                                          {perms.map((perm) => {
                                            const isEnabled = getEffective(
                                              perm.permissionKey,
                                            );
                                            const hasOverride =
                                              perm.permissionKey in
                                              localUserOverrides;
                                            return (
                                              <div
                                                key={perm.permissionKey}
                                                className={`flex items-center justify-between gap-3 p-3 rounded-md border transition-colors ${
                                                  hasOverride && !isEnabled
                                                    ? "border-red-500/30 bg-red-500/5"
                                                    : "border-border hover:bg-muted/50"
                                                }`}
                                              >
                                                <div className="flex-1 min-w-0">
                                                  <div className="text-sm font-medium text-foreground leading-tight flex items-center gap-1.5">
                                                    {perm.displayName}
                                                    {hasOverride && (
                                                      <Badge
                                                        variant="outline"
                                                        className="text-[10px] px-1 py-0 h-4"
                                                      >
                                                        Override
                                                      </Badge>
                                                    )}
                                                  </div>
                                                  <div className="text-xs text-muted-foreground mt-0.5 leading-snug">
                                                    {perm.description}
                                                  </div>
                                                </div>
                                                <Switch
                                                  checked={isEnabled}
                                                  onCheckedChange={() =>
                                                    toggleUserPerm(
                                                      perm.permissionKey,
                                                    )
                                                  }
                                                />
                                              </div>
                                            );
                                          })}
                                        </div>
                                      </div>
                                    ),
                                  )}
                                </div>
                              );
                            })()}

                            <div className="flex justify-end pt-2">
                              <Button
                                onClick={handleSaveUserOverrides}
                                disabled={updateUserOverrides.isPending}
                                className="gap-2"
                              >
                                {updateUserOverrides.isPending ? (
                                  <Loader2 className="h-4 w-4 animate-spin" />
                                ) : (
                                  <Save className="h-4 w-4" />
                                )}
                                Save User Permissions
                              </Button>
                            </div>
                          </>
                        )}
                      </div>
                    )}
                  </div>
                </CardContent>
              </Card>
            </>
          )}

          {!permsSelectedCompanyId && (
            <div className="text-center py-12">
              <Building2 className="h-10 w-10 text-muted-foreground mx-auto mb-3" />
              <p className="text-sm text-muted-foreground">
                Select a partner company above to manage permission overrides.
              </p>
            </div>
          )}
        </TabsContent>
      </Tabs>

      {/* ─── Add Internal User Sheet ─────────────────────────────── */}
      <Sheet open={addInternalUserOpen} onOpenChange={setAddInternalUserOpen}>
        <SheetContent>
          <SheetHeader>
            <SheetTitle>Add Internal User</SheetTitle>
            <SheetDescription>
              Create a new internal user. A temporary password will be emailed
              to the user.
            </SheetDescription>
          </SheetHeader>
          <div className="space-y-4 mt-6">
            <div className="space-y-2">
              <Label htmlFor="add-int-first">
                First Name <span className="text-destructive">*</span>
              </Label>
              <Input
                id="add-int-first"
                value={addInternalForm.firstName}
                onChange={(e) =>
                  setAddInternalForm((prev) => ({
                    ...prev,
                    firstName: e.target.value,
                  }))
                }
                placeholder="First name"
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="add-int-last">
                Last Name <span className="text-destructive">*</span>
              </Label>
              <Input
                id="add-int-last"
                value={addInternalForm.lastName}
                onChange={(e) =>
                  setAddInternalForm((prev) => ({
                    ...prev,
                    lastName: e.target.value,
                  }))
                }
                placeholder="Last name"
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="add-int-email">
                Email <span className="text-destructive">*</span>
              </Label>
              <Input
                id="add-int-email"
                type="email"
                value={addInternalForm.email}
                onChange={(e) =>
                  setAddInternalForm((prev) => ({
                    ...prev,
                    email: e.target.value,
                  }))
                }
                placeholder="user@company.com"
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="add-int-role">Role</Label>
              <Select
                value={addInternalForm.clientRoleId}
                onValueChange={(value) =>
                  setAddInternalForm((prev) => ({
                    ...prev,
                    clientRoleId: value,
                  }))
                }
              >
                <SelectTrigger id="add-int-role">
                  <SelectValue placeholder="Select a role..." />
                </SelectTrigger>
                <SelectContent>
                  {internalRoles.map((role: ClientRole) => (
                    <SelectItem key={role.id} value={role.id}>
                      {role.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <Alert className="border-blue-500/30 bg-blue-500/5">
              <Lock className="h-4 w-4 text-blue-600" />
              <AlertDescription className="text-blue-700 dark:text-blue-400">
                A temporary password will be emailed to the user.
              </AlertDescription>
            </Alert>
            <div className="flex justify-end gap-3 pt-4">
              <Button
                variant="outline"
                onClick={() => setAddInternalUserOpen(false)}
              >
                Cancel
              </Button>
              <Button
                onClick={handleCreateInternalUser}
                disabled={
                  createUser.isPending ||
                  !addInternalForm.firstName.trim() ||
                  !addInternalForm.lastName.trim() ||
                  !addInternalForm.email.trim()
                }
                className="gap-2"
              >
                {createUser.isPending ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <Save className="h-4 w-4" />
                )}
                Create User
              </Button>
            </div>
          </div>
        </SheetContent>
      </Sheet>

      {/* ─── Edit User Sheet ───────────────────────────────────────── */}
      <Sheet open={editUserSheetOpen} onOpenChange={setEditUserSheetOpen}>
        <SheetContent>
          <SheetHeader>
            <SheetTitle>Edit User</SheetTitle>
            <SheetDescription>
              {editingUserType === "internal"
                ? "Update internal user details"
                : "Update partner user details"}
            </SheetDescription>
          </SheetHeader>
          <div className="space-y-4 mt-6">
            <div className="space-y-2">
              <Label htmlFor="edit-first">First Name</Label>
              <Input
                id="edit-first"
                value={editUserForm.firstName}
                onChange={(e) =>
                  setEditUserForm((prev) => ({
                    ...prev,
                    firstName: e.target.value,
                  }))
                }
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="edit-last">Last Name</Label>
              <Input
                id="edit-last"
                value={editUserForm.lastName}
                onChange={(e) =>
                  setEditUserForm((prev) => ({
                    ...prev,
                    lastName: e.target.value,
                  }))
                }
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="edit-email">Email</Label>
              <Input
                id="edit-email"
                type="email"
                value={editUserForm.email}
                onChange={(e) =>
                  setEditUserForm((prev) => ({
                    ...prev,
                    email: e.target.value,
                  }))
                }
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="edit-role">Role</Label>
              <Select
                value={editUserForm.clientRoleId}
                onValueChange={(value) =>
                  setEditUserForm((prev) => ({ ...prev, clientRoleId: value }))
                }
              >
                <SelectTrigger id="edit-role">
                  <SelectValue placeholder="Select a role..." />
                </SelectTrigger>
                <SelectContent>
                  {(editingUserType === "internal"
                    ? internalRoles
                    : externalRoles
                  ).map((role: ClientRole) => (
                    <SelectItem key={role.id} value={role.id}>
                      {role.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="edit-status">Status</Label>
              <Select
                value={editUserForm.status}
                onValueChange={(value) =>
                  setEditUserForm((prev) => ({
                    ...prev,
                    status: value as ApiUserStatusType,
                  }))
                }
              >
                <SelectTrigger id="edit-status">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ACTIVE">Active</SelectItem>
                  <SelectItem value="INACTIVE">Inactive</SelectItem>
                </SelectContent>
              </Select>
            </div>
            {/* Dynamic fields for partner users */}
            {editingUserType === "partner" &&
              partnerUserDynamicFields.length > 0 && (
                <div className="space-y-2 pt-2 border-t border-border">
                  <p className="text-xs font-medium text-muted-foreground uppercase tracking-wider">
                    Additional Fields
                  </p>
                  <DynamicFieldRenderer
                    fields={partnerUserDynamicFields.filter(
                      (f) =>
                        !Object.keys(PARTNER_USER_FIELD_MAP).includes(f.name),
                    )}
                    values={editPartnerUserDynValues}
                    onChange={(key, val) =>
                      setEditPartnerUserDynValues((prev) => ({
                        ...prev,
                        [key]: val,
                      }))
                    }
                  />
                </div>
              )}
            <div className="flex justify-end gap-3 pt-4">
              <Button
                variant="outline"
                onClick={() => setEditUserSheetOpen(false)}
              >
                Cancel
              </Button>
              <Button
                onClick={handleEditUserSave}
                disabled={updateUser.isPending}
                className="gap-2"
              >
                {updateUser.isPending ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <Save className="h-4 w-4" />
                )}
                Save Changes
              </Button>
            </div>
          </div>
        </SheetContent>
      </Sheet>

      {/* ─── Add Partner Company Sheet ─────────────────────────────── */}
      <Sheet
        open={addPartnerCompanyOpen}
        onOpenChange={setAddPartnerCompanyOpen}
      >
        <SheetContent>
          <SheetHeader>
            <SheetTitle>Add Partner Company</SheetTitle>
            <SheetDescription>Create a new partner company.</SheetDescription>
          </SheetHeader>
          <div className="space-y-4 mt-6">
            {partnerFieldsLoading ? (
              <div className="flex items-center justify-center py-8">
                <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
                <span className="ml-2 text-sm text-muted-foreground">
                  Loading form...
                </span>
              </div>
            ) : partnerDynamicFields.length > 0 ? (
              <DynamicFieldRenderer
                fields={partnerDynamicFields}
                values={addPartnerFormValues}
                onChange={(key, val) =>
                  setAddPartnerFormValues((prev) => ({ ...prev, [key]: val }))
                }
              />
            ) : (
              <div className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="add-pc-name">
                    Partner Name <span className="text-destructive">*</span>
                  </Label>
                  <Input
                    id="add-pc-name"
                    value={
                      (addPartnerFormValues["Partner Name"] as string) ?? ""
                    }
                    onChange={(e) =>
                      setAddPartnerFormValues((prev) => ({
                        ...prev,
                        "Partner Name": e.target.value,
                      }))
                    }
                    placeholder="Company name"
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="add-pc-region">
                    Region <span className="text-destructive">*</span>
                  </Label>
                  <Input
                    id="add-pc-region"
                    value={(addPartnerFormValues["Region"] as string) ?? ""}
                    onChange={(e) =>
                      setAddPartnerFormValues((prev) => ({
                        ...prev,
                        Region: e.target.value,
                      }))
                    }
                    placeholder="e.g. AMERICAS"
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="add-pc-type">
                    Partner Type <span className="text-destructive">*</span>
                  </Label>
                  <Input
                    id="add-pc-type"
                    value={
                      (addPartnerFormValues["Partner Type"] as string) ?? ""
                    }
                    onChange={(e) =>
                      setAddPartnerFormValues((prev) => ({
                        ...prev,
                        "Partner Type": e.target.value,
                      }))
                    }
                    placeholder="e.g. Reseller"
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="add-pc-email">Contact Email</Label>
                  <Input
                    id="add-pc-email"
                    type="email"
                    value={
                      (addPartnerFormValues["Contact Email"] as string) ?? ""
                    }
                    onChange={(e) =>
                      setAddPartnerFormValues((prev) => ({
                        ...prev,
                        "Contact Email": e.target.value,
                      }))
                    }
                  />
                </div>
              </div>
            )}
            <div className="flex justify-end gap-3 pt-4">
              <Button
                variant="outline"
                onClick={() => setAddPartnerCompanyOpen(false)}
              >
                Cancel
              </Button>
              <Button
                onClick={handleCreatePartnerCompany}
                disabled={createPartnerCompanyMut.isPending}
                className="gap-2"
              >
                {createPartnerCompanyMut.isPending ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <Save className="h-4 w-4" />
                )}
                Create Company
              </Button>
            </div>
          </div>
        </SheetContent>
      </Sheet>

      {/* ─── Edit Partner Company Sheet ────────────────────────────── */}
      <Sheet
        open={editPartnerCompanyOpen}
        onOpenChange={setEditPartnerCompanyOpen}
      >
        <SheetContent>
          <SheetHeader>
            <SheetTitle>Edit Partner Company</SheetTitle>
            <SheetDescription>
              Update partner company details for {editingPartnerCompany?.name}.
            </SheetDescription>
          </SheetHeader>
          <div className="space-y-4 mt-6">
            {partnerFieldsLoading ? (
              <div className="flex items-center justify-center py-8">
                <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
                <span className="ml-2 text-sm text-muted-foreground">
                  Loading form...
                </span>
              </div>
            ) : partnerDynamicFields.length > 0 ? (
              <DynamicFieldRenderer
                fields={partnerDynamicFields}
                values={editPartnerFormValues}
                onChange={(key, val) =>
                  setEditPartnerFormValues((prev) => ({ ...prev, [key]: val }))
                }
              />
            ) : (
              <div className="space-y-4">
                <div className="space-y-2">
                  <Label>Partner Name</Label>
                  <Input
                    value={
                      (editPartnerFormValues["Partner Name"] as string) ?? ""
                    }
                    onChange={(e) =>
                      setEditPartnerFormValues((prev) => ({
                        ...prev,
                        "Partner Name": e.target.value,
                      }))
                    }
                  />
                </div>
                <div className="space-y-2">
                  <Label>Region</Label>
                  <Input
                    value={(editPartnerFormValues["Region"] as string) ?? ""}
                    onChange={(e) =>
                      setEditPartnerFormValues((prev) => ({
                        ...prev,
                        Region: e.target.value,
                      }))
                    }
                  />
                </div>
                <div className="space-y-2">
                  <Label>Partner Type</Label>
                  <Input
                    value={
                      (editPartnerFormValues["Partner Type"] as string) ?? ""
                    }
                    onChange={(e) =>
                      setEditPartnerFormValues((prev) => ({
                        ...prev,
                        "Partner Type": e.target.value,
                      }))
                    }
                  />
                </div>
              </div>
            )}
            <div className="flex justify-end gap-3 pt-4">
              <Button
                variant="outline"
                onClick={() => setEditPartnerCompanyOpen(false)}
              >
                Cancel
              </Button>
              <Button
                onClick={handleEditPartnerCompanySave}
                disabled={updatePartnerCompanyMut.isPending}
                className="gap-2"
              >
                {updatePartnerCompanyMut.isPending ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <Save className="h-4 w-4" />
                )}
                Save Changes
              </Button>
            </div>
          </div>
        </SheetContent>
      </Sheet>

      {/* ─── Add Partner User Sheet ────────────────────────────────── */}
      <Sheet open={addPartnerUserOpen} onOpenChange={setAddPartnerUserOpen}>
        <SheetContent>
          <SheetHeader>
            <SheetTitle>Add Partner User</SheetTitle>
            <SheetDescription>
              Add a user to {selectedPartnerCompany?.name}. A temporary password
              will be emailed.
            </SheetDescription>
          </SheetHeader>
          <div className="space-y-4 mt-6">
            {partnerUserFieldsLoading ? (
              <div className="flex items-center justify-center py-8">
                <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
                <span className="ml-2 text-sm text-muted-foreground">
                  Loading form...
                </span>
              </div>
            ) : partnerUserDynamicFields.length > 0 ? (
              <DynamicFieldRenderer
                fields={partnerUserDynamicFields}
                values={addPartnerUserFormValues}
                onChange={(key, val) =>
                  setAddPartnerUserFormValues((prev) => ({
                    ...prev,
                    [key]: val,
                  }))
                }
              />
            ) : (
              <div className="space-y-4">
                <div className="space-y-2">
                  <Label>
                    First Name <span className="text-destructive">*</span>
                  </Label>
                  <Input
                    value={
                      (addPartnerUserFormValues[
                        "Employee First Name"
                      ] as string) ?? ""
                    }
                    onChange={(e) =>
                      setAddPartnerUserFormValues((prev) => ({
                        ...prev,
                        "Employee First Name": e.target.value,
                      }))
                    }
                    placeholder="First name"
                  />
                </div>
                <div className="space-y-2">
                  <Label>
                    Last Name <span className="text-destructive">*</span>
                  </Label>
                  <Input
                    value={
                      (addPartnerUserFormValues[
                        "Employee Last Name"
                      ] as string) ?? ""
                    }
                    onChange={(e) =>
                      setAddPartnerUserFormValues((prev) => ({
                        ...prev,
                        "Employee Last Name": e.target.value,
                      }))
                    }
                    placeholder="Last name"
                  />
                </div>
                <div className="space-y-2">
                  <Label>
                    Email <span className="text-destructive">*</span>
                  </Label>
                  <Input
                    type="email"
                    value={
                      (addPartnerUserFormValues["Employee Email"] as string) ??
                      ""
                    }
                    onChange={(e) =>
                      setAddPartnerUserFormValues((prev) => ({
                        ...prev,
                        "Employee Email": e.target.value,
                      }))
                    }
                    placeholder="user@partner.com"
                  />
                </div>
              </div>
            )}
            <div className="space-y-2">
              <Label htmlFor="add-pu-role">Role</Label>
              <Select
                value={addPartnerUserRoleId}
                onValueChange={setAddPartnerUserRoleId}
              >
                <SelectTrigger id="add-pu-role">
                  <SelectValue placeholder="Select a role..." />
                </SelectTrigger>
                <SelectContent>
                  {externalRoles.map((role: ClientRole) => (
                    <SelectItem key={role.id} value={role.id}>
                      {role.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <Alert className="border-blue-500/30 bg-blue-500/5">
              <Lock className="h-4 w-4 text-blue-600" />
              <AlertDescription className="text-blue-700 dark:text-blue-400">
                A temporary password will be emailed to the user.
              </AlertDescription>
            </Alert>
            <div className="flex justify-end gap-3 pt-4">
              <Button
                variant="outline"
                onClick={() => setAddPartnerUserOpen(false)}
              >
                Cancel
              </Button>
              <Button
                onClick={handleCreatePartnerUser}
                disabled={createUser.isPending}
                className="gap-2"
              >
                {createUser.isPending ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <Save className="h-4 w-4" />
                )}
                Create User
              </Button>
            </div>
          </div>
        </SheetContent>
      </Sheet>

      {/* ─── Delete Confirmation Dialog ────────────────────────────── */}
      <Dialog open={deleteConfirmOpen} onOpenChange={setDeleteConfirmOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Confirm Delete</DialogTitle>
            <DialogDescription>
              Are you sure you want to delete{" "}
              {deleteTarget?.type === "user" ? "user" : "partner company"}{" "}
              <strong>{deleteTarget?.name}</strong>? This action cannot be
              undone.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setDeleteConfirmOpen(false)}
            >
              Cancel
            </Button>
            <Button
              variant="destructive"
              onClick={handleDeleteConfirm}
              disabled={
                deleteUser.isPending || deletePartnerCompanyMut.isPending
              }
              className="gap-2"
            >
              {deleteUser.isPending || deletePartnerCompanyMut.isPending ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <Trash2 className="h-4 w-4" />
              )}
              Delete
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* ─── Role Edit Sheet ───────────────────────────────────────── */}
      <RoleEditSheet
        open={roleSheetOpen}
        onOpenChange={setRoleSheetOpen}
        role={roleSheetTarget}
        catalog={permCatalog ?? []}
        readOnly={roleSheetReadOnly}
      />

      {/* ─── Create Role Sheet ─────────────────────────────────────── */}
      <CreateRoleSheet
        open={createRoleSheetOpen}
        onOpenChange={setCreateRoleSheetOpen}
        catalog={permCatalog ?? []}
        existingRoles={visibleRoles}
      />
    </div>
  );
}

export default UserSettingsPage;

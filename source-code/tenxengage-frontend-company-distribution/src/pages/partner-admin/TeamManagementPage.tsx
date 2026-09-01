import { useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Switch } from "@/components/ui/switch";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Loader2, Save, Users, Mail } from "lucide-react";
import { PageBanner } from "@/components/PageBanner";
import { useToast } from "@/hooks/use-toast";
import {
  usePartnerUsers,
  useSellerPermissions,
  useUpdateSellerPermissions,
} from "@/hooks/usePartnerUserApi";
import { usePermissionCatalog } from "@/hooks/usePermissionApi";
import type { PermissionDef } from "@/types/permission.types";

function TeamManagementPage() {
  const { toast } = useToast();
  const { data: teamMembers, isLoading: membersLoading } = usePartnerUsers();
  const { data: permCatalog } = usePermissionCatalog();
  const [selectedUserId, setSelectedUserId] = useState<string | null>(null);
  const { data: sellerPermsRaw, isLoading: permsLoading } =
    useSellerPermissions(selectedUserId ?? undefined);
  const updatePerms = useUpdateSellerPermissions();

  // Local overrides for toggle state
  const [localOverrides, setLocalOverrides] = useState<Record<string, boolean>>(
    {},
  );
  const [overridesDirty, setOverridesDirty] = useState(false);

  const sellerPerms = sellerPermsRaw
    ? new Set(
        Array.isArray(sellerPermsRaw)
          ? sellerPermsRaw
          : Object.keys(sellerPermsRaw),
      )
    : new Set<string>();

  const handleSelectUser = (userId: string) => {
    setSelectedUserId(userId);
    setLocalOverrides({});
    setOverridesDirty(false);
  };

  const togglePerm = (key: string) => {
    const currentlyEnabled =
      key in localOverrides ? localOverrides[key] : sellerPerms.has(key);
    setLocalOverrides((prev) => ({ ...prev, [key]: !currentlyEnabled }));
    setOverridesDirty(true);
  };

  const getEffective = (key: string): boolean => {
    if (key in localOverrides) return localOverrides[key] ?? false;
    return sellerPerms.has(key);
  };

  const handleSave = () => {
    if (!selectedUserId || Object.keys(localOverrides).length === 0) return;

    // Build the permissions map — only send changed overrides
    const permissions: Record<string, boolean> = {};
    for (const [key, val] of Object.entries(localOverrides)) {
      permissions[key] = val;
    }

    updatePerms.mutate(
      { userId: selectedUserId, data: { permissions } },
      {
        onSuccess: () => {
          toast({
            title: "Permissions updated",
            description: "User permissions have been saved.",
          });
          setLocalOverrides({});
          setOverridesDirty(false);
        },
        onError: () => {
          toast({
            title: "Update failed",
            description:
              "Could not update permissions. You can only restrict permissions, not expand them.",
            variant: "destructive",
          });
        },
      },
    );
  };

  // Filter permission catalog to EXTERNAL/ALL scope only
  const applicablePerms = (permCatalog ?? []).filter(
    (p: PermissionDef) => p.scope === "EXTERNAL" || p.scope === "ALL",
  );

  // Group by category
  const permsByCategory = new Map<string, PermissionDef[]>();
  applicablePerms
    .sort((a: PermissionDef, b: PermissionDef) => a.sortOrder - b.sortOrder)
    .forEach((p: PermissionDef) => {
      if (!permsByCategory.has(p.category)) permsByCategory.set(p.category, []);
      permsByCategory.get(p.category)!.push(p);
    });

  const selectedUser = teamMembers?.find((u) => u.id === selectedUserId);

  return (
    <div className="space-y-6">
      <PageBanner
        theme="users"
        title="Team Management"
        subtitle="Manage your team's access and permissions"
      />

      {/* Team Members */}
      <Card>
        <CardHeader>
          <div className="flex items-center gap-3">
            <div className="p-2 rounded-lg bg-primary/10">
              <Users className="h-5 w-5 text-primary" />
            </div>
            <CardTitle>Team Members</CardTitle>
          </div>
        </CardHeader>
        <CardContent>
          {membersLoading ? (
            <div className="flex items-center justify-center py-8">
              <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
              <span className="ml-2 text-sm text-muted-foreground">
                Loading team members...
              </span>
            </div>
          ) : teamMembers && teamMembers.length > 0 ? (
            <div className="rounded-lg border overflow-hidden">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Name</TableHead>
                    <TableHead>Email</TableHead>
                    <TableHead>Role</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead className="w-[100px]">Actions</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {teamMembers.map((member) => (
                    <TableRow
                      key={member.id}
                      className={
                        selectedUserId === member.id
                          ? "bg-primary/5"
                          : undefined
                      }
                    >
                      <TableCell className="font-medium">
                        {member.firstName} {member.lastName}
                      </TableCell>
                      <TableCell>
                        <div className="flex items-center gap-1.5 text-muted-foreground">
                          <Mail className="h-3.5 w-3.5" />
                          {member.email}
                        </div>
                      </TableCell>
                      <TableCell>
                        <Badge
                          variant="outline"
                          className="bg-primary/10 text-primary border-primary/20"
                        >
                          {member.clientRoleName ?? "No role"}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        <Badge
                          variant={
                            member.status === "ACTIVE" ? "default" : "secondary"
                          }
                          className="text-xs"
                        >
                          {member.status}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        <Button
                          variant={
                            selectedUserId === member.id ? "default" : "outline"
                          }
                          size="sm"
                          onClick={() => handleSelectUser(member.id)}
                        >
                          {selectedUserId === member.id ? "Selected" : "Manage"}
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          ) : (
            <p className="text-sm text-muted-foreground py-4 text-center">
              No team members found.
            </p>
          )}
        </CardContent>
      </Card>

      {/* Permission Overrides */}
      {selectedUserId && (
        <Card>
          <CardHeader>
            <CardTitle>
              Permission Overrides
              {selectedUser && (
                <span className="text-muted-foreground font-normal ml-2">
                  for {selectedUser.firstName} {selectedUser.lastName}
                </span>
              )}
            </CardTitle>
            <p className="text-sm text-muted-foreground">
              Toggle permissions off to restrict access. You can only restrict
              permissions, not grant new ones.
            </p>
          </CardHeader>
          <CardContent>
            {permsLoading ? (
              <div className="flex items-center justify-center py-8">
                <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
                <span className="ml-2 text-sm text-muted-foreground">
                  Loading permissions...
                </span>
              </div>
            ) : (
              <div className="space-y-4">
                {Array.from(permsByCategory.entries()).map(
                  ([category, perms]) => (
                    <div
                      key={category}
                      className="rounded-xl border border-border p-4 space-y-3"
                    >
                      <h5 className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                        {category.replace(/_/g, " ")}
                      </h5>
                      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                        {perms.map((perm) => {
                          const isEnabled = getEffective(perm.permissionKey);
                          const hasOverride =
                            perm.permissionKey in localOverrides;
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
                                  togglePerm(perm.permissionKey)
                                }
                              />
                            </div>
                          );
                        })}
                      </div>
                    </div>
                  ),
                )}

                <div className="flex justify-end pt-2">
                  <Button
                    onClick={handleSave}
                    disabled={!overridesDirty || updatePerms.isPending}
                    className="gap-2"
                  >
                    {updatePerms.isPending ? (
                      <Loader2 className="h-4 w-4 animate-spin" />
                    ) : (
                      <Save className="h-4 w-4" />
                    )}
                    Save User Permissions
                  </Button>
                </div>
              </div>
            )}
          </CardContent>
        </Card>
      )}
    </div>
  );
}

export default TeamManagementPage;

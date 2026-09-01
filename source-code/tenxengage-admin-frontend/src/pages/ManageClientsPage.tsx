import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Plus, Search, Pencil, Ban, SlidersHorizontal } from "lucide-react";
import { toast } from "sonner";

import { PageBanner } from "@/components/PageBanner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";

import {
  useClients,
  useCreateClient,
  useUpdateClient,
} from "@/hooks/useClientApi";
import { formatDate } from "@/utils/formatters";

import type {
  ClientResponse,
  ClientStatus,
  SubscriptionTier,
  CreateClientRequest,
  UpdateClientRequest,
} from "@/types/client.types";

// -- Badge helpers --

const STATUS_BADGE_CLASSES: Record<ClientStatus, string> = {
  ACTIVE: "bg-green-100 text-green-800 hover:bg-green-100",
  INACTIVE: "bg-gray-100 text-gray-800 hover:bg-gray-100",
  SUSPENDED: "bg-red-100 text-red-800 hover:bg-red-100",
  TRIAL: "bg-amber-100 text-amber-800 hover:bg-amber-100",
};

const TIER_BADGE_CLASSES: Record<SubscriptionTier, string> = {
  STARTER: "bg-slate-100 text-slate-800 hover:bg-slate-100",
  PROFESSIONAL: "bg-blue-100 text-blue-800 hover:bg-blue-100",
  ENTERPRISE: "bg-purple-100 text-purple-800 hover:bg-purple-100",
};

// -- Form state type --

interface ClientFormState {
  name: string;
  subdomain: string;
  subscriptionTier: SubscriptionTier;
  status: ClientStatus;
  logoUrl: string;
}

const EMPTY_FORM: ClientFormState = {
  name: "",
  subdomain: "",
  subscriptionTier: "STARTER",
  status: "ACTIVE",
  logoUrl: "",
};

// -- Component --

export default function ManageClientsPage() {
  const navigate = useNavigate();

  // Search & filter state
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<string>("ALL");
  const [tierFilter, setTierFilter] = useState<string>("ALL");
  const [page, setPage] = useState(0);

  // Sheet state
  const [sheetOpen, setSheetOpen] = useState(false);
  const [editingClient, setEditingClient] = useState<ClientResponse | null>(
    null,
  );
  const [form, setForm] = useState<ClientFormState>(EMPTY_FORM);

  // Queries & mutations
  const { data: clientsPage, isLoading } = useClients({
    page,
    size: 10,
    search: search || undefined,
    status: statusFilter !== "ALL" ? statusFilter : undefined,
    subscriptionTier: tierFilter !== "ALL" ? tierFilter : undefined,
  });

  const createClient = useCreateClient();
  const updateClient = useUpdateClient();

  // Handlers

  function openCreateSheet() {
    setEditingClient(null);
    setForm(EMPTY_FORM);
    setSheetOpen(true);
  }

  function openEditSheet(client: ClientResponse) {
    setEditingClient(client);
    setForm({
      name: client.name,
      subdomain: client.subdomain,
      subscriptionTier: client.subscriptionTier,
      status: client.status,
      logoUrl: client.logoUrl ?? "",
    });
    setSheetOpen(true);
  }

  async function handleDeactivate(client: ClientResponse) {
    try {
      await updateClient.mutateAsync({
        id: client.id,
        data: { status: "INACTIVE" },
      });
      toast.success(`${client.name} has been deactivated`);
    } catch {
      toast.error("Failed to deactivate client");
    }
  }

  async function handleSave() {
    if (!form.name.trim()) {
      toast.error("Name is required");
      return;
    }

    try {
      if (editingClient) {
        const data: UpdateClientRequest = {
          name: form.name,
          status: form.status,
          subscriptionTier: form.subscriptionTier,
          logoUrl: form.logoUrl || undefined,
        };
        await updateClient.mutateAsync({ id: editingClient.id, data });
        toast.success(`${form.name} updated successfully`);
      } else {
        if (!form.subdomain.trim()) {
          toast.error("Subdomain is required");
          return;
        }
        const data: CreateClientRequest = {
          name: form.name,
          subdomain: form.subdomain,
          subscriptionTier: form.subscriptionTier,
          status: form.status,
          logoUrl: form.logoUrl || undefined,
        };
        await createClient.mutateAsync(data);
        toast.success(`${form.name} created successfully`);
      }
      setSheetOpen(false);
    } catch {
      toast.error(
        editingClient ? "Failed to update client" : "Failed to create client",
      );
    }
  }

  const isSaving = createClient.isPending || updateClient.isPending;
  const clients = clientsPage?.data ?? [];

  // Render

  return (
    <div className="flex flex-col gap-6">
      <PageBanner
        title="Manage Clients"
        subtitle="View and manage all platform clients"
        theme="default"
        actions={
          <Button onClick={openCreateSheet}>
            <Plus className="mr-2 h-4 w-4" />
            Create Client
          </Button>
        }
      />

      {/* Search & Filters */}
      <div className="flex flex-col gap-4 px-6 sm:flex-row sm:items-center">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder="Search by name or subdomain..."
            className="pl-9"
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0);
            }}
          />
        </div>

        <Select
          value={statusFilter}
          onValueChange={(v) => {
            setStatusFilter(v);
            setPage(0);
          }}
        >
          <SelectTrigger className="w-[160px]">
            <SelectValue placeholder="Status" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">All Statuses</SelectItem>
            <SelectItem value="ACTIVE">Active</SelectItem>
            <SelectItem value="INACTIVE">Inactive</SelectItem>
            <SelectItem value="SUSPENDED">Suspended</SelectItem>
            <SelectItem value="TRIAL">Trial</SelectItem>
          </SelectContent>
        </Select>

        <Select
          value={tierFilter}
          onValueChange={(v) => {
            setTierFilter(v);
            setPage(0);
          }}
        >
          <SelectTrigger className="w-[180px]">
            <SelectValue placeholder="Tier" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">All Tiers</SelectItem>
            <SelectItem value="STARTER">Starter</SelectItem>
            <SelectItem value="PROFESSIONAL">Professional</SelectItem>
            <SelectItem value="ENTERPRISE">Enterprise</SelectItem>
          </SelectContent>
        </Select>
      </div>

      {/* Client Table */}
      <div className="px-6">
        <div className="rounded-lg border bg-card">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-muted/50">
                  <th className="px-4 py-3 text-left font-medium text-muted-foreground">
                    Name
                  </th>
                  <th className="px-4 py-3 text-left font-medium text-muted-foreground">
                    Subdomain
                  </th>
                  <th className="px-4 py-3 text-left font-medium text-muted-foreground">
                    Status
                  </th>
                  <th className="px-4 py-3 text-left font-medium text-muted-foreground">
                    Tier
                  </th>
                  <th className="px-4 py-3 text-left font-medium text-muted-foreground">
                    Created
                  </th>
                  <th className="px-4 py-3 text-right font-medium text-muted-foreground">
                    Actions
                  </th>
                </tr>
              </thead>
              <tbody>
                {isLoading ? (
                  <tr>
                    <td
                      colSpan={6}
                      className="px-4 py-8 text-center text-muted-foreground"
                    >
                      Loading clients...
                    </td>
                  </tr>
                ) : clients.length === 0 ? (
                  <tr>
                    <td
                      colSpan={6}
                      className="px-4 py-8 text-center text-muted-foreground"
                    >
                      No clients found
                    </td>
                  </tr>
                ) : (
                  clients.map((client) => (
                    <tr
                      key={client.id}
                      className="border-b last:border-b-0 hover:bg-muted/30 transition-colors"
                    >
                      <td className="px-4 py-3">
                        <Link
                          to={`/clients/${client.id}`}
                          className="font-medium text-primary hover:underline"
                        >
                          {client.name}
                        </Link>
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">
                        {client.subdomain}
                      </td>
                      <td className="px-4 py-3">
                        <Badge
                          variant="secondary"
                          className={STATUS_BADGE_CLASSES[client.status]}
                        >
                          {client.status}
                        </Badge>
                      </td>
                      <td className="px-4 py-3">
                        <Badge
                          variant="secondary"
                          className={
                            TIER_BADGE_CLASSES[client.subscriptionTier]
                          }
                        >
                          {client.subscriptionTier}
                        </Badge>
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">
                        {formatDate(client.createdAt)}
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex items-center justify-end gap-2">
                          <Button
                            variant="ghost"
                            size="icon"
                            onClick={() => navigate(`/clients/${client.id}`)}
                            title="Manage features"
                          >
                            <SlidersHorizontal className="h-4 w-4" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="icon"
                            onClick={() => openEditSheet(client)}
                            title="Edit client"
                          >
                            <Pencil className="h-4 w-4" />
                          </Button>
                          {client.status === "ACTIVE" && (
                            <Button
                              variant="ghost"
                              size="icon"
                              onClick={() => handleDeactivate(client)}
                              title="Deactivate client"
                            >
                              <Ban className="h-4 w-4 text-destructive" />
                            </Button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>

          {/* Pagination */}
          {clientsPage && clientsPage.totalPages > 1 && (
            <div className="flex items-center justify-between border-t px-4 py-3">
              <p className="text-sm text-muted-foreground">
                Page {clientsPage.page + 1} of {clientsPage.totalPages}
              </p>
              <div className="flex gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={!clientsPage.hasPrevious}
                  onClick={() => setPage((p) => p - 1)}
                >
                  Previous
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={!clientsPage.hasNext}
                  onClick={() => setPage((p) => p + 1)}
                >
                  Next
                </Button>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Create / Edit Sheet */}
      <Sheet open={sheetOpen} onOpenChange={setSheetOpen}>
        <SheetContent className="sm:max-w-md">
          <SheetHeader>
            <SheetTitle>
              {editingClient ? "Edit Client" : "Create Client"}
            </SheetTitle>
          </SheetHeader>

          <div className="mt-6 flex flex-col gap-5">
            {/* Name */}
            <div className="flex flex-col gap-2">
              <Label htmlFor="client-name">
                Name <span className="text-destructive">*</span>
              </Label>
              <Input
                id="client-name"
                placeholder="Acme Corp"
                value={form.name}
                onChange={(e) =>
                  setForm((prev) => ({ ...prev, name: e.target.value }))
                }
              />
            </div>

            {/* Subdomain */}
            <div className="flex flex-col gap-2">
              <Label htmlFor="client-subdomain">
                Subdomain <span className="text-destructive">*</span>
              </Label>
              <Input
                id="client-subdomain"
                placeholder="acme"
                value={form.subdomain}
                disabled={!!editingClient}
                onChange={(e) =>
                  setForm((prev) => ({ ...prev, subdomain: e.target.value }))
                }
              />
              {editingClient && (
                <p className="text-xs text-muted-foreground">
                  Subdomain cannot be changed after creation
                </p>
              )}
            </div>

            {/* Subscription Tier */}
            <div className="flex flex-col gap-2">
              <Label>Subscription Tier</Label>
              <Select
                value={form.subscriptionTier}
                onValueChange={(v) =>
                  setForm((prev) => ({
                    ...prev,
                    subscriptionTier: v as SubscriptionTier,
                  }))
                }
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="STARTER">Starter</SelectItem>
                  <SelectItem value="PROFESSIONAL">Professional</SelectItem>
                  <SelectItem value="ENTERPRISE">Enterprise</SelectItem>
                </SelectContent>
              </Select>
            </div>

            {/* Status */}
            <div className="flex flex-col gap-2">
              <Label>Status</Label>
              <Select
                value={form.status}
                onValueChange={(v) =>
                  setForm((prev) => ({
                    ...prev,
                    status: v as ClientStatus,
                  }))
                }
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ACTIVE">Active</SelectItem>
                  <SelectItem value="INACTIVE">Inactive</SelectItem>
                  <SelectItem value="SUSPENDED">Suspended</SelectItem>
                  <SelectItem value="TRIAL">Trial</SelectItem>
                </SelectContent>
              </Select>
            </div>

            {/* Logo URL */}
            <div className="flex flex-col gap-2">
              <Label htmlFor="client-logo">Logo URL</Label>
              <Input
                id="client-logo"
                placeholder="https://example.com/logo.png"
                value={form.logoUrl}
                onChange={(e) =>
                  setForm((prev) => ({ ...prev, logoUrl: e.target.value }))
                }
              />
            </div>

            {/* Save */}
            <Button
              className="mt-2"
              onClick={handleSave}
              disabled={isSaving}
            >
              {isSaving ? "Saving..." : "Save"}
            </Button>
          </div>
        </SheetContent>
      </Sheet>
    </div>
  );
}

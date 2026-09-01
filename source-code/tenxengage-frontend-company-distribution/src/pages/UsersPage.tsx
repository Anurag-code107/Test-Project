import { useState } from "react";
import type { ColumnDef } from "@tanstack/react-table";
import { useUsers } from "@/hooks/useApi";
import type { User } from "@/types/user.types";
import { Button } from "@/components/ui/button";
import DataTable from "@/components/DataTable";
import { formatDate } from "@/utils/formatters";
import { Plus } from "lucide-react";

const columns: ColumnDef<User, unknown>[] = [
  {
    accessorKey: "name",
    header: "Name",
    cell: ({ row }) => {
      const user = row.original;
      return (
        <span className="font-medium">
          {user.firstName} {user.lastName}
        </span>
      );
    },
  },
  {
    accessorKey: "email",
    header: "Email",
  },
  {
    accessorKey: "status",
    header: "Status",
    cell: ({ row }) => {
      const status = row.original.status;
      const colorMap: Record<string, string> = {
        ACTIVE: "bg-green-100 text-green-800",
        INACTIVE: "bg-gray-100 text-gray-800",
        SUSPENDED: "bg-red-100 text-red-800",
        PENDING: "bg-yellow-100 text-yellow-800",
      };
      return (
        <span
          className={`inline-flex rounded-full px-2 py-1 text-xs font-medium ${colorMap[status] ?? "bg-gray-100 text-gray-800"}`}
        >
          {status}
        </span>
      );
    },
  },
  {
    accessorKey: "roles",
    header: "Roles",
    cell: ({ row }) => {
      const roles = row.original.roles;
      return (
        <div className="flex flex-wrap gap-1">
          {roles.map((role) => (
            <span
              key={role.id}
              className="inline-flex rounded-md bg-secondary px-2 py-0.5 text-xs"
            >
              {role.name}
            </span>
          ))}
        </div>
      );
    },
  },
  {
    accessorKey: "createdAt",
    header: "Created At",
    cell: ({ row }) => formatDate(row.original.createdAt),
  },
];

function UsersPage() {
  const [page, setPage] = useState(0);
  const { data, isLoading } = useUsers({ page, pageSize: 10 });

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-3xl font-bold tracking-tight">Users</h2>
          <p className="text-muted-foreground">
            Manage platform users and their roles
          </p>
        </div>
        <Button>
          <Plus className="mr-2 h-4 w-4" />
          Create User
        </Button>
      </div>

      <DataTable
        columns={columns}
        data={data?.data ?? []}
        page={data?.page ?? page}
        totalPages={data?.totalPages ?? 1}
        totalElements={data?.totalElements ?? 0}
        onPageChange={setPage}
        isLoading={isLoading}
      />
    </div>
  );
}

export default UsersPage;

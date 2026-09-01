import api from "@/lib/axios";
import type {
  ApiResponse,
  PaginatedResponse,
  PaginationParams,
} from "@/types/api.types";
import type {
  User,
  CreateUserRequest,
  UpdateUserRequest,
} from "@/types/user.types";

export async function getUsers(
  params?: PaginationParams,
): Promise<PaginatedResponse<User>> {
  const response = await api.get<ApiResponse<PaginatedResponse<User>>>(
    "/users",
    { params },
  );
  return response.data.data;
}

export async function getUserById(id: string): Promise<User> {
  const response = await api.get<ApiResponse<User>>(`/users/${id}`);
  return response.data.data;
}

export async function createUser(data: CreateUserRequest): Promise<User> {
  const response = await api.post<ApiResponse<User>>("/users", data);
  return response.data.data;
}

export async function updateUser(
  id: string,
  data: UpdateUserRequest,
): Promise<User> {
  const response = await api.put<ApiResponse<User>>(`/users/${id}`, data);
  return response.data.data;
}

export async function deleteUser(id: string): Promise<void> {
  await api.delete(`/users/${id}`);
}

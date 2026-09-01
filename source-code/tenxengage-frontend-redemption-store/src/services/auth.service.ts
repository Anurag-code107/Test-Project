import api from "@/lib/axios";
import type { LoginRequest, LoginResponse, AuthUser } from "@/types/auth.types";

export async function login(data: LoginRequest): Promise<LoginResponse> {
  const response = await api.post<LoginResponse>("/auth/login", data);
  return response.data;
}

export async function refresh(): Promise<LoginResponse> {
  const response = await api.post<LoginResponse>("/auth/refresh");
  return response.data;
}

export async function logout(): Promise<void> {
  await api.post("/auth/logout");
}

export async function getMe(): Promise<AuthUser> {
  const response = await api.get<AuthUser>("/auth/me");
  return response.data;
}

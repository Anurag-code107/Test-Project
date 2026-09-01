import api from "@/lib/axios";
import type { ApiResponse } from "@/types/api.types";
import type { LmsCourse } from "@/types/incentive.types";

export async function getLmsCourses(params?: {
  category?: string;
  search?: string;
}): Promise<LmsCourse[]> {
  const response = await api.get<ApiResponse<LmsCourse[]>>("/lms-courses", {
    params,
  });
  return response.data.data;
}

export async function getLmsCourseCategories(): Promise<string[]> {
  const response = await api.get<ApiResponse<string[]>>(
    "/lms-courses/categories",
  );
  return response.data.data;
}

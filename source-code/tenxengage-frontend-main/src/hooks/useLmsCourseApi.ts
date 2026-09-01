import { useQuery } from "@tanstack/react-query";
import {
  getLmsCourses,
  getLmsCourseCategories,
} from "@/services/lmsCourse.service";

export function useLmsCourses(params?: { category?: string; search?: string }) {
  return useQuery({
    queryKey: ["lms-courses", params?.category, params?.search],
    queryFn: () => getLmsCourses(params),
  });
}

export function useLmsCourseCategories() {
  return useQuery({
    queryKey: ["lms-course-categories"],
    queryFn: getLmsCourseCategories,
  });
}

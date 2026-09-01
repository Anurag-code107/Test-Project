import api from "@/lib/axios";
import type { ApiResponse } from "@/types/api.types";
import type {
  ProductSKU,
  CreateProductRequest,
  ProductUploadResponse,
} from "@/types/incentive.types";

export async function getProducts(params?: {
  category?: string;
  search?: string;
}): Promise<ProductSKU[]> {
  const response = await api.get<ApiResponse<ProductSKU[]>>("/products", {
    params,
  });
  return response.data.data;
}

export async function getProductCategories(): Promise<string[]> {
  const response = await api.get<ApiResponse<string[]>>("/products/categories");
  return response.data.data;
}

export async function createProduct(
  data: CreateProductRequest,
): Promise<ProductSKU> {
  const response = await api.post<ApiResponse<ProductSKU>>("/products", data);
  return response.data.data;
}

export async function uploadProducts(
  file: File,
): Promise<ProductUploadResponse> {
  const formData = new FormData();
  formData.append("file", file);
  const response = await api.post<ApiResponse<ProductUploadResponse>>(
    "/products/upload",
    formData,
    {
      headers: { "Content-Type": "multipart/form-data" },
    },
  );
  return response.data.data;
}

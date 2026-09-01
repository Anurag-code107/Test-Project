import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  getProducts,
  getProductCategories,
  createProduct,
  uploadProducts,
} from "@/services/product.service";
import type { CreateProductRequest } from "@/types/incentive.types";

export function useProducts(params?: { category?: string; search?: string }) {
  return useQuery({
    queryKey: ["products", params?.category, params?.search],
    queryFn: () => getProducts(params),
  });
}

export function useProductCategories() {
  return useQuery({
    queryKey: ["product-categories"],
    queryFn: getProductCategories,
  });
}

export function useCreateProduct() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateProductRequest) => createProduct(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["products"] });
      queryClient.invalidateQueries({ queryKey: ["product-categories"] });
    },
  });
}

export function useUploadProducts() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (file: File) => uploadProducts(file),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["products"] });
      queryClient.invalidateQueries({ queryKey: ["product-categories"] });
    },
  });
}

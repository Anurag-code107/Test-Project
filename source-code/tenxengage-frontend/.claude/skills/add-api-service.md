# Skill: Add API Service

TRIGGER when: user asks to connect to API, fetch data, or create a service
DO NOT TRIGGER when: user is asking about backend Java services

## Steps

1. **Read the contract**: Check `contracts/endpoints/` for the API spec (endpoints, methods, request/response shapes, status codes). If no contract exists, STOP and tell the developer to add it to the contracts repo first.

2. **Create or update types**: In `src/types/`, define TypeScript interfaces that exactly match the contract models. Field names, types, and optionality must be identical.

3. **Create the service file**: In `src/services/`, create `<domain>.service.ts`:
   - Import the axios instance from `@/lib/axios`
   - One function per endpoint
   - Fully typed parameters and return values
   - Example:
     ```typescript
     export async function getUsers(params: PaginationParams): Promise<PaginatedResponse<User>> {
       const { data } = await api.get("/api/v1/users", { params });
       return data;
     }
     ```

4. **Create TanStack Query hooks**: In `src/hooks/useApi.ts` or a new domain-specific file:
   - `useQuery` for GET endpoints (with query keys including params)
   - `useMutation` for POST/PUT/DELETE (with `onSuccess` query invalidation)
   - Example:
     ```typescript
     export function useUsers(params: PaginationParams) {
       return useQuery({
         queryKey: ["users", params],
         queryFn: () => getUsers(params),
       });
     }
     ```

5. **Wire to components**: Import and use hooks in page or component files

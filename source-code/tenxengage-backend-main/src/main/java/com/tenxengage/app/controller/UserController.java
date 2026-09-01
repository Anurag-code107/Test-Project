package com.tenxengage.app.controller;

import com.tenxengage.app.audit.Audited;
import com.tenxengage.app.dto.request.CreateUserRequest;
import com.tenxengage.app.dto.request.UpdateUserRequest;
import com.tenxengage.app.dto.response.UserResponse;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "User management endpoints")
@Validated
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "List users", description = "Get a paginated list of users with optional search, company filter, or internal-only filter")
    @RequiresPermission("action.users.view")
    public ResponseEntity<Page<UserResponse>> getUsers(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @Parameter(description = "Search term for email, first name, or last name")
            @RequestParam(required = false) @Size(max = 255) String search,
            @Parameter(description = "Filter by partner company ID")
            @RequestParam(required = false) UUID partnerCompanyId,
            @Parameter(description = "If true, return only internal users (no partner company)")
            @RequestParam(required = false) Boolean internal) {
        Page<UserResponse> users = userService.getUsers(pageable, search, partnerCompanyId, internal);
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID", description = "Get a specific user by their UUID")
    @RequiresPermission("action.users.view")
    public ResponseEntity<UserResponse> getUserById(
            @PathVariable UUID id) {
        UserResponse user = userService.getUserById(id);
        return ResponseEntity.ok(user);
    }

    @PostMapping
    @Operation(summary = "Create user", description = "Create a new user account")
    @RequiresPermission("action.users.create")
    @Audited(action = "Created", resourceType = "USER", resourceName = "#result.body.firstName + ' ' + #result.body.lastName", resourceId = "#result.body.id.toString()")
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody CreateUserRequest request) {
        UserResponse user = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update user", description = "Update an existing user")
    @RequiresPermission("action.users.edit")
    @Audited(action = "Edited", resourceType = "USER", resourceName = "#result.body.firstName + ' ' + #result.body.lastName", resourceId = "#result.body.id.toString()")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request) {
        UserResponse user = userService.updateUser(id, request);
        return ResponseEntity.ok(user);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete user", description = "Delete a user by ID")
    @RequiresPermission("action.users.delete")
    @Audited(action = "Deleted", resourceType = "USER", resourceId = "#id.toString()")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}

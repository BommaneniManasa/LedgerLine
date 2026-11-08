package com.snovasys.ledgerline.web;

import com.snovasys.ledgerline.security.AuthUser;
import com.snovasys.ledgerline.service.AccountService;
import com.snovasys.ledgerline.service.AdminService;
import com.snovasys.ledgerline.web.dto.AccountDtos.AccountAdminView;
import com.snovasys.ledgerline.web.dto.AccountDtos.CreateAccountRequest;
import com.snovasys.ledgerline.web.dto.AccountDtos.StatusRequest;
import com.snovasys.ledgerline.web.dto.AdminDtos.CreateUserRequest;
import com.snovasys.ledgerline.web.dto.AdminDtos.UserRoleRequest;
import com.snovasys.ledgerline.web.dto.AdminDtos.UserStatusRequest;
import com.snovasys.ledgerline.web.dto.PageResponse;
import com.snovasys.ledgerline.web.dto.UserDtos.UserView;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** ADMIN only (enforced in SecurityConfig: /api/admin/** requires ROLE_ADMIN). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final AccountService accountService;

    public AdminController(AdminService adminService, AccountService accountService) {
        this.adminService = adminService;
        this.accountService = accountService;
    }

    @GetMapping("/users")
    public PageResponse<UserView> users(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return adminService.listUsers(Paging.page(page), Paging.size(size));
    }

    @PostMapping("/users")
    public ResponseEntity<UserView> createUser(@AuthenticationPrincipal AuthUser admin,
                                               @Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createUser(admin, request));
    }

    @PatchMapping("/users/{id}/status")
    public UserView setStatus(@AuthenticationPrincipal AuthUser admin, @PathVariable UUID id,
                              @Valid @RequestBody UserStatusRequest request) {
        return adminService.setUserStatus(admin, id, request.status());
    }

    @PatchMapping("/users/{id}/role")
    public UserView setRole(@AuthenticationPrincipal AuthUser admin, @PathVariable UUID id,
                            @Valid @RequestBody UserRoleRequest request) {
        return adminService.setUserRole(admin, id, request.role());
    }

    @PostMapping("/users/{id}/accounts")
    public ResponseEntity<AccountAdminView> createAccount(@AuthenticationPrincipal AuthUser admin,
                                                          @PathVariable UUID id,
                                                          @Valid @RequestBody CreateAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminService.createAccountForUser(admin, id, request.currency()));
    }

    @GetMapping("/accounts")
    public PageResponse<AccountAdminView> accounts(@RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return adminService.listAccounts(Paging.page(page), Paging.size(size));
    }

    @PatchMapping("/accounts/{id}/status")
    public AccountAdminView setAccountStatus(@AuthenticationPrincipal AuthUser admin, @PathVariable UUID id,
                                             @Valid @RequestBody StatusRequest request) {
        return AccountAdminView.from(accountService.setStatus(admin, id, request.status()));
    }
}

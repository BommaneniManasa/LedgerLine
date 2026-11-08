package com.snovasys.ledgerline.web;

import com.snovasys.ledgerline.domain.Account;
import com.snovasys.ledgerline.security.AuthUser;
import com.snovasys.ledgerline.service.AccountService;
import com.snovasys.ledgerline.service.AuditActor;
import com.snovasys.ledgerline.web.dto.AccountDtos.AccountView;
import com.snovasys.ledgerline.web.dto.AccountDtos.CreateAccountRequest;
import com.snovasys.ledgerline.web.dto.AccountDtos.LedgerEntryView;
import com.snovasys.ledgerline.web.dto.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** CUSTOMER only. The owner is always the logged-in user; the request never contains a user id. */
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public List<AccountView> list(@AuthenticationPrincipal AuthUser user) {
        return accountService.listOwned(user).stream().map(AccountView::from).toList();
    }

    @PostMapping
    public ResponseEntity<AccountView> create(@AuthenticationPrincipal AuthUser user,
                                              @Valid @RequestBody CreateAccountRequest request) {
        Account account = accountService.createAccount(user.id(), request.currency(), AuditActor.of(user));
        return ResponseEntity.status(HttpStatus.CREATED).body(AccountView.from(account));
    }

    @GetMapping("/{id}")
    public AccountView get(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        return AccountView.from(accountService.requireOwned(user, id));
    }

    @GetMapping("/{id}/ledger")
    public PageResponse<LedgerEntryView> ledger(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return accountService.ledgerFor(user, id, Paging.page(page), Paging.size(size));
    }
}

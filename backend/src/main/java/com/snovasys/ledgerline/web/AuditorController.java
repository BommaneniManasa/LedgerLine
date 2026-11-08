package com.snovasys.ledgerline.web;

import com.snovasys.ledgerline.domain.LedgerEntry;
import com.snovasys.ledgerline.repository.AccountRepository;
import com.snovasys.ledgerline.repository.LedgerEntryRepository;
import com.snovasys.ledgerline.service.LedgerIntegrityService;
import com.snovasys.ledgerline.service.TransactionService;
import com.snovasys.ledgerline.web.dto.AccountDtos.AccountAdminView;
import com.snovasys.ledgerline.web.dto.AccountDtos.LedgerEntryView;
import com.snovasys.ledgerline.web.dto.AdminDtos.IntegrityReport;
import com.snovasys.ledgerline.web.dto.PageResponse;
import com.snovasys.ledgerline.web.dto.TransactionDtos.TransactionView;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** AUDITOR only, and strictly read-only: this controller contains GET endpoints only. */
@RestController
@RequestMapping("/api/auditor")
public class AuditorController {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final TransactionService transactionService;
    private final LedgerIntegrityService integrityService;

    public AuditorController(AccountRepository accountRepository, LedgerEntryRepository ledgerEntryRepository,
                             TransactionService transactionService, LedgerIntegrityService integrityService) {
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.transactionService = transactionService;
        this.integrityService = integrityService;
    }

    @GetMapping("/accounts")
    @Transactional(readOnly = true)
    public PageResponse<AccountAdminView> accounts(@RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(accountRepository.findAll(
                PageRequest.of(Paging.page(page), Paging.size(size), Sort.by(Sort.Direction.DESC, "createdAt"))),
                AccountAdminView::from);
    }

    @GetMapping("/transactions")
    public PageResponse<TransactionView> transactions(@RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return transactionService.listAll(Paging.page(page), Paging.size(size));
    }

    @GetMapping("/transactions/{id}/ledger")
    public List<LedgerEntryView> ledger(@PathVariable UUID id) {
        List<LedgerEntry> entries = ledgerEntryRepository.findByTransactionIdOrderByEntryTypeAsc(id);
        return entries.stream().map(LedgerEntryView::from).toList();
    }

    @GetMapping("/ledger-integrity")
    public IntegrityReport integrity() {
        return integrityService.check();
    }
}

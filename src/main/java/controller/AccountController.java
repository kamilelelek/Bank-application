package controller;

import dto.account.AccountResponse;
import dto.account.CreateAccountRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import service.AccountService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/account")
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/create")
    public ResponseEntity<AccountResponse> createAccount(@RequestBody CreateAccountRequest request,
                                                         @AuthenticationPrincipal UserDetails userDetails) {
        String email = userDetails.getUsername();
        return ResponseEntity.status(201).body(accountService.createAccount(request, email));
    }
    @GetMapping("/my")
    public ResponseEntity<List<AccountResponse>> getAllMyAccounts(@AuthenticationPrincipal UserDetails userDetails){
        return ResponseEntity.status(200).body(accountService.getMyAccounts(userDetails.getUsername()));
    }
    @GetMapping("/details/{id}")
    public ResponseEntity<AccountResponse> getDetails(@PathVariable UUID id,
                                                      @AuthenticationPrincipal UserDetails userDetails){
        return ResponseEntity.status(200).body(accountService.getAccountById(id,userDetails.getUsername()));
    }
}

package controller;

import dto.transaction.TransactionResponse;
import dto.transaction.TransferRequest;
import dto.transaction.WithdrawalRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import service.TransactionService;

@RestController
@RequestMapping("/transactions")
public class TransactionController {
    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/withdraw")
    public ResponseEntity<TransactionResponse> withdraw(@RequestBody WithdrawalRequest request,
                                                        @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.status(201).body(transactionService.withdraw(request, userDetails.getUsername()));
    }

    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> transfer(@RequestBody TransferRequest request,
                                                        @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.status(201).body(transactionService.transfer(request, userDetails.getUsername()));
    }
}

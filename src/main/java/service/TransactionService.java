package service;

import dto.transaction.TransactionResponse;
import dto.transaction.WithdrawalRequest;
import jakarta.transaction.Transactional;
import model.*;
import org.springframework.stereotype.Service;
import repository.BankAccountRepository;
import repository.TransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final BankAccountRepository bankAccountRepository;

    public TransactionService(TransactionRepository transactionRepository, BankAccountRepository bankAccountRepository) {
        this.transactionRepository = transactionRepository;
        this.bankAccountRepository = bankAccountRepository;
    }

    @Transactional
    public TransactionResponse withdraw(WithdrawalRequest request, String email) {
        BankAccount account = bankAccountRepository
                .findByAccountNumber(request.sourceAccountNumber())
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        if (!account.getOwner().getEmail().equals(email)) {
            throw new IllegalArgumentException("Access denied");
        }
        if (!account.getStatus().equals(AccountStatus.ACTIVE)) {
            throw new IllegalArgumentException("Account is not active");
        }
        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (account.getBalance().compareTo(request.amount()) < 0) {
            throw new IllegalArgumentException("Insufficient funds");
        }

        account.setBalance(account.getBalance().subtract(request.amount()));
        bankAccountRepository.save(account);

        Transaction transaction = Transaction.builder()
                .referenceId(java.util.UUID.randomUUID().toString())
                .amount(request.amount())
                .currency(request.currency())
                .title(request.title())
                .type(TransactionType.WITHDRAWAL)
                .status(TransactionStatus.COMPLETED)
                .sourceAccount(account)
                .completedAt(LocalDateTime.now())
                .build();

        transactionRepository.save(transaction);

        return new TransactionResponse(
                transaction.getId(),
                transaction.getReferenceId(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getTitle(),
                transaction.getType(),
                transaction.getStatus(),
                account.getAccountNumber(),
                null,
                transaction.getCreatedAt(),
                transaction.getCompletedAt()
        );
    }

}

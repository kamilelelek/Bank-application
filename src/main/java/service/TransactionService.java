package service;

import dto.transaction.TransactionResponse;
import dto.transaction.TransferRequest;
import dto.transaction.WithdrawalRequest;
import exception.*;
import jakarta.transaction.Transactional;
import model.*;
import org.springframework.stereotype.Service;
import repository.BankAccountRepository;
import repository.TransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
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
                .orElseThrow(() -> new AccountNotFoundException("Account not found"));

        if (!account.getOwner().getEmail().equals(email)) {
            throw new UnauthorizedAccountAccessException("Access denied");
        }
        if (!account.getStatus().equals(AccountStatus.ACTIVE)) {
            throw new AccountStatusException("Account is not active");
        }
        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionException("Amount must be greater than zero");
        }
        if (account.getBalance().compareTo(request.amount()) < 0) {
            throw new InsufficientFundsException("Insufficient funds");
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
                transaction.getTitle(),
                transaction.getType(),
                transaction.getStatus(),
                account.getAccountNumber(),
                null,
                transaction.getCreatedAt(),
                transaction.getCompletedAt()
        );
    }
    @Transactional
    public TransactionResponse transfer(TransferRequest request, String email) {
        BankAccount account = bankAccountRepository
                .findByAccountNumber(request.sourceAccountNumber())
                .orElseThrow(() -> new AccountNotFoundException("Account not found"));
        BankAccount targetAccount= bankAccountRepository.findByAccountNumber(request.targetAccountNumber())
                .orElseThrow(()-> new AccountNotFoundException("Account not found"));

        if (!account.getOwner().getEmail().equals(email)) {
            throw new UnauthorizedAccountAccessException("Access denied");
        }
        if (!account.getStatus().equals(AccountStatus.ACTIVE)|| !targetAccount.getStatus().equals(AccountStatus.ACTIVE)) {
            throw new AccountStatusException("Account is not active");
        }
        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionException("Amount must be greater than zero");
        }
        if (account.getBalance().compareTo(request.amount()) < 0) {
            throw new InsufficientFundsException("Insufficient funds");
        }
        if(Objects.equals(account.getAccountNumber(), targetAccount.getAccountNumber())){
            throw new IllegalArgumentException("Source account number is the same like target account");
        }
        account.setBalance(account.getBalance().subtract(request.amount()));
        targetAccount.setBalance(targetAccount.getBalance().add(request.amount()));

        Transaction transaction = Transaction.builder()
                .referenceId(java.util.UUID.randomUUID().toString())
                .amount(request.amount())
                .title(request.title())
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.COMPLETED)
                .sourceAccount(account)
                .completedAt(LocalDateTime.now())
                .build();
        transactionRepository.save(transaction);

        return new TransactionResponse(
                transaction.getId(),
                transaction.getReferenceId(),
                transaction.getAmount(),
                transaction.getTitle(),
                transaction.getType(),
                transaction.getStatus(),
                account.getAccountNumber(),
                targetAccount.getAccountNumber(),
                transaction.getCreatedAt(),
                transaction.getCompletedAt()
        );
    }
}

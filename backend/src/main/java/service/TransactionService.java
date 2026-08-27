package service;

import dto.transaction.DepositRequest;
import dto.transaction.TransactionResponse;
import dto.transaction.TransferRequest;
import dto.transaction.WithdrawalRequest;
import jakarta.transaction.Transactional;
import model.*;
import org.springframework.stereotype.Service;
import repository.BankAccountRepository;
import repository.TransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

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

        validateAccountAccess(account, email);
        validateAmount(request.amount());
        if (account.getBalance().compareTo(request.amount()) < 0) {
            throw new IllegalArgumentException("Insufficient funds");
        }

        account.setBalance(account.getBalance().subtract(request.amount()));
        bankAccountRepository.save(account);

        Transaction transaction = Transaction.builder()
                .referenceId(UUID.randomUUID().toString())
                .amount(request.amount())
                .currency(request.currency() != null ? request.currency() : account.getCurrency())
                .title(request.title())
                .type(TransactionType.WITHDRAWAL)
                .status(TransactionStatus.COMPLETED)
                .sourceAccount(account)
                .completedAt(LocalDateTime.now())
                .build();

        transactionRepository.save(transaction);
        return toResponse(transaction);
    }

    @Transactional
    public TransactionResponse transfer(TransferRequest request, String email) {
        BankAccount sourceAccount = bankAccountRepository
                .findByAccountNumber(request.sourceAccountNumber())
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        BankAccount targetAccount = bankAccountRepository
                .findByAccountNumber(request.targetAccountNumber())
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        validateAccountAccess(sourceAccount, email);
        validateAccountActive(sourceAccount);
        validateAccountActive(targetAccount);
        validateAmount(request.amount());
        if (sourceAccount.getBalance().compareTo(request.amount()) < 0) {
            throw new IllegalArgumentException("Insufficient funds");
        }

        sourceAccount.setBalance(sourceAccount.getBalance().subtract(request.amount()));
        targetAccount.setBalance(targetAccount.getBalance().add(request.amount()));
        bankAccountRepository.save(sourceAccount);
        bankAccountRepository.save(targetAccount);

        Transaction transaction = Transaction.builder()
                .referenceId(request.referenceId() != null ? request.referenceId() : UUID.randomUUID().toString())
                .amount(request.amount())
                .currency(sourceAccount.getCurrency())
                .title(request.title())
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.COMPLETED)
                .sourceAccount(sourceAccount)
                .targetAccount(targetAccount)
                .completedAt(LocalDateTime.now())
                .build();

        transactionRepository.save(transaction);
        return toResponse(transaction);
    }

    @Transactional
    public TransactionResponse deposit(DepositRequest request, String email) {
        BankAccount account = bankAccountRepository
                .findByAccountNumber(request.targetAccountNumber())
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        validateAccountAccess(account, email);
        validateAccountActive(account);
        validateAmount(request.amount());

        account.setBalance(account.getBalance().add(request.amount()));
        bankAccountRepository.save(account);

        Transaction transaction = Transaction.builder()
                .referenceId(UUID.randomUUID().toString())
                .amount(request.amount())
                .currency(request.currency() != null ? request.currency() : account.getCurrency())
                .title(request.title())
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.COMPLETED)
                .targetAccount(account)
                .completedAt(LocalDateTime.now())
                .build();

        transactionRepository.save(transaction);
        return toResponse(transaction);
    }

    private void validateAccountAccess(BankAccount account, String email) {
        if (!account.getOwner().getEmail().equals(email)) {
            throw new IllegalArgumentException("Access denied");
        }
        validateAccountActive(account);
    }

    private void validateAccountActive(BankAccount account) {
        if (!account.getStatus().equals(AccountStatus.ACTIVE)) {
            throw new IllegalArgumentException("Account is not active");
        }
    }

    private void validateAmount(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
    }

    private TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getReferenceId(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getTitle(),
                transaction.getType(),
                transaction.getStatus(),
                transaction.getSourceAccount() != null ? transaction.getSourceAccount().getAccountNumber() : null,
                transaction.getTargetAccount() != null ? transaction.getTargetAccount().getAccountNumber() : null,
                transaction.getCreatedAt(),
                transaction.getCompletedAt()
        );
    }
}

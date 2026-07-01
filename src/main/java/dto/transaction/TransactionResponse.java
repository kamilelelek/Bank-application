package dto.transaction;

import model.TransactionStatus;
import model.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransactionResponse(UUID id,
                                  String referenceId ,
                                  BigDecimal amount,
                                  String title,
                                  TransactionType transactionType,
                                  TransactionStatus transactionStatus,
                                  String sourceAccountNumber,
                                  String targetAccountNumber,
                                  LocalDateTime createdAt,
                                  LocalDateTime completedAt) {
}
// TransactionResponse — id, referenceId, amount, currency, type, status, title, createdAt, completedAt, sourceAccountNumber, targetAccountNumber
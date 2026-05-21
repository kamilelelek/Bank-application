package dto.transaction;

import java.math.BigDecimal;

public record TransferRequest(String sourceAccountNumber, String targetAccountNumber, BigDecimal amount, String currency, String title, String referenceId) {
}

package dto.transaction;

import java.math.BigDecimal;

public record DepositRequest(String sourceAccountNumber, String targetAccountNumber, BigDecimal amount, String currency, String title) {
}

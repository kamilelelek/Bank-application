package dto.transaction;

import java.math.BigDecimal;

public record DepositRequest(String targetAccountNumber,
                             BigDecimal amount,
                             String currency,
                             String title) {
}

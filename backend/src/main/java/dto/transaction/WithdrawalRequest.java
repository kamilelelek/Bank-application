package dto.transaction;

import java.math.BigDecimal;

public record WithdrawalRequest(String sourceAccountNumber,
                                String targetAccountNumber,
                                BigDecimal amount,
                                String currency,
                                String title) {
}
//  WithdrawalRequest — sourceAccountNumber, amount, currency, title
package dto.account;

import model.AccountStatus;
import model.AccountType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record AccountResponse(UUID id,
                              String accountNumber,
                              AccountType type,
                              BigDecimal balance,
                              String currency,
                              AccountStatus status,
                              LocalDateTime createdAt)
{}

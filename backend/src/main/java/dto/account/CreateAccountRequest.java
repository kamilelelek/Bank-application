package dto.account;

import model.AccountType;

public record CreateAccountRequest(AccountType type,String currency) {
}

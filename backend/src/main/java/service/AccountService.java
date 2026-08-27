package service;

import dto.account.AccountResponse;
import dto.account.CreateAccountRequest;
import model.AccountStatus;
import model.BankAccount;
import org.springframework.stereotype.Service;
import repository.BankAccountRepository;
import repository.UserRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Service
public class AccountService {
    private final UserRepository userRepository;
    private final BankAccountRepository bankAccountRepository;
    public AccountService(BankAccountRepository bankAccountRepository, UserRepository userRepository){
        this.bankAccountRepository=bankAccountRepository;
        this.userRepository=userRepository;
    }
    public AccountResponse createAccount(CreateAccountRequest request, String email) {
        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        BankAccount bankAccount = BankAccount.builder()
                .owner(user)
                .accountNumber(generateAccountNumber())
                .type(request.type())
                .currency("PLN")
                .status(AccountStatus.ACTIVE)
                .balance(BigDecimal.ZERO)
                .build();

        bankAccountRepository.save(bankAccount);

        return new AccountResponse(
                bankAccount.getId(),
                bankAccount.getAccountNumber(),
                bankAccount.getType(),
                bankAccount.getBalance(),
                bankAccount.getCurrency(),
                bankAccount.getStatus(),
                bankAccount.getCreatedAt()
        );
    }

    private String generateAccountNumber() {
        String accountNumber = String.valueOf(Math.abs(new Random().nextLong()));
        if (bankAccountRepository.existsByAccountNumber(accountNumber)) {
            return generateAccountNumber();
        }
        return accountNumber;
    }
    public List<AccountResponse> getMyAccounts(String email){
        var user=userRepository.findByEmail(email)
                .orElseThrow(()-> new NullPointerException("User not found"));
        return bankAccountRepository.findByOwner(user).stream()
                .map(account -> new AccountResponse(
                        account.getId(),
                        account.getAccountNumber(),
                        account.getType(),
                        account.getBalance(),
                        account.getCurrency(),
                        account.getStatus(),
                        account.getCreatedAt()
                ))
                .toList();
    }
    public AccountResponse getAccountById (UUID id,String email){
        var account= bankAccountRepository.findById(id)
                .orElseThrow(()-> new NullPointerException("User not found"));
        if(!account.getOwner().getEmail().equals(email)){
            throw new IllegalArgumentException("Acces denied");
        }
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getType(),
                account.getBalance(),
                account.getCurrency(),
                account.getStatus(),
                account.getCreatedAt()
        );
    }
}

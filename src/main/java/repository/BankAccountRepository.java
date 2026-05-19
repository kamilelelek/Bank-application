package repository;

import model.BankAccount;
import model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {

    List<BankAccount> findByOwner(User owner);

    List<BankAccount> findByOwnerId(UUID ownerId);

    Optional<BankAccount> findByAccountNumber(String accountNumber);

    boolean existsByAccountNumber(String accountNumber);

    @Query("SELECT b.owner FROM BankAccount b WHERE b.id = :id")
    Optional<User> findOwnerByAccountId(@Param("id") UUID id);
}

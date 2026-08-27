package repository;

import model.Transaction;
import model.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    List<Transaction> findBySourceAccountId(UUID id);

    List<Transaction> findByTargetAccountId(UUID id);

    List<Transaction> findBySourceAccountIdOrTargetAccountId(UUID sourceId, UUID targetId);

    List<Transaction> findByStatus(TransactionStatus status);

    Optional<Transaction> findByReferenceId(String referenceId);

    boolean existsByReferenceId(String referenceId);

    @Query("SELECT t FROM Transaction t WHERE (t.sourceAccount.id = :accountId OR t.targetAccount.id = :accountId) AND t.createdAt BETWEEN :from AND :to")
    List<Transaction> findByAccountIdAndDateRange(
            @Param("accountId") UUID accountId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}

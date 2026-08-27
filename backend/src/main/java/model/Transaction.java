package model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.FetchType.LAZY;
import static jakarta.persistence.GenerationType.UUID;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = UUID)
    private java.util.UUID id;

    @Column(unique = true, nullable = false)
    private String referenceId;

    @Column(precision = 19, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(length = 3, nullable = false)
    @Builder.Default
    private String currency = "PLN";

    @Enumerated(STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Enumerated(STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    @Column(nullable = false)
    private String title;

    @Column(nullable = true)
    private String description;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "source_account_id")
    private BankAccount sourceAccount;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "target_account_id")
    private BankAccount targetAccount;

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}

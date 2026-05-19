package model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.FetchType.LAZY;
import static jakarta.persistence.GenerationType.UUID;

@Entity
@Table(name = "bank_accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankAccount {

    @Id
    @GeneratedValue(strategy = UUID)
    private java.util.UUID id;

    @Column(unique = true, length = 26, nullable = false)
    private String accountNumber;

    @Enumerated(STRING)
    @Column(nullable = false)
    private AccountType type;

    @Column(precision = 19, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(length = 3, nullable = false)
    @Builder.Default
    private String currency = "PLN";

    @Enumerated(STRING)
    @Column(nullable = false)
    private AccountStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User owner;

    @OneToMany(mappedBy = "sourceAccount", fetch = LAZY)
    private List<Transaction> outgoingTransactions;

    @OneToMany(mappedBy = "targetAccount", fetch = LAZY)
    private List<Transaction> incomingTransactions;

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}

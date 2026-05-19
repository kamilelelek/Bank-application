# 🏦 Banking API

REST API do zarządzania kontami bankowymi napisane w **Java 21 + Spring Boot 3.3**.
Umożliwia rejestrację użytkowników, tworzenie kont bankowych oraz wykonywanie operacji finansowych (przelewy, wpłaty, wypłaty).

---

## 📌 Cel aplikacji

Aplikacja symuluje backend systemu bankowego. Użytkownik może:
- Założyć konto w systemie i się zalogować (JWT)
- Otworzyć jedno lub więcej kont bankowych (rozliczeniowe, oszczędnościowe, firmowe)
- Wykonywać przelewy między kontami
- Wpłacać i wypłacać środki
- Przeglądać historię transakcji
- Zarządzać swoim profilem i hasłem
- Blokować lub zamykać konta bankowe

---

## 🛠️ Stack technologiczny

| Technologia | Wersja | Zastosowanie |
|-------------|--------|--------------|
| Java | 21 | Język programowania |
| Spring Boot | 3.3 | Framework aplikacji |
| Spring Security | 6.x | Autoryzacja i uwierzytelnianie |
| Spring Data JPA | 3.x | Warstwa dostępu do danych |
| Hibernate | 6.x | ORM |
| jjwt | 0.12.5 | Generowanie i walidacja tokenów JWT |
| H2 | runtime | Baza danych in-memory (dev) |
| PostgreSQL | 16 | Baza danych (prod) |
| Lombok | latest | Redukcja boilerplate |
| Springdoc OpenAPI | 2.5.0 | Swagger UI – dokumentacja API |
| Maven | 3.9+ | Build tool |

---

## 📁 Struktura projektu

```
src/main/java/pl/bankapi/
├── BankingApiApplication.java       # Punkt startowy aplikacji
│
├── model/                           # Encje JPA
│   ├── User.java
│   ├── BankAccount.java
│   └── Transaction.java
│
├── dto/                             # Obiekty transferu danych
│   ├── auth/
│   │   ├── RegisterRequest.java
│   │   ├── LoginRequest.java
│   │   └── AuthResponse.java
│   ├── user/
│   │   ├── UserResponse.java
│   │   ├── UpdateUserRequest.java
│   │   └── ChangePasswordRequest.java
│   ├── account/
│   │   ├── CreateAccountRequest.java
│   │   └── AccountResponse.java
│   └── transaction/
│       ├── TransferRequest.java
│       ├── DepositRequest.java
│       ├── WithdrawalRequest.java
│       └── TransactionResponse.java
│
├── repository/                      # Interfejsy Spring Data JPA
│   ├── UserRepository.java
│   ├── BankAccountRepository.java
│   └── TransactionRepository.java
│
├── service/                         # Logika biznesowa
│   ├── AuthService.java
│   ├── UserService.java
│   ├── AccountService.java
│   └── TransactionService.java
│
├── controller/                      # Kontrolery REST
│   ├── AuthController.java
│   ├── UserController.java
│   ├── AccountController.java
│   └── TransactionController.java
│
├── security/                        # JWT + Spring Security
│   ├── JwtUtil.java
│   ├── JwtAuthFilter.java
│   ├── CustomUserDetailsService.java
│   └── SecurityConfig.java
│
└── config/                          # Konfiguracja aplikacji
    ├── OpenApiConfig.java
    └── GlobalExceptionHandler.java
```

---

## 🗄️ Model danych

### `User`
Reprezentuje użytkownika systemu bankowego.

```java
UUID   id             // @Id, @GeneratedValue(UUID), klucz główny
String email          // @Column(unique = true, nullable = false)
String password       // BCrypt hash, nullable = false
String firstName      // nullable = false
String lastName       // nullable = false
String pesel          // @Column(unique = true, length = 11, nullable = false)
String phoneNumber    // nullable = true
Role   role           // @Enumerated(STRING): USER | ADMIN
boolean enabled       // default = true
LocalDateTime createdAt  // @PrePersist, ustawiany automatycznie

// Relacje
List<BankAccount> accounts  // @OneToMany(mappedBy = "owner", cascade = ALL)
```

---

### `BankAccount`
Reprezentuje konto bankowe należące do użytkownika.

```java
UUID              id             // @Id, @GeneratedValue(UUID)
String            accountNumber  // @Column(unique = true, length = 26) – format IBAN PL
AccountType       type           // @Enumerated(STRING): CHECKING | SAVINGS | BUSINESS
BigDecimal        balance        // @Column(precision = 19, scale = 2), default = 0
String            currency       // @Column(length = 3), default = "PLN"
AccountStatus     status         // @Enumerated(STRING): ACTIVE | FROZEN | CLOSED
LocalDateTime     createdAt      // @PrePersist

// Relacje
User              owner                // @ManyToOne(fetch = LAZY), @JoinColumn(name = "user_id")
List<Transaction> outgoingTransactions // @OneToMany(mappedBy = "sourceAccount")
List<Transaction> incomingTransactions // @OneToMany(mappedBy = "targetAccount")
```

---

### `Transaction`
Reprezentuje pojedynczą operację finansową.

```java
UUID              id               // @Id, @GeneratedValue(UUID)
String            referenceId      // @Column(unique = true) – UUID jako String, do idempotencji
BigDecimal        amount           // @Column(precision = 19, scale = 2, nullable = false)
String            currency         // @Column(length = 3), default = "PLN"
TransactionType   type             // @Enumerated(STRING): TRANSFER | DEPOSIT | WITHDRAWAL
TransactionStatus status           // @Enumerated(STRING): PENDING | COMPLETED | FAILED | REVERSED
String            title            // nullable = false
String            description      // nullable = true
LocalDateTime     createdAt        // @PrePersist
LocalDateTime     completedAt      // ustawiane po zakończeniu transakcji

// Relacje
BankAccount sourceAccount  // @ManyToOne(fetch = LAZY), nullable (przy DEPOSIT)
BankAccount targetAccount  // @ManyToOne(fetch = LAZY), nullable (przy WITHDRAWAL)
```

---

## 🔐 Autoryzacja

API używa **JWT Bearer Token** (HS512, ważność 24h).

1. Zarejestruj się → `POST /api/auth/register` → otrzymasz token
2. Zaloguj się → `POST /api/auth/login` → otrzymasz token
3. Do każdego chronionego endpointu dodaj nagłówek:
```
Authorization: Bearer <twój_token>
```

---

## 📡 Endpointy

### Auth – `/api/auth` (publiczne)
| Metoda | Ścieżka | Opis | Status |
|--------|---------|------|--------|
| POST | `/register` | Rejestracja nowego użytkownika | 201 |
| POST | `/login` | Logowanie, zwraca JWT | 200 |

### Użytkownik – `/api/users` (wymaga JWT)
| Metoda | Ścieżka | Opis | Status |
|--------|---------|------|--------|
| GET | `/me` | Pobierz swój profil | 200 |
| PUT | `/me` | Zaktualizuj dane (imię, telefon) | 200 |
| PUT | `/me/password` | Zmień hasło | 204 |

### Konta bankowe – `/api/accounts` (wymaga JWT)
| Metoda | Ścieżka | Opis | Status |
|--------|---------|------|--------|
| POST | `/` | Otwórz nowe konto bankowe | 201 |
| GET | `/` | Lista moich aktywnych kont | 200 |
| GET | `/{accountNumber}` | Szczegóły konta | 200 |
| PUT | `/{accountNumber}/freeze` | Zablokuj konto | 200 |
| DELETE | `/{accountNumber}` | Zamknij konto (tylko saldo = 0) | 200 |

### Transakcje – `/api/transactions` (wymaga JWT)
| Metoda | Ścieżka | Opis | Status |
|--------|---------|------|--------|
| POST | `/transfer` | Przelew między kontami | 201 |
| POST | `/deposit` | Wpłata gotówki na konto | 201 |
| POST | `/withdrawal` | Wypłata gotówki z konta | 201 |
| GET | `/history/{accountNumber}` | Historia transakcji konta | 200 |

---

## ⚙️ Uruchomienie

### Wymagania
- Java 21+
- Maven 3.9+

### Dev (H2 in-memory)
```bash
./mvnw spring-boot:run
```

Aplikacja startuje na `http://localhost:8080`

| URL | Opis |
|-----|------|
| `http://localhost:8080/swagger-ui.html` | Interaktywna dokumentacja API |
| `http://localhost:8080/h2-console` | Konsola bazy danych H2 |

### Prod (PostgreSQL)
Zmień w `application.yml`:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/bankdb
    username: postgres
    password: twoje_haslo
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
```

---

## ❌ Obsługa błędów

Wszystkie błędy zwracają JSON w formacie:
```json
{
  "status": "409",
  "error": "Conflict",
  "message": "Email jest już zajęty: jan@example.com"
}
```

| HTTP Status | Kiedy |
|-------------|-------|
| 400 | Błędne dane / walidacja nie przeszła |
| 401 | Brak lub nieważny token JWT |
| 403 | Próba dostępu do cudzego zasobu |
| 404 | Zasób nie istnieje |
| 409 | Konflikt (email/PESEL już zajęty) |

---

## 🔒 Zasady biznesowe

- Przelew możliwy tylko z konta o statusie `ACTIVE` z saldem >= kwota przelewu
- Konto `FROZEN` nie może wysyłać ani przyjmować transakcji
- Zamknięcie konta (`CLOSED`) możliwe tylko gdy saldo = 0
- Użytkownik ma dostęp wyłącznie do własnych kont (403 przy próbie cudzego)
- Każda transakcja ma unikalny `referenceId` (UUID) – zabezpieczenie przed duplikatami
- Hasła przechowywane jako hash BCrypt
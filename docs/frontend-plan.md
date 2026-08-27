# Frontend Bank Application — plan architektury i pracy

Dokument dla osoby, która będzie pisać FE. Czytaj od góry do dołu, nie przeskakuj sekcji 1 i 2.

---

## 0. Zasada nadrzędna

Frontend jest **cienki**. Nie liczy sald, nie decyduje o tym czy przelew przejdzie, nie wie
co to "wystarczające środki". Backend jest jedynym źródłem prawdy. FE ma trzy zadania:

1. zebrać dane od użytkownika i wysłać je w poprawnym kształcie,
2. pokazać to co wróciło z serwera,
3. nie zgubić się kiedy serwer zwróci błąd.

Każda walidacja po stronie FE to **wyłącznie UX** (szybszy feedback), nigdy zabezpieczenie.
Jeśli kiedykolwiek napiszesz w komponencie `if (balance < amount)` żeby zablokować przycisk —
to jest OK jako podpowiedź, ale request i tak musi polecieć na backend i backend i tak musi
umieć odmówić.

---

## 1. Stan backendu — co jest, czego brakuje

### 1.1 Co API już wystawia

| Metoda | Ścieżka | Auth | Request | Response |
|---|---|---|---|---|
| POST | `/auth/register` | nie | `RegisterRequest` | 201 `AuthResponse` |
| POST | `/auth/login` | nie | `LoginRequest` | 200 `AuthResponse` |
| POST | `/accounts/create` | tak | `CreateAccountRequest` | 201 `AccountResponse` |
| GET | `/accounts/my` | tak | — | 200 `AccountResponse[]` |
| GET | `/accounts/details/{id}` | tak | — | 200 `AccountResponse` |

Base URL: `http://localhost:8087` (z `application.properties`, `server.port=8087`).
Brak `server.servlet.context-path`, więc **nie ma prefiksu `/api`**.

Kształty (z rekordów Javy):

```
AuthResponse         { token: string, type: string }          // type === "Bearer"
RegisterRequest      { email, password, firstName, lastName, personalIdNumber, phoneNumber }
LoginRequest         { email, password }
CreateAccountRequest { type: AccountType, currency: string }
AccountResponse      { id: UUID, accountNumber, type, balance, currency, status, createdAt }
TransactionResponse  { id, referenceId, amount, currency, title, transactionType,
                       transactionStatus, sourceAccountNumber, targetAccountNumber,
                       createdAt, completedAt }
```

Enumy (Jackson serializuje je jako stringi — nazwa stałej):

```
AccountType       CHECKING | SAVINGS | BUSINESS
AccountStatus     ACTIVE | FROZEN | CLOSED
TransactionType   TRANSFER | DEPOSIT | WITHDRAWAL
TransactionStatus PENDING | COMPLETED | FAILED | CANCELLED
Role              ADMIN | USER
```

Autoryzacja: `Authorization: Bearer <token>`, token z `AuthResponse.token`, JWT ważny
24h (`jwt.expiration=86400000`). Sesje wyłączone (`STATELESS`), CSRF wyłączony.

### 1.2 Blokery — do naprawy na backendzie ZANIM FE ruszy

To nie jest lista życzeń, to rzeczy przez które FE fizycznie nie zadziała albo będzie
zgadywał. Uporządkowane wg tego jak bardzo bolą.

**B1. Brak konfiguracji CORS — FE nie wykona ani jednego requestu.**
Vite stoi na `http://localhost:5173`, backend na `:8087`. To inny origin, przeglądarka
zablokuje wszystko na preflight. W `SecurityConfig` trzeba dodać `.cors(...)` i bean
`CorsConfigurationSource` z dozwolonym originem `http://localhost:5173`, metodami
`GET/POST/PUT/DELETE/OPTIONS` i nagłówkiem `Authorization`.
*Obejście na czas developmentu:* proxy w `vite.config.ts` (opisane w §5.3) — wtedy przeglądarka
widzi jeden origin i CORS nie występuje. Ale to działa tylko w dev, więc CORS i tak trzeba dodać.

**B2. `GlobalExceptionHandler` jest pusty.**
Każdy `IllegalArgumentException` z serwisów (a jest ich mnóstwo: "Insufficient funds",
"Account not found", "Invalid email or password") wyleci jako **500 Internal Server Error**
z domyślnym bodym Spring Boota. FE nie ma jak odróżnić "za mało środków" od "padła baza".
Trzeba dodać `@ExceptionHandler` mapujące hierarchię z `exception/` na sensowne kody
(404 / 400 / 403 / 409) i zwracające `ErrorResponse`.

**B3. `ErrorResponse` ma pole `HttpServletRequest request`.**
Jackson tego nie zserializuje — poleci `InvalidDefinitionException` przy próbie zwrócenia
błędu, czyli błąd o błędzie. Zamień na `String path`. Import `HttpServletResponse` też
jest nieużywany.
Docelowy kształt, na którym FE oprze obsługę błędów:
```json
{ "timestamp": "2026-07-28T12:00:00", "status": 400, "error": "InsufficientFunds",
  "message": "Insufficient funds", "path": "/transactions/transfer" }
```

**B4. Brak `TransactionController`.**
`TransactionService.withdraw()` istnieje, ale nie jest wystawiony żadnym endpointem.
Cała funkcjonalność transakcyjna FE nie ma się do czego podpiąć. Potrzebne minimum:
```
POST /transactions/transfer     TransferRequest    -> TransactionResponse
POST /transactions/deposit      DepositRequest     -> TransactionResponse
POST /transactions/withdraw     WithdrawalRequest  -> TransactionResponse
GET  /transactions/account/{accountId}             -> TransactionResponse[]
```

**B5. Brak `GET /auth/me`.**
Po odświeżeniu strony FE ma token w localStorage, ale nie wie **kto** jest zalogowany —
nie ma jak wyświetlić "Cześć, Kamil" bez dekodowania JWT po stronie klienta (czego nie robimy,
bo to jest untrusted). Potrzebny endpoint zwracający `{ email, firstName, lastName, role }`.
*Tymczasowe obejście:* po zalogowaniu trzymamy w stanie to co user sam wpisał. Działa do F5.

**B6. Brak walidacji `@Valid` / bean validation na DTO.**
Backend przyjmie `amount: -500` albo pusty email. FE może to zablokować w formularzu, ale
to tylko UX (patrz §0).

**B7. `jwt.secret` jest zacommitowany w `application.properties`.**
Nie dotyczy FE bezpośrednio, ale odnotuj: sekret w repo = każdy kto ma dostęp do repo może
podpisać sobie dowolny token. Docelowo zmienna środowiskowa.

**Co robisz z tą listą:** B1 i B4 są twardymi blokerami — bez nich FE nie ma czego robić poza
ekranem logowania. B2/B3 zrób razem, bo bez nich obsługa błędów w FE to zgadywanka.
B5 i B6 możesz odłożyć.

---

## 2. Stack — decyzje i uzasadnienie

Decyzje są podjęte, nie są do przedyskutowania na starcie. Jeśli po dwóch tygodniach coś
Cię uwiera — wtedy rozmawiamy, z konkretnym przykładem.

| Obszar | Wybór | Dlaczego akurat to |
|---|---|---|
| Bundler | **Vite** | Standard dla nowych projektów React. Zero konfiguracji na start, HMR w milisekundach. |
| Framework | **React 19 + TypeScript (strict)** | `strict: true` od pierwszego dnia. Włączenie tego później to tydzień płaczu. |
| Routing | **React Router v7** (tryb declarative) | Nie potrzebujemy SSR ani loaderów. `createBrowserRouter` + `<RouterProvider>`. |
| Stan serwera | **TanStack Query v5** | Cache, invalidacja, retry, `isPending`/`isError` za darmo. Bez tego napiszesz 200 linii `useEffect` z race conditionami. |
| Stan klienta | **React Context** (tylko auth) | Zustand/Redux to overkill. Jedyny globalny stan to token + user. Jeden context wystarczy. |
| HTTP | **axios** + interceptory | Interceptory (wstrzykiwanie tokenu, mapowanie błędów) to dokładnie to czego potrzebujemy. Z `fetch` piszesz to ręcznie. |
| Formularze | **react-hook-form** | Niekontrolowane inputy = brak re-renderu na każde naciśnięcie klawisza. |
| Walidacja | **zod** | Jeden schemat daje walidację formularza **i** typ TS przez `z.infer`. Zero duplikacji. |
| Style | **Tailwind CSS v4** | Szybko, spójnie, bez wymyślania nazw klas i bez martwego CSS. |
| Testy | **Vitest + React Testing Library + MSW** | Vitest bo dzieli config z Vite. MSW mockuje sieć na poziomie HTTP, nie modułu — testujesz to co naprawdę leci. |
| Formatowanie | **ESLint + Prettier** | Skonfigurowane raz, potem nikt o tym nie dyskutuje na review. |

**Czego świadomie NIE bierzemy:** Next.js (nie potrzebujemy SSR, dokładamy złożoność),
Redux Toolkit (nie mamy globalnego stanu klienta poza auth), Material UI / shadcn
(najpierw naucz się zbudować `<Button>`, potem sięgaj po gotowce), Storybook (za wcześnie).

---

## 3. Architektura — warstwy i kierunek zależności

Podział **feature-based**, nie type-based. Czyli katalog `features/transactions/` zawiera
wszystko o transakcjach (API, typy, komponenty), a nie katalog `components/` ze wszystkimi
komponentami aplikacji wrzuconymi razem.

Dlaczego: kiedy dostaniesz zadanie "dodaj pole tytułu do przelewu", chcesz otworzyć jeden
katalog, a nie skakać między `components/`, `hooks/`, `types/`, `api/`.

### Kierunek zależności — zasada żelazna

```
pages/        (składa widok z feature'ów, zna routing)
   ↓
features/     (logika domenowa: auth, accounts, transactions)
   ↓
shared/       (UI, http, helpery — nic nie wie o domenie banku)
```

Strzałki idą **tylko w dół**. Konkretnie:

- `shared/` **nigdy** nie importuje z `features/` ani `pages/`. `shared/ui/Button` nie wie
  że istnieje coś takiego jak konto bankowe.
- `features/transactions/` **nie importuje** z `features/accounts/`. Jeśli formularz przelewu
  potrzebuje listy kont — `pages/TransferPage.tsx` pobiera konta i przekazuje je propsem.
  To jest ten moment gdzie strona pełni rolę kompozytora.
- Komponent w `features/` **nie robi `axios.get()` bezpośrednio**. Idzie przez
  `features/x/api/`.

Jeśli łamiesz którąś z tych zasad — najpierw zapytaj. W 9 przypadkach na 10 to znak że
coś jest w złym katalogu.

---

## 4. Struktura plików — co za co odpowiada

```
frontend/
├── .env.example
├── .env.local                  (w .gitignore!)
├── index.html
├── package.json
├── tsconfig.json
├── vite.config.ts
├── eslint.config.js
├── .prettierrc
└── src/
    ├── main.tsx
    ├── app/
    ├── shared/
    ├── features/
    ├── pages/
    ├── layouts/
    └── styles/
```

### 4.1 `src/app/` — złożenie aplikacji

| Plik | Odpowiedzialność |
|---|---|
| `main.tsx` | Punkt wejścia. `createRoot().render(<Providers><RouterProvider/></Providers>)`. Nic więcej — żadnej logiki. |
| `app/providers.tsx` | Opakowanie w `QueryClientProvider`, `AuthProvider`, `ToastProvider`, `ErrorBoundary`. Jedno miejsce na kolejność providerów. |
| `app/queryClient.ts` | Instancja `QueryClient` + domyślne opcje: `staleTime: 30_000`, `retry: (count, err) => err.status >= 500 && count < 2` (nie retry'ujemy 4xx — to nie naprawi się samo). |
| `app/router.tsx` | Definicja tras. Trasy chronione owinięte w `<RequireAuth>`. Lazy loading stron przez `React.lazy`. |
| `app/routes.ts` | **Stałe ze ścieżkami.** `export const ROUTES = { login: '/login', accountDetails: (id: string) => `/accounts/${id}` }`. Zero stringów `'/login'` rozsianych po kodzie — literówka w stringu kompiluje się bez problemu, literówka w `ROUTES.logn` nie. |

### 4.2 `src/shared/` — rzeczy niezależne od domeny

#### `shared/api/`

| Plik | Odpowiedzialność |
|---|---|
| `httpClient.ts` | Instancja axios: `baseURL` z env, `timeout: 15000`, `Content-Type: application/json`. Eksportuje jedną instancję — nikt nie tworzy własnej. |
| `interceptors.ts` | **Request:** dokleja `Authorization: Bearer <token>` jeśli token jest w pamięci. **Response:** przy 401 czyści sesję i przekierowuje na `/login`; każdy inny błąd mapuje na `ApiError`. |
| `apiError.ts` | Klasa `ApiError extends Error` z polami `status`, `code`, `message`, `path`. Plus funkcja `toApiError(unknown): ApiError` obsługująca trzy przypadki: (a) backend zwrócił `ErrorResponse`, (b) backend zwrócił coś innego (np. HTML błędu 500), (c) w ogóle nie było odpowiedzi (sieć padła). **Ten plik jest ważniejszy niż wygląda** — dzięki niemu reszta aplikacji ma jeden typ błędu zamiast `unknown`. |
| `types.ts` | `ErrorResponse` odwzorowane 1:1 z backendu. |

#### `shared/config/`

| Plik | Odpowiedzialność |
|---|---|
| `env.ts` | Odczyt `import.meta.env` z walidacją zodem przy starcie. Brak `VITE_API_URL` → aplikacja wywala się natychmiast z czytelnym komunikatem, a nie po 20 minutach debugowania requestu na `undefined/accounts/my`. |

#### `shared/lib/`

| Plik | Odpowiedzialność |
|---|---|
| `money.ts` | `formatMoney(amount, currency)` przez `Intl.NumberFormat`. **Czytaj §6.4 zanim to napiszesz.** |
| `date.ts` | `formatDateTime`, `formatRelative`. Backend zwraca `LocalDateTime` **bez strefy** (`"2026-07-28T12:00:00"`) — nie parsuj tego jako UTC, bo przesuniesz godziny. |
| `accountNumber.ts` | `formatAccountNumber` (grupowanie po 4 znaki), `maskAccountNumber` (`•••• 4821`). |
| `cn.ts` | Sklejanie klas Tailwinda (`clsx` + `tailwind-merge`). |

#### `shared/types/`

| Plik | Odpowiedzialność |
|---|---|
| `enums.ts` | Enumy z backendu jako union types: `export type AccountStatus = 'ACTIVE' \| 'FROZEN' \| 'CLOSED'`. Plus mapy etykiet do wyświetlania (`ACCOUNT_STATUS_LABEL`) — żeby użytkownik widział "Aktywne", a nie `ACTIVE`. |
| `api.ts` | Typy współdzielone. Typy specyficzne dla feature'a mieszkają w tym feature'rze. |

#### `shared/ui/` — komponenty prezentacyjne

Zasada: **zero wiedzy o domenie i zero requestów**. `<Button>` nie wie co to przelew.
Każdy w osobnym katalogu z `index.ts`.

`Button` (warianty: primary/secondary/danger/ghost, stan `isLoading`), `Input` (label,
error, hint — zintegrowany z react-hook-form), `Select`, `Card`, `Badge` (kolor sterowany
propem `tone`), `Modal` (focus trap, Esc, klik w tło), `Spinner`, `Skeleton`,
`EmptyState` (ikona + tekst + opcjonalna akcja), `ErrorState` (komunikat + "Spróbuj ponownie"),
`Toast` + `useToast`.

> **Uwaga o `EmptyState` / `ErrorState` / `Skeleton`:** to nie są ozdobniki na koniec projektu.
> Każdy ekran pobierający dane ma **cztery** stany: ładowanie, błąd, pusto, dane. Jeśli
> zbudujesz tylko czwarty, będziesz je dopisywał później w 8 miejscach.

### 4.3 `src/features/`

Każdy feature ma ten sam kształt: `api/` (surowe wywołania + hooki React Query),
`model/` (typy, schematy zod, stan), `components/` (komponenty domenowe).

#### `features/auth/`

| Plik | Odpowiedzialność |
|---|---|
| `api/authApi.ts` | `login(dto)`, `register(dto)` — czyste wywołania HTTP, zwracają `AuthResponse`. |
| `api/authQueries.ts` | `useLogin()`, `useRegister()` — `useMutation`. W `onSuccess`: zapis tokenu + `navigate` na dashboard. |
| `model/authTypes.ts` | `LoginRequest`, `RegisterRequest`, `AuthResponse`, `AuthUser`. |
| `model/authSchemas.ts` | Schematy zod. Rejestracja: email, hasło min. 8 znaków, PESEL 11 cyfr, telefon. **Reguły walidacji muszą odpowiadać temu, co backend faktycznie przyjmuje** — jak backend nie waliduje (B6), to Twoje reguły są tylko podpowiedzią dla użytkownika. |
| `model/tokenStorage.ts` | `getToken/setToken/clearToken`. **Jedyne** miejsce dotykające `localStorage`. Jak kiedyś przejdziemy na httpOnly cookie, zmieniamy jeden plik. |
| `model/AuthContext.tsx` | `AuthProvider` + `useAuth()`. Trzyma `{ user, token, isAuthenticated, login(), logout() }`. Przy starcie odczytuje token ze storage. |
| `components/LoginForm.tsx` | Formularz. Pola, walidacja, wyświetlenie błędu z serwera. Nie robi `navigate` — to należy do hooka mutacji. |
| `components/RegisterForm.tsx` | Jw., 6 pól. |
| `components/RequireAuth.tsx` | Route guard. Brak tokenu → `<Navigate to="/login" state={{ from: location }} />`, żeby po zalogowaniu wrócić tam gdzie użytkownik chciał wejść. |

#### `features/accounts/`

| Plik | Odpowiedzialność |
|---|---|
| `api/accountsApi.ts` | `getMyAccounts()`, `getAccountById(id)`, `createAccount(dto)`. |
| `api/accountQueries.ts` | `useMyAccounts()`, `useAccount(id)`, `useCreateAccount()`. Klucze cache trzymane w `accountKeys` (`['accounts']`, `['accounts', id]`) — bez tego invalidacja to zgadywanie stringów. Po `createAccount` → `invalidateQueries(accountKeys.all)`. |
| `model/accountTypes.ts` | `Account`, `CreateAccountRequest`. |
| `model/accountSchemas.ts` | Schemat formularza tworzenia konta (typ + waluta). |
| `components/AccountCard.tsx` | Kafelek konta: numer (sformatowany), typ, saldo, badge statusu. Klikalny → szczegóły. |
| `components/AccountList.tsx` | Lista/siatka kart. Obsługuje `EmptyState` gdy brak kont. |
| `components/AccountStatusBadge.tsx` | `ACTIVE` → zielony, `FROZEN` → żółty, `CLOSED` → szary. Mapowanie w jednym miejscu. |
| `components/CreateAccountModal.tsx` | Modal z formularzem. |
| `components/AccountSelect.tsx` | Select konta — reużywany w każdym formularzu transakcji. Pokazuje numer + saldo, filtruje po `status === 'ACTIVE'`. |

#### `features/transactions/`

> Zależy od **B4**. Do czasu powstania `TransactionController` pracujesz na MSW (§7) — to
> jest normalny sposób pracy, nie hack. Kontrakt jest znany z DTO, więc możesz budować.

| Plik | Odpowiedzialność |
|---|---|
| `api/transactionsApi.ts` | `transfer()`, `deposit()`, `withdraw()`, `getAccountTransactions(accountId)`. |
| `api/transactionQueries.ts` | Mutacje + query historii. **Po każdej udanej transakcji invaliduj `accountKeys.all` i historię** — saldo się zmieniło, cache jest nieaktualny. To jest ten jeden szczegół, który najczęściej się pomija i potem "saldo się nie odświeża". |
| `model/transactionTypes.ts` | `Transaction`, `TransferRequest`, `DepositRequest`, `WithdrawalRequest`. |
| `model/transactionSchemas.ts` | Walidacja kwoty (> 0, maks. 2 miejsca po przecinku), numeru konta, tytułu. |
| `components/TransferForm.tsx` | Konto źródłowe (select), numer docelowy, kwota, waluta, tytuł. **Przycisk blokowany na czas `isPending`** — inaczej dwuklik = dwa przelewy. Backend nie ma idempotencji poza `referenceId`, więc to jest realna ochrona. |
| `components/DepositForm.tsx` | Wpłata na własne konto. |
| `components/WithdrawForm.tsx` | Wypłata. |
| `components/TransactionList.tsx` | Historia z 4 stanami (loading/error/empty/data). |
| `components/TransactionRow.tsx` | Wiersz: ikona typu, tytuł, data, kwota ze znakiem i kolorem (wpływ zielony / wypływ czerwony), badge statusu. Znak wyznaczasz porównując `sourceAccountNumber` z numerem oglądanego konta. |
| `components/TransactionStatusBadge.tsx` | `COMPLETED`/`PENDING`/`FAILED`/`CANCELLED`. |

### 4.4 `src/pages/` — kompozycja

Strona pobiera dane, wybiera layout, składa komponenty z feature'ów, obsługuje parametry
z URL. **Nie zawiera logiki biznesowej ani stylowania szczegółów.** Jeśli plik strony
przekracza ~120 linii, coś z niej powinno wyjechać do `features/`.

`LoginPage`, `RegisterPage`, `DashboardPage` (lista kont + skrót ostatnich transakcji),
`AccountDetailsPage` (`useParams` → `useAccount(id)` + historia + akcje),
`TransferPage`, `NotFoundPage`.

### 4.5 `src/layouts/`

`AppLayout` (header + nav + `<Outlet/>`, dla zalogowanych), `AuthLayout` (wyśrodkowana karta
dla login/register), `Header` (logo, nazwa użytkownika, wyloguj), `Nav`.

---

## 5. Kluczowe przepływy — jak to ma działać

### 5.1 Logowanie

```
LoginForm submit
  → useLogin().mutate({ email, password })
  → POST /auth/login
  → 200 { token, type }
  → tokenStorage.setToken(token)
  → AuthContext.login(token, user)
  → navigate(location.state?.from ?? '/dashboard')
```

Odświeżenie strony: `AuthProvider` czyta token ze storage w `useState(() => getToken())`.
Token jest, więc `isAuthenticated === true`. Danych użytkownika nie mamy (**B5**) — do czasu
dodania `/auth/me` header pokazuje sam email zapamiętany przy logowaniu, albo nic.

**Nie dekodujemy JWT na froncie żeby wyciągnąć dane użytkownika.** Payload JWT nie jest
weryfikowany po stronie klienta — to dane, którym nie ufamy. Do wyświetlenia nazwiska
potrzebny jest endpoint.

### 5.2 Wygaśnięcie tokenu

Token żyje 24h, backend nie ma refresh tokenu. Kiedy wygaśnie, kolejny request wraca 401.
Response interceptor: czyści storage, czyści cache React Query (`queryClient.clear()` —
inaczej następny użytkownik zobaczy cudze salda z cache), przekierowuje na `/login`
z komunikatem "Sesja wygasła".

Przekierowanie z interceptora robisz przez zdarzenie/callback, nie przez import routera do
warstwy HTTP — to złamałoby kierunek zależności z §3.

### 5.3 Dev proxy (obejście CORS na czas developmentu)

```ts
// vite.config.ts
server: {
  proxy: {
    '/auth':         { target: 'http://localhost:8087', changeOrigin: true },
    '/accounts':     { target: 'http://localhost:8087', changeOrigin: true },
    '/transactions': { target: 'http://localhost:8087', changeOrigin: true },
  },
}
```
Wtedy `VITE_API_URL=''` (ścieżki względne) i przeglądarka widzi jeden origin.
**To działa tylko w dev.** CORS na backendzie (B1) i tak jest potrzebny.

### 5.4 Obsługa błędów — trzy poziomy

1. **Błąd pola** (400 z konkretnym polem) → pod inputem, przez `setError` z react-hook-form.
2. **Błąd operacji** (`InsufficientFunds`, `AccountNotFound`) → alert w formularzu + toast.
3. **Błąd nieoczekiwany** (500, brak sieci) → `ErrorState` z przyciskiem ponowienia, przy
   awarii całej strony `ErrorBoundary`.

Do czasu naprawy **B2** wszystko wraca jako 500 — zbuduj obsługę pod docelowy kształt
`ErrorResponse`, ale nie zdziw się że na razie wszystko wpada do worka „nieoczekiwane".

**Nigdy nie pokazuj użytkownikowi surowego stack trace'a ani `error.message` z 500.**
Mapuj na komunikaty po polsku w jednym miejscu (`shared/api/errorMessages.ts`).

### 5.5 Pieniądze — przeczytaj zanim napiszesz `formatMoney`

Backend używa `BigDecimal`, Jackson serializuje go domyślnie jako **liczbę JSON**:
`"balance": 1234.56`. W JS to `number`, czyli IEEE 754 double — `0.1 + 0.2 === 0.30000000000004`.

Zasady:
- **Nie wykonuj arytmetyki na kwotach we frontendzie.** Nie sumuj sald, nie odejmuj prowizji,
  nie licz "ile zostanie po przelewie". Wyświetlaj to co przyszło.
- Do formatowania: `Intl.NumberFormat('pl-PL', { style: 'currency', currency })`.
- Kwotę wpisaną przez użytkownika trzymaj w formularzu jako **string**, waliduj regexem
  `^\d+([.,]\d{1,2})?$`, konwertuj na number dopiero przy wysyłce.
- Jeśli kiedyś pojawi się potrzeba liczenia po stronie FE — rozmawiamy o `dinero.js`
  albo o tym, żeby backend zwracał grosze jako integer. Nie improwizuj.

---

## 6. Konwencje

**Nazewnictwo plików:** komponenty `PascalCase.tsx`, reszta `camelCase.ts`, katalogi
`kebab-case` lub `camelCase` (byle spójnie), typy `PascalCase`, stałe `SCREAMING_SNAKE_CASE`.

**Komponenty:** funkcyjne, nazwany export (`export function AccountCard()`), propsy jako
`type AccountCardProps`, żadnego `React.FC`. Jeden komponent = jeden plik.

**TypeScript:** `strict: true`, **zero `any`** — jak nie wiesz jaki typ, użyj `unknown`
i zawęź. `interface` dla obiektów rozszerzalnych, `type` dla unii i propsów.

**Importy:** alias `@/` na `src/` (skonfiguruj w `tsconfig.json` **i** `vite.config.ts` —
oba, inaczej TS widzi a bundler nie). Kolejność: zewnętrzne → `@/shared` → `@/features`
→ względne.

**Git:** branche `feature/fe-login-form`, `fix/fe-token-refresh`. Commity w konwencji
`feat(fe): ...`, `fix(fe): ...` — spójnie z tym co już jest w repo. Małe PR-y: jeden PR =
jeden milestone albo mniej. PR na 40 plików nikt porządnie nie przejrzy.

---

## 7. Milestone'y

Kolejność ma znaczenie — każdy krok stoi na poprzednim. Nie zaczynaj M3 zanim M2 nie działa.

### M0 — Odblokowanie backendu
Napraw **B1** (CORS), **B3** (`ErrorResponse` → `String path`), **B2** (handlery wyjątków).
Dodaj `TransactionController` (**B4**).
**DoD:** curlem przechodzi rejestracja → logowanie → utworzenie konta → lista kont;
błędny login zwraca 400/401 z JSON-em `ErrorResponse`, nie 500 z HTML-em.

### M1 — Szkielet
`npm create vite@latest frontend -- --template react-ts`. Tailwind, ESLint, Prettier,
alias `@/`, struktura katalogów, `httpClient` + interceptory + `ApiError`, `env.ts`,
router z 2 pustymi stronami, `AppLayout`.
**DoD:** `npm run dev` startuje, `npm run build` przechodzi bez błędów TS,
`/login` i `/dashboard` renderują się.

### M2 — Autoryzacja
`AuthContext`, `tokenStorage`, `LoginForm`, `RegisterForm`, `RequireAuth`, obsługa 401.
**DoD:** rejestracja tworzy konto i loguje; F5 nie wylogowuje; wejście na `/dashboard`
bez tokenu przekierowuje na `/login` i po zalogowaniu wraca na `/dashboard`; zły login
pokazuje czytelny komunikat, nie „Request failed with status code 500".

### M3 — Konta
`useMyAccounts`, `AccountCard`, `AccountList`, `CreateAccountModal`, `DashboardPage`,
`AccountDetailsPage`, cztery stany widoku.
**DoD:** dashboard pokazuje konta z sald; utworzenie konta odświeża listę **bez
przeładowania strony**; pusty stan ma sensowny ekran; wejście na `/accounts/<losowy-uuid>`
pokazuje błąd zamiast białego ekranu.

### M4 — Transakcje
`TransferForm`, `DepositForm`, `WithdrawForm`, `TransactionList`, historia na szczegółach
konta, invalidacja cache.
**DoD:** przelew między własnymi kontami zmienia oba salda w UI natychmiast po sukcesie;
przelew ponad saldo pokazuje "Niewystarczające środki"; dwuklik w "Wyślij" tworzy
**jeden** przelew.

### M5 — Jakość
Testy (§8), dostępność (etykiety, focus, nawigacja klawiaturą), responsywność (360px),
loading skeletony, `README.md` z instrukcją uruchomienia.
**DoD:** `npm run test` zielony, `npm run build` bez ostrzeżeń, aplikacja używalna
na telefonie i z samej klawiatury.

---

## 8. Testy

Nie testujemy wszystkiego. Testujemy to, czego zepsucie boli.

**Warto:** `money.ts` / `accountNumber.ts` / `date.ts` (czyste funkcje, tanie testy),
`toApiError` (trzy ścieżki błędu), schematy zod, `LoginForm` (walidacja + wysyłka +
błąd serwera), `TransferForm` (blokada podwójnego submitu), `RequireAuth`.

**Nie warto:** że `<Button>` renderuje tekst, snapshoty całych stron (pękają przy każdej
zmianie klasy CSS i nikt ich nie czyta).

**MSW** — handlery w `tests/mocks/handlers.ts` odwzorowujące kontrakt z §1.1. Ten sam mock
obsługuje testy **i** development przed powstaniem `TransactionController`. Jeden komplet
handlerów, dwa zastosowania.

---

## 9. Poza zakresem

Żeby było jasne czego **nie** robimy w tym podejściu: refresh tokeny, role ADMIN i panel
administracyjny, paginacja historii, filtry i wyszukiwanie transakcji, eksport do PDF/CSV,
wielojęzyczność, dark mode, powiadomienia real-time (WebSocket/SSE), PWA.

Część z tych rzeczy pewnie dołożymy — ale nie zanim M4 nie będzie działać.

---

## 10. Pierwszy dzień — konkretne kroki

1. Odpal backend i przeklikaj API w Postmanie/curlu. Zarejestruj użytkownika, zaloguj,
   stwórz konto, pobierz listę. **Zanim napiszesz linijkę TS, musisz wiedzieć co API
   naprawdę zwraca** — nie co powinno zwracać wg dokumentu.
2. Zrób M0. Bez tego reszta stoi.
3. `npm create vite@latest frontend -- --template react-ts`, zainstaluj zależności z §2.
4. Postaw `httpClient` + interceptory + `ApiError` i zrób jeden request do `/auth/login`
   z zahardkodowanymi danymi. Jak zobaczysz token w konsoli — fundament stoi.
5. Dopiero teraz buduj UI.

Jak coś w tym dokumencie nie zgadza się z rzeczywistością (bo backend się zmienił) —
popraw dokument w tym samym PR-ze co kod. Nieaktualna dokumentacja jest gorsza niż żadna.

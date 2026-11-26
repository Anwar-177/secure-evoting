# SecureVote - Secure Blockchain E-Voting System with Biometric Authentication

Java Spring Boot implementation of the four modules described in `c1-CSE-C.docx`.

## Technology

| Layer      | Technology |
|------------|------------|
| Backend    | Java 17+, Spring Boot 3.5 (Web MVC, Security, Data JPA) |
| Frontend   | Thymeleaf, HTML, CSS, JavaScript |
| Database   | H2 file database by default (zero setup), MySQL via profile |
| Biometric  | face-api.js (face detection + 128-value face descriptor), matched on the server |
| Security   | BCrypt passwords, SHA-256, HMAC-SHA256, AES-256-GCM, CSRF protection |
| Blockchain | Private Java blockchain with proof of work, stored in the database |

## How to run

Requires only a JDK (17 or newer). Maven is downloaded automatically by the wrapper.

```
cd secure-evoting
mvnw.cmd spring-boot:run
```

Open <http://localhost:8085>

| Role  | Login |
|-------|-------|
| Admin | username `admin`, password `admin123` (at `/admin/login`) |
| Voter | register at `/voter/register`, then log in with email + password |

Port, admin credentials, encryption secret, mining difficulty and the face-match threshold are in
`src/main/resources/application.properties`.

### Using MySQL instead of H2

Edit the username/password in `application-mysql.properties`, then:

```
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=mysql
```

Tables are created automatically.

### Running with Docker Compose (application + MySQL)

Requires only Docker Desktop (running). Java, Maven and MySQL are not needed on the computer.

```
cd secure-evoting
docker compose up --build -d
```

Open <http://localhost:8085>. MySQL is reachable on `localhost:3385` (database `evoting`, user `evoting`,
password `evoting123`).

| Command | Purpose |
|---------|---------|
| `docker compose ps` | container status |
| `docker compose logs -f app` | application log |
| `docker compose stop` / `start` | stop / start, data kept |
| `docker compose down` | remove containers, keep the database volume |
| `docker compose down -v` | remove containers and delete all data |

To change ports or passwords, copy `.env.example` to `.env` and edit it.

### Camera note

Browsers allow camera access only on `http://localhost` or HTTPS. On a machine without a webcam use
**Upload photo** instead.

## Demo walk-through

1. **Admin** logs in, creates an election and adds at least two candidates.
2. **Voter** registers with personal details and a face capture (fingerprint image optional).
3. **Admin** opens *Voters*, compares the face photo and approves the voter.
4. **Admin** starts the election.
5. **Voter** logs in, clicks *Verify identity and vote*, passes face verification, selects a candidate and
   receives a receipt with the blockchain transaction ID.
6. **Admin** stops the election, opens *Blockchain* to validate the chain (use *Simulate tampering* to show
   detection, then *Restore*), and publishes the result.
7. **Anyone** views the result under *Results* and verifies a transaction ID on the *Public Ledger*.

## Modules and user stories

### Module 1 - Admin / Election Management (`AdminController`)
| Story | Where |
|-------|-------|
| US-1.1 Admin login | `/admin/login`, `SecurityConfig` |
| US-1.2 Create election | `/admin/elections` |
| US-1.3 Candidate management | `/admin/elections/{id}` |
| US-1.4 Start / stop election | `/admin/elections/{id}` (Draft -> Active -> Closed -> Published) |
| US-1.5 Voter list | `/admin/voters` (approve / reject) |
| US-1.6 Dashboard | `/admin/dashboard` |

### Module 2 - Voter Registration and Biometric Authentication (`VoterController`, `BiometricService`, `face.js`)
| Story | Where |
|-------|-------|
| US-2.1 Voter registration | `/voter/register` |
| US-2.2 Face registration | camera capture or photo upload, descriptor stored as the template |
| US-2.3 Fingerprint registration | optional fingerprint image, keyed hash stored |
| US-2.4 Voter login | `/voter/login` |
| US-2.5 Biometric verification | `/voter/verify` - live descriptor vs stored template (Euclidean distance) |
| US-2.6 Access control | ballot is unreachable without verification; 5 failed attempts log the voter out |

### Module 3 - Secure Voting and Blockchain Transaction (`VotingService`, `BlockchainService`, `CryptoService`)
| Story | Where |
|-------|-------|
| US-3.1 / 3.2 Election and candidate display | `/voter/dashboard`, `/voter/ballot/{id}` |
| US-3.3 Vote casting | `POST /voter/vote` |
| US-3.4 Double-vote prevention | unique `vote_records` row + anonymous voter token checked on the chain |
| US-3.5 Vote security | AES-256-GCM encryption + HMAC-SHA256 vote hash |
| US-3.6 Blockchain transaction | one mined block per vote, linked by SHA-256 hashes |
| US-3.7 Vote confirmation | `/voter/receipt/{id}` |

### Module 4 - Blockchain Verification, Counting and Results (`ResultService`, `HomeController`)
| Story | Where |
|-------|-------|
| US-4.1 Blockchain validation | `/admin/blockchain` |
| US-4.2 / 4.3 Vote counting, candidate-wise result | election page after voting is stopped |
| US-4.4 Result viewing | `/results` |
| US-4.5 Blockchain audit | `/audit` (public ledger + transaction lookup) |
| US-4.6 Result publication | *Verify and publish result* (blocked if the chain is invalid) |

## Project structure

```
src/main/java/com/evoting
  config/       SecurityConfig, DataInitializer (default admin + genesis block)
  controller/   AdminController, VoterController, HomeController
  model/        Admin, Voter, Election, Candidate, VoteRecord, Block
  repository/   Spring Data JPA repositories
  service/      CryptoService, BlockchainService, VotingService, ResultService, BiometricService
src/main/resources
  templates/    Thymeleaf pages (admin/, voter/, public)
  static/       css/app.css, js/face.js, vendor/face-api (library + models, works offline)
```

## Scope of the academic implementation

- The blockchain is a private, single-node chain kept in the database (as recommended in the project
  document), not a distributed Ethereum network.
- The face descriptor is computed in the browser and matched on the server; there is no liveness detection.
- Fingerprint is a simplified optional factor: without a hardware scanner, the same fingerprint image file
  must be presented again.

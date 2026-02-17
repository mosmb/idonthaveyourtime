# Architecture Guardrails

These rules define the clean architecture standards for this repository.

## Dependency direction
- UI layer depends on domain use cases.
- Domain depends only on domain ports and domain/model types.
- Data/infrastructure depends on domain ports and implements them.
- Feature modules must not depend on the data layer (domain-only).

## ViewModel rules
- A `*ViewModel` must depend only on `*UseCase` classes.
- A `*ViewModel` must not inject domain ports, repositories, or data sources directly.

## Use case rules
- A `*UseCase` must represent one business responsibility.
- Use cases must not import Android (`android.*`, `androidx.*`) APIs.
- Use case dependencies must be explicit constructor parameters.
- Use cases must depend **only** on `*Repository` domain ports and domain/model types (never `*DataSource` ports).

## Domain model rules
- One domain model declaration per file.
- Nested declarations are allowed only when the nested type is fully contained by the parent domain model.

## Port and data-source interface rules
- In general, one interface declaration per file for data-source/domain port interfaces.
- Ports are declared in the **domain** layer and implemented in outer layers.
- The domain layer must declare **repository ports only** (`*Repository`) and must not declare any `*DataSource` interface.
- All data-source interfaces live in the **data** layer (`:core:data`, under `core.data.datasource.*`).
- Data-source interfaces must be **boundary-explicit** via suffix:
  - **Local** = on-device concerns (filesystem/assets, Room/DataStore, ContentResolver, JNI, WorkManager when used for on-device work) → `*LocalDataSource`
  - **Remote** = network/remote-service concerns (including WorkManager workers that perform network I/O) → `*RemoteDataSource`
- If a responsibility spans both local and remote concerns, **split it into separate data-source interfaces** (no hybrid/ambiguous data sources).
- A single data-source implementation must never implement both a `*LocalDataSource` and a `*RemoteDataSource` interface.
- A `*LocalDataSource` implementation must not depend on any `*RemoteDataSource` interface (and vice versa). Put local/remote orchestration in a `*Repository`.
- Every interface (repositories and data sources) must have KDoc. Do not add KDoc to implementations.

## Repository rules
- Repository ports are declared in the domain layer and are the only ports that `*UseCase` classes may depend on.
- Repository implementations live in outer layers (typically `:core:data`) and may coordinate local + remote data sources.
- Repository implementations must be `internal`.

## Naming conventions
- **Enforced suffixes:**
  - `*ViewModel`
  - `*Repository`
  - `*LocalDataSource`
  - `*RemoteDataSource`
- **Convention (not enforced):**
  - `*UseCase`
- Repository **ports** must be suffixed with `Repository` (e.g. `SessionRepository`).
- Data-source interfaces must:
  - be suffixed with exactly one boundary marker: `LocalDataSource` or `RemoteDataSource`
  - examples: `AudioImporterLocalDataSource`, `ModelDownloaderRemoteDataSource`
- Any class implementing a data-source interface must:
  - be `internal`
  - be suffixed with `LocalDataSource` / `RemoteDataSource` matching the interface boundary
  - examples: `ContentResolverAudioImporterLocalDataSource`, `WorkManagerModelDownloaderRemoteDataSource`
- Guardrail: a `*LocalDataSource` must not perform network I/O or initiate remote downloads (directly or indirectly).
- Repository implementations must be suffixed with `Repository`.
- Dependencies of `*Repository` / `*LocalDataSource` / `*RemoteDataSource` implementations are **not** required to follow any suffix rule (unless they are themselves a `*ViewModel`, `*Repository`, `*LocalDataSource`, or `*RemoteDataSource`).
- Explicit exceptions (not domain ports): Room `*Dao`, Retrofit `*Service`, etc. may use conventional names.
- In the clean architecture layer, only classes under concrete data-source implementation packages may use names outside these suffix rules when needed.

## Current scoped exception
- Model-file import flow in the summarize screen currently stays in UI (`SummarizeScreen`) and is not yet extracted into a dedicated use case.

# Vianova

## Prerequisites

- Java 17+
- Maven (or use `./mvnw`)
- SQL Server running and reachable

## 1. Enable SQL Server TCP/IP

On Windows:

1. Open `SQL Server Configuration Manager`
2. Go to `SQL Server Network Configuration` -> `Protocols for <your-instance>`
3. Enable `TCP/IP`
4. Open `TCP/IP` -> `IP Addresses`
5. Set `TCP Port` to `1433` (clear dynamic ports if needed)
6. Restart SQL Server service (`SQL Server (MSSQLSERVER)` or `SQL Server (SQLEXPRESS)`)

Optional quick check:

- Confirm SQL Server is listening on `localhost:1433`

## 2. Update DB Configuration

Before starting the project, update:

- `vianova-api/src/main/resources/application.properties`

Set these values for your environment:

```properties
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=master;encrypt=true;trustServerCertificate=true
spring.datasource.username=java_user
spring.datasource.password=StrongPassword123
spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver
```

## 3. Run Schema Files

Run these SQL files in SQL Server:

1. `vianova-api/src/main/resources/driver-schema.sql`
2. `vianova-api/src/main/resources/rider-schema.sql`

You can run them using SSMS/Azure Data Studio in the target database.

Note: the app also has SQL init enabled, but run these once manually first to ensure the database is ready.

## 4. Start API

Optional LLM tool-calling configuration:

```properties
openai.api.key=${OPENAI_API_KEY:}
openai.base-url=https://api.openai.com/v1
openai.chat.model=gpt-4.1-mini
```

Set `OPENAI_API_KEY` in your local environment to enable OpenAI tool-calling. Without it, the chatbot falls back to the local deterministic intent router.

From project root:

```bash
./mvnw -pl vianova-api spring-boot:run
```

API default port: `8443`

Optional ML fare/ETA service configuration in `vianova-api/src/main/resources/application.properties`:

```properties
ml.service.enabled=true
ml.service.base-url=http://127.0.0.1:8001
```

When enabled, `/rides/estimate` and the server-side estimate used for `/rides/requests` call the Python ML service first and fall back to a local heuristic if the service is unavailable.

## 4a. Start ML Service

In a separate terminal:

```bash
cd ml/ride-ml-service
pip install -r requirements.txt
python -u -m uvicorn ml_inference_service:app --host 127.0.0.1 --port 8001 > ml-service.out.log 2> ml-service.err.log
```

ML service default port: `8001`

On Windows PowerShell, you can also start it detached and keep the same log files:

```powershell
Start-Process -FilePath python -ArgumentList '-u','-m','uvicorn','ml_inference_service:app','--host','127.0.0.1','--port','8001' -WorkingDirectory 'C:\Users\suraj\IdeaProjects\vianova\ml\ride-ml-service' -RedirectStandardOutput 'C:\Users\suraj\IdeaProjects\vianova\ml\ride-ml-service\ml-service.out.log' -RedirectStandardError 'C:\Users\suraj\IdeaProjects\vianova\ml\ride-ml-service\ml-service.err.log'
```

Log files:

- `ml/ride-ml-service/ml-service.out.log`
- `ml/ride-ml-service/ml-service.err.log`

## 5. Start UI

In a new terminal:

```bash
./mvnw -pl vianova-ui spring-boot:run
```

UI default port: `8081`

## 6. Access App

Open:

- `http://localhost:8081`

If API URL/port changes, update:

- `vianova-ui/src/main/resources/application.properties`
  - `vianova.api.base-url=...`

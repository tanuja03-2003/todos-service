# todos-service — CI/CD Pipeline Demo

A Spring Boot REST API used to demonstrate a complete enterprise CI/CD pipeline with Jenkins.

---

## Jenkins Plugins Required

| Plugin | Purpose | Install Name |
|--------|---------|--------------|
| **Pipeline** | Declarative pipeline support | `workflow-aggregator` |
| **Git** | SCM checkout | `git` |
| **Credentials Binding** | `withCredentials` for secrets | `credentials-binding` |
| **SonarQube Scanner** | SAST integration + Quality Gate | `sonar` |
| **JUnit** | Test result reporting | `junit` |
| **JaCoCo** | Code coverage reporting | `jacoco` |
| **Coverage** | Code coverage visualization | `coverage` |
| **OWASP Dependency-Check** | SCA report publishing | `dependency-check-jenkins-plugin` |
| **HTML Publisher** | Security report dashboards | `htmlpublisher` |
| **Docker Pipeline** | Docker build/push steps | `docker-workflow` |
| **Pipeline: Stage View** | Visual stage view | `pipeline-stage-view` |
| **Timestamps** | Add timestamps to logs | `timestamper` |
| **Slack Notification** | Slack alerts | `slack` |
| **Email Extension** | Email notifications | `email-ext` |
| **Pipeline Utility Steps** | Utility steps (readJSON, etc.) | `pipeline-utility-steps` |

---

## Jenkins Tools Required

Configure these under **Manage Jenkins → Tools**:

| Tool | Name in Jenkins | Notes |
|------|----------------|-------|
| **JDK** | `JDK-17` | Java 17+ required |
| **Maven** | `Maven-3` | Maven 3.9+ recommended |
| **Docker** | (system) | Docker must be installed on the agent |

---

## Jenkins Credentials Required

Configure these under **Manage Jenkins → Credentials**:

| Credential ID | Type | Purpose |
|---------------|------|---------|
| `sonar-token` | Secret text | SonarQube authentication token |
| `nexus-credentials` | Username/Password | Nexus Repository Manager login |

---

## Jenkins System Configuration

Under **Manage Jenkins → System**:

| Setting | Value |
|---------|-------|
| **SonarQube servers** | Name: `SonarQube`, URL: `http://localhost:8085`, Token: `sonar-token` credential |
| **Slack** | Workspace, channel `#builds`, bot token |

---

## External Services

| Service | Port | Purpose |
|---------|------|---------|
| **SonarQube** | `8085` | SAST — static code analysis |
| **Nexus (Web/Maven)** | `8081` | Artifact repository (JAR) |
| **Nexus (Docker)** | `8083` | Docker image registry |
| **MailHog (SMTP)** | `1025` | Mail server (SMTP) |
| **MailHog (Web UI)** | `8025` | Mail inbox viewer |

---

## SonarQube Setup

SonarQube performs static code analysis (SAST) — finds bugs, code smells, and security vulnerabilities.

### 1. Run SonarQube Container

```bash
docker run -d --name sonarqube \
    -p 8085:9000 \
    -v sonarqube_data:/opt/sonarqube/data \
    -v sonarqube_extensions:/opt/sonarqube/extensions \
    -v sonarqube_logs:/opt/sonarqube/logs \
    sonarqube:lts-community
```

- **Web UI**: `http://localhost:8085`
- Container port `9000` is mapped to host port `8085`
- Data persists in Docker volumes

### 2. Initial Setup

1. Open `http://localhost:8085` — wait 1-2 minutes for startup
2. Login with default credentials: **admin / admin**
3. Change the password when prompted

### 3. Generate Authentication Token

1. Go to **My Account** (top-right avatar) → **Security**
2. Enter token name: `jenkins`
3. Click **Generate** → copy the token (e.g., `sqp_xxxxxxxxxxxx`)
4. Save this token — you'll need it for Jenkins

### 4. Create Project

1. Go to **Projects → Create Project → Manually**
2. Project key: `todos-service`
3. Project name: `todos-service`
4. Click **Set Up**

### 5. Configure Webhook (for Quality Gate)

1. Go to **Administration → Configuration → Webhooks**
2. Click **Create**
3. Name: `Jenkins`
4. URL: `http://<JENKINS_IP>:8080/sonarqube-webhook/`
5. Click **Create**

### 6. Configure Jenkins

**Manage Jenkins → Credentials:**
- Kind: **Secret text**
- Secret: paste the token from step 3
- ID: `sonar-token`

**Manage Jenkins → System → SonarQube servers:**
- Name: `SonarQube`
- Server URL: `http://localhost:8085`
- Server authentication token: select `sonar-token`

---

## Nexus Repository Setup

Nexus stores build artifacts (JAR files) and Docker images in a private registry.

### 1. Run Nexus Container

```bash
docker run -d --name nexus \
    -p 8081:8081 \
    -p 8083:8083 \
    -v nexus_data:/nexus-data \
    sonatype/nexus3
```

- **Web UI**: `http://localhost:8081`
- **Docker Registry**: port `8083`
- Data persists in Docker volume

### 2. Get Initial Admin Password

```bash
# Wait ~2 minutes for Nexus to start, then:
docker exec nexus cat /nexus-data/admin.password
```

### 3. Initial Setup

1. Open `http://localhost:8081`
2. Click **Sign In** → username: `admin`, password: from step 2
3. Follow the setup wizard:
   - Set new admin password (e.g., `admin123`)
   - Enable anonymous access: **Yes** (for pulling artifacts)

### 4. Create Maven Repository

1. Go to **Settings (gear icon) → Repositories → Create Repository**
2. Select **maven2 (hosted)**
3. Name: `maven-snapshots`
4. Version policy: **Snapshot**
5. Deployment policy: **Allow redeploy**
6. Click **Create Repository**

### 5. Create Docker Repository

1. Go to **Settings → Repositories → Create Repository**
2. Select **docker (hosted)**
3. Name: `psi-docker`
4. Check **HTTP** connector, port: `8083`
5. Enable **Docker V1 API** (optional)
6. Click **Create Repository**

### 6. Enable Docker Realm

1. Go to **Settings → Security → Realms**
2. Move **Docker Bearer Token Realm** to the **Active** column
3. Click **Save**

### 7. Configure Docker Insecure Registry

On the **Jenkins/Nexus host**, add insecure registry:

```bash
sudo tee /etc/docker/daemon.json <<EOF
{
  "insecure-registries": ["localhost:8083", "<YOUR_SERVER_IP>:8083"]
}
EOF
sudo systemctl restart docker
```

On **Mac (Docker Desktop)**: Settings → Docker Engine → add `"insecure-registries": ["<IP>:8083"]`

### 8. Configure Jenkins

**Manage Jenkins → Credentials:**
- Kind: **Username with password**
- Username: `admin`
- Password: your Nexus admin password
- ID: `nexus-credentials`

### 9. Verify

```bash
# Test Maven repo
curl -u admin:admin123 http://localhost:8081/repository/maven-snapshots/

# Test Docker login
echo "admin123" | docker login -u admin --password-stdin localhost:8083

# Test Docker push
docker pull hello-world
docker tag hello-world localhost:8083/hello-world:test
docker push localhost:8083/hello-world:test
```

---

## Mail Server Setup (MailHog)

MailHog is a lightweight email testing tool — it catches all outgoing emails so you can view them in a web UI. No real emails are sent.

### 1. Run MailHog Container

```bash
docker run -d --name mailhog \
    -p 1025:1025 \
    -p 8025:8025 \
    mailhog/mailhog
```

- **SMTP**: `localhost:1025` (Jenkins sends emails here)
- **Web UI**: `http://localhost:8025` (view received emails in browser)

### 2. Configure Jenkins Email

Go to **Manage Jenkins → System**:

#### Extended E-mail Notification (Email Extension Plugin)

| Setting | Value |
|---------|-------|
| SMTP server | `localhost` |
| SMTP port | `1025` |
| Default Content Type | `text/html` |
| Default Recipients | `devops@example.com` |
| Default Subject | `$PROJECT_NAME - Build #$BUILD_NUMBER - $BUILD_STATUS` |

> Leave **Use SSL** and **Use TLS** unchecked. No authentication needed for MailHog.

#### E-mail Notification (built-in)

| Setting | Value |
|---------|-------|
| SMTP server | `localhost` |
| SMTP port | `1025` |
| Test e-mail recipient | `test@example.com` |

Click **Test configuration** — you should see the test email appear in MailHog at `http://localhost:8025`.

### 3. Verify

1. Open `http://localhost:8025` in your browser
2. Trigger a Jenkins build that fails
3. Check MailHog inbox — you should see the failure notification email

> **Tip**: MailHog captures ALL emails regardless of recipient address. Use any `@example.com` address.

---

## Pipeline Parameters

The pipeline supports these build parameters:

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `DEPLOY_ENV` | Choice | `none` | `none` / `staging` / `production` |
| `SKIP_TESTS` | Boolean | `false` | Skip unit tests |
| `SKIP_SECURITY_SCANS` | Boolean | `false` | Skip all security scans (Gitleaks, SAST, SCA, DAST, Trivy) |

---

## Pipeline Stages (18)

```
 ┌─────────────────────────────────────────────────────────────┐
 │  1. Checkout                                                │
 │  2. Secrets Detection (Gitleaks)          ← skippable       │
 │  3. Build & Test (mvn clean verify)       ← skippable       │
 │  4. SAST & SCA (parallel)                 ← skippable       │
 │     ├── SonarQube (SAST)                                    │
 │     └── OWASP Dependency-Check (SCA)      ← SLOW (~5 min)  │
 │  5. Quality Gate                          ← skippable       │
 │  6. Package JAR & Build Docker Image                        │
 │  7. Vulnerability Scan (Trivy)            ← skippable       │
 │  8. DAST - OWASP ZAP                     ← skippable       │
 │  9. Security Reports Dashboard            ← skippable       │
 │ 10. Publish JAR to Nexus                  ← main only       │
 │ 11. Push Docker Image to Nexus            ← main only       │
 │ 12. Deploy to Staging                     ← parameter       │
 │ 13. Smoke Tests (/health)                 ← parameter       │
 │ 14. Manual Approval                       ← parameter       │
 │ 15. Deploy to Production + Git Tag        ← parameter       │
 │ 16. Rollback (placeholder)                ← parameter       │
 │ 17. Build Summary Dashboard                                 │
 └─────────────────────────────────────────────────────────────┘
```

### Slow Stages (disable with `SKIP_SECURITY_SCANS=true`)

| Stage | Time | Reason |
|-------|------|--------|
| **SCA (OWASP DC)** | ~5-10 min | Downloads NVD database on first run |
| **SAST (SonarQube)** | ~1-2 min | Full code analysis |
| **Docker Build** | ~1-3 min | Multi-stage Dockerfile build |

> **Tip for demos**: Use `SKIP_SECURITY_SCANS=true` for fast builds during class. Enable for full pipeline demos.
> Security scan failures mark the build as **UNSTABLE** (not FAILED) so the pipeline continues.

---

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/todos` | List all todos |
| `GET` | `/api/v1/todos/{id}` | Get todo by ID |
| `POST` | `/api/v1/todos` | Create a todo |
| `PUT` | `/api/v1/todos/{id}` | Update a todo |
| `DELETE` | `/api/v1/todos/{id}` | Delete a todo |
| `GET` | `/health` | Health check |

---

## Quick Start (Local)

```bash
cd todos-service
mvn clean verify -B        # Build & test
mvn spring-boot:run        # Run locally on :8080
curl http://localhost:8080/health
curl http://localhost:8080/api/v1/todos
```

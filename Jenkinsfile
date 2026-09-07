pipeline {
    agent any

    environment {
        IMAGE_NAME       = 'todos-service'
        IMAGE_TAG        = "${BUILD_NUMBER}"
        NEXUS_URL        = 'http://localhost:8082'
        NEXUS_REPO       = 'maven-snapshots'
        NEXUS_DOCKER_REG = 'localhost:8083'
        SONARQUBE_URL    = 'http://localhost:8085'
        STAGING_PORT     = '9090'
        PRODUCTION_PORT  = '8084'
    }

    parameters {
        choice(name: 'DEPLOY_ENV', choices: ['none', 'staging', 'production'], description: 'Target environment to deploy')
        booleanParam(name: 'SKIP_TESTS', defaultValue: false, description: 'Skip test execution')
        booleanParam(name: 'SKIP_SECURITY_SCANS', defaultValue: false, description: 'Skip all security scans')
    }

    // tools {
    //     maven 'Maven-3'    // Uncomment if Maven is configured in Jenkins Global Tool Config
    //     jdk 'JDK-17'       // Uncomment if JDK is configured in Jenkins Global Tool Config
    // }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timestamps()
        timeout(time: 60, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    triggers {
        pollSCM('H/5 * * * *')
    }

    stages {

        // ═══════════════════════════════════════════════════════════
        // STAGE 1: Checkout
        // WHY: Pull latest code from SCM. Every pipeline starts here.
        // ═══════════════════════════════════════════════════════════
        stage('Checkout') {
            steps {
                checkout scm
                sh 'chmod +x ./mvnw'
                sh 'echo "Branch: ${GIT_BRANCH} | Commit: ${GIT_COMMIT}"'
            }
        }

        // ═══════════════════════════════════════════════════════════
        // STAGE 2: Secrets Detection
        // WHY: Catch leaked credentials BEFORE they reach production.
        //       Uses Gitleaks to scan full git history.
        // TOOL: Gitleaks (Docker-based)
        // ═══════════════════════════════════════════════════════════
        stage('Secrets Detection') {
            when { expression { !params.SKIP_SECURITY_SCANS } }
            steps {
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                sh '''
                    echo "=== Running Gitleaks ==="
                    mkdir -p reports
                    docker run --rm -v $(pwd):/repo \
                        zricethezav/gitleaks:v8.18.0 \
                        detect --source /repo \
                        --config /repo/security-config/gitleaks.toml \
                        --report-path /repo/reports/gitleaks-report.json \
                        --report-format json \
                        --verbose
                '''
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'reports/gitleaks-report.json', allowEmptyArchive: true
                }
            }
        }

        // ═══════════════════════════════════════════════════════════
        // STAGE 3: Build & Test
        // WHY: Compile code, run unit tests, generate coverage.
        //       'mvn clean verify' does compile + test + verify in one.
        // REPORTS: JUnit XML (test results), JaCoCo (code coverage)
        // ═══════════════════════════════════════════════════════════
        stage('Build & Test') {
            steps {
                sh "./mvnw clean ${params.SKIP_TESTS ? 'compile' : 'verify'} -B"
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
                    jacoco(execPattern: 'target/jacoco.exec')
                }
            }
        }

        // ═══════════════════════════════════════════════════════════
        // STAGE 4: SAST & SCA (parallel)
        // WHY: Run static analysis and dependency checks simultaneously
        //       to save time. Both are read-only analysis tasks.
        // SAST: Static Application Security Testing (SonarQube)
        //       — finds bugs, code smells, and security vulnerabilities
        // SCA:  Software Composition Analysis (OWASP Dependency-Check)
        //       — finds known CVEs in third-party dependencies
        // ═══════════════════════════════════════════════════════════
        stage('SAST & SCA') {
            when { expression { !params.SKIP_SECURITY_SCANS } }
            parallel {
                stage('SAST - SonarQube') {
                    steps {
                        catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                            withSonarQubeEnv('SonarQube') {
                                withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
                                    sh """
                                        ./mvnw sonar:sonar \
                                            -Dsonar.projectKey=${IMAGE_NAME} \
                                            -Dsonar.projectName=${IMAGE_NAME} \
                                            -Dsonar.host.url=${SONARQUBE_URL} \
                                            -Dsonar.login=${SONAR_TOKEN} \
                                            -B
                                    """
                                }
                            }
                        }
                    }
                }
                stage('SCA - OWASP Dependency-Check') {
                    steps {
                        catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                            sh './mvnw org.owasp:dependency-check-maven:check -DdataDirectory=/opt/dependency-check-data -B'
                        }
                    }
                    post {
                        always {
                            dependencyCheckPublisher pattern: 'target/dependency-check-report.json'
                            archiveArtifacts artifacts: 'target/dependency-check-report.*', allowEmptyArchive: true
                        }
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════════
        // STAGE 5: Quality Gate
        // WHY: SonarQube evaluates code against quality rules.
        //       Pipeline aborts if quality gate fails — enforcing
        //       code quality standards before artifacts are built.
        // ═══════════════════════════════════════════════════════════
        stage('Quality Gate') {
            when { expression { !params.SKIP_SECURITY_SCANS } }
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        // ═══════════════════════════════════════════════════════════
        // STAGE 6: Package JAR & Build Docker Image
        // WHY: Create deployable artifacts. Multi-stage Dockerfile
        //       keeps the image small and secure (no build tools).
        // ═══════════════════════════════════════════════════════════
        stage('Package JAR & Build Docker Image') {
            steps {
                sh './mvnw package -DskipTests -B'
                sh "docker build -t ${IMAGE_NAME}:${IMAGE_TAG} -t ${IMAGE_NAME}:latest ."
            }
        }

        // ═══════════════════════════════════════════════════════════
        // STAGE 7: Vulnerability Scan (JAR & Image)
        // WHY: Scan the built artifacts (not just source code) for
        //       vulnerabilities. Catches issues in base images and
        //       runtime dependencies that SAST/SCA might miss.
        // TOOL: Trivy (placeholder commands for teaching)
        // ═══════════════════════════════════════════════════════════
        stage('Vulnerability Scan') {
            when { expression { !params.SKIP_SECURITY_SCANS } }
            parallel {
                stage('Scan JAR (Trivy)') {
                    steps {
                        sh '''
                            echo "=== Scanning JAR for vulnerabilities ==="
                            echo "trivy fs --scanners vuln target/*.jar --format json --output reports/trivy-jar-report.json"
                            echo "trivy fs --scanners vuln target/*.jar --format template --template @contrib/html.tpl --output reports/trivy-jar-report.html"
                            echo "JAR scan complete."
                        '''
                    }
                }
                stage('Scan Docker Image (Trivy)') {
                    steps {
                        sh """
                            echo "=== Scanning Docker Image for vulnerabilities ==="
                            echo "trivy image --severity HIGH,CRITICAL --format json --output reports/trivy-image-report.json ${IMAGE_NAME}:${IMAGE_TAG}"
                            echo "trivy image --severity HIGH,CRITICAL --format template --template @contrib/html.tpl --output reports/trivy-image-report.html ${IMAGE_NAME}:${IMAGE_TAG}"
                            echo "Docker image scan complete."
                        """
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════════
        // STAGE 8: DAST - OWASP ZAP
        // WHY: Dynamic Application Security Testing scans the RUNNING
        //       application for vulnerabilities like XSS, SQL injection,
        //       missing security headers. Catches issues that only
        //       appear at runtime — SAST can't find these.
        // FLOW: Start app in container → Run ZAP against it → Teardown
        // TOOL: OWASP ZAP (placeholder commands for teaching)
        // ═══════════════════════════════════════════════════════════
        stage('DAST - OWASP ZAP') {
            when { expression { !params.SKIP_SECURITY_SCANS } }
            steps {
                sh """
                    echo "=== Starting application for DAST scanning ==="
                    echo "docker run -d --name ${IMAGE_NAME}-dast -p 8888:8080 ${IMAGE_NAME}:${IMAGE_TAG}"
                    echo "Waiting for application to start..."
                    echo "sleep 15"

                    echo "=== Running OWASP ZAP Baseline Scan ==="
                    echo "docker run --rm --network host -v \\\$(pwd)/reports:/zap/wrk:rw \\\\
                        ghcr.io/zaproxy/zaproxy:stable zap-baseline.py \\\\
                        -t http://localhost:8888 \\\\
                        -r zap-report.html \\\\
                        -J zap-report.json \\\\
                        -c security-config/zap-rules.tsv || true"

                    echo "=== Tearing down DAST target ==="
                    echo "docker stop ${IMAGE_NAME}-dast && docker rm ${IMAGE_NAME}-dast"
                    echo "DAST scan complete."
                """
            }
            post {
                always {
                    archiveArtifacts artifacts: 'reports/zap-report.*', allowEmptyArchive: true
                }
            }
        }

        // ═══════════════════════════════════════════════════════════
        // STAGE 9: Security Reports Dashboard
        // WHY: Consolidate all security reports into Jenkins sidebar
        //       for easy access. Teams can review all findings in one
        //       place without digging through build logs.
        // PLUGIN: HTML Publisher
        // ═══════════════════════════════════════════════════════════
        stage('Security Reports') {
            when { expression { !params.SKIP_SECURITY_SCANS } }
            steps {
                sh 'mkdir -p reports'
                echo '=== Publishing Security Reports ==='
            }
            post {
                always {
                    publishHTML(target: [
                        allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true,
                        reportDir: 'reports', reportFiles: 'gitleaks-report.json',
                        reportName: 'Gitleaks Secrets Report'
                    ])
                    publishHTML(target: [
                        allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true,
                        reportDir: 'target', reportFiles: 'dependency-check-report.html',
                        reportName: 'OWASP Dependency-Check Report'
                    ])
                    publishHTML(target: [
                        allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true,
                        reportDir: 'reports', reportFiles: 'zap-report.html',
                        reportName: 'OWASP ZAP DAST Report'
                    ])
                    publishHTML(target: [
                        allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true,
                        reportDir: 'reports', reportFiles: 'trivy-jar-report.html',
                        reportName: 'Trivy JAR Vulnerability Report'
                    ])
                    publishHTML(target: [
                        allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true,
                        reportDir: 'reports', reportFiles: 'trivy-image-report.html',
                        reportName: 'Trivy Image Vulnerability Report'
                    ])
                    archiveArtifacts artifacts: 'reports/**', allowEmptyArchive: true
                }
            }
        }

        // ═══════════════════════════════════════════════════════════
        // STAGE 10: Publish JAR to Nexus
        // WHY: Store versioned JAR in artifact repository for
        //       traceability and rollback capability.
        // ═══════════════════════════════════════════════════════════
        stage('Publish JAR to Nexus') {
            when { branch 'main' }
            steps {
                withCredentials([usernamePassword(credentialsId: 'nexus-credentials', usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASS')]) {
                    sh """
                        SETTINGS=\$(mktemp)
                        chmod 600 \$SETTINGS
                        cat > \$SETTINGS <<XMLEOF
<settings>
  <servers>
    <server>
      <id>nexus-releases</id>
      <username>${NEXUS_USER}</username>
      <password>${NEXUS_PASS}</password>
    </server>
  </servers>
</settings>
XMLEOF
                        ./mvnw deploy \
                            -DskipTests \
                            -DaltDeploymentRepository=nexus-releases::default::${NEXUS_URL}/repository/${NEXUS_REPO}/ \
                            -s \$SETTINGS -B
                        rm -f \$SETTINGS
                    """
                }
            }
        }

        // ═══════════════════════════════════════════════════════════
        // STAGE 11: Push Docker Image to Nexus
        // WHY: Store Docker image in private registry. Ensures
        //       deployments always pull from a trusted source.
        // ═══════════════════════════════════════════════════════════
        stage('Push Docker Image to Nexus') {
            when { branch 'main' }
            steps {
                withCredentials([usernamePassword(credentialsId: 'nexus-credentials', usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASS')]) {
                    sh """
                        echo \${NEXUS_PASS} | docker login -u \${NEXUS_USER} --password-stdin ${NEXUS_DOCKER_REG}
                        docker tag ${IMAGE_NAME}:${IMAGE_TAG} ${NEXUS_DOCKER_REG}/${IMAGE_NAME}:${IMAGE_TAG}
                        docker tag ${IMAGE_NAME}:latest ${NEXUS_DOCKER_REG}/${IMAGE_NAME}:latest
                        docker push ${NEXUS_DOCKER_REG}/${IMAGE_NAME}:${IMAGE_TAG}
                        docker push ${NEXUS_DOCKER_REG}/${IMAGE_NAME}:latest
                    """
                }
            }
        }

        // ═══════════════════════════════════════════════════════════
        // STAGE 12: Deploy to Staging
        // WHY: Staging mirrors production. Test in a real environment
        //       before touching production. Automatic after artifacts
        //       are published — no manual intervention needed.
        // ═══════════════════════════════════════════════════════════
        stage('Deploy to Staging') {
            when {
                allOf {
                    branch 'main'
                    expression { params.DEPLOY_ENV == 'staging' || params.DEPLOY_ENV == 'production' }
                }
            }
            steps {
                sh """
                    echo "=== Deploying to STAGING ==="
                    docker stop ${IMAGE_NAME}-staging || true
                    docker rm ${IMAGE_NAME}-staging || true
                    docker run -d --name ${IMAGE_NAME}-staging \
                        -p ${STAGING_PORT}:8080 \
                        -e SPRING_PROFILES_ACTIVE=staging \
                        ${IMAGE_NAME}:${IMAGE_TAG}
                    echo "Staging deployment complete on port ${STAGING_PORT}"
                """
            }
        }

        // ═══════════════════════════════════════════════════════════
        // STAGE 13: Smoke Tests
        // WHY: Verify the deployed application is actually running
        //       and responding. A basic health check with retries
        //       catches deployment failures before users do.
        // ═══════════════════════════════════════════════════════════
        stage('Smoke Tests') {
            when {
                allOf {
                    branch 'main'
                    expression { params.DEPLOY_ENV == 'staging' || params.DEPLOY_ENV == 'production' }
                }
            }
            steps {
                sh """
                    echo "=== Running Smoke Tests against Staging ==="
                    for i in 1 2 3 4 5 6; do
                        STATUS=\$(curl -s -o /dev/null -w "%{http_code}" http://localhost:${STAGING_PORT}/health || echo "000")
                        echo "Attempt \$i: HTTP \$STATUS"
                        if [ "\$STATUS" = "200" ]; then
                            echo "Smoke test PASSED"
                            exit 0
                        fi
                        sleep 10
                    done
                    echo "Smoke test FAILED - application did not respond with 200"
                    exit 1
                """
            }
        }

        // ═══════════════════════════════════════════════════════════
        // STAGE 14: Manual Approval
        // WHY: Human gate before production. Gives teams time to
        //       review staging, check reports, and make a go/no-go
        //       decision. Times out after 30 minutes to avoid
        //       blocking the build queue forever.
        // ═══════════════════════════════════════════════════════════
        stage('Approval for Production') {
            when {
                allOf {
                    branch 'main'
                    expression { params.DEPLOY_ENV == 'production' }
                }
            }
            steps {
                timeout(time: 30, unit: 'MINUTES') {
                    input message: "Deploy ${IMAGE_NAME}:${IMAGE_TAG} to PRODUCTION?",
                          ok: 'Deploy to Production',
                          submitter: 'admin,devops'
                }
            }
        }

        // ═══════════════════════════════════════════════════════════
        // STAGE 15: Deploy to Production
        // WHY: The final destination. Only reached after all scans
        //       pass, staging works, smoke tests pass, and a human
        //       approves. This is the safest path to production.
        // ═══════════════════════════════════════════════════════════
        stage('Deploy to Production') {
            when {
                allOf {
                    branch 'main'
                    expression { params.DEPLOY_ENV == 'production' }
                }
            }
            steps {
                sh """
                    echo "=== Deploying to PRODUCTION ==="
                    docker stop ${IMAGE_NAME}-prod || true
                    docker rm ${IMAGE_NAME}-prod || true
                    docker run -d --name ${IMAGE_NAME}-prod \
                        -p ${PRODUCTION_PORT}:8080 \
                        -e SPRING_PROFILES_ACTIVE=production \
                        ${IMAGE_NAME}:${IMAGE_TAG}
                    echo "Production deployment complete on port ${PRODUCTION_PORT}"
                """
                sh """
                    git tag -a "v${IMAGE_TAG}" -m "Production release v${IMAGE_TAG}"
                    git push origin "v${IMAGE_TAG}"
                """
            }
        }

        // ═══════════════════════════════════════════════════════════
        // STAGE 16: Rollback (Placeholder)
        // WHY: If production deploy goes wrong, roll back to the
        //       previous known-good version. This is a placeholder
        //       showing the pattern — in real pipelines you'd track
        //       the last successful image tag.
        // ═══════════════════════════════════════════════════════════
        stage('Rollback') {
            when {
                allOf {
                    branch 'main'
                    expression { params.DEPLOY_ENV == 'production' }
                }
            }
            steps {
                sh """
                    echo "=== Rollback Plan ==="
                    echo "If production deploy fails, run:"
                    echo "  docker stop ${IMAGE_NAME}-prod"
                    echo "  docker rm ${IMAGE_NAME}-prod"
                    echo "  docker run -d --name ${IMAGE_NAME}-prod -p ${PRODUCTION_PORT}:8080 ${NEXUS_DOCKER_REG}/${IMAGE_NAME}:<PREVIOUS_TAG>"
                    echo ""
                    echo "Previous successful tags:"
                    echo "  git tag -l 'v*' --sort=-version:refname | head -5"
                    echo ""
                    echo "To rollback via Jenkins: re-run a previous green build."
                """
            }
        }

        // ═══════════════════════════════════════════════════════════
        // STAGE 17: Build Summary Dashboard
        // WHY: A single view of everything that happened in this
        //       build. Makes it easy to review results without
        //       scrolling through hundreds of lines of logs.
        // ═══════════════════════════════════════════════════════════
        stage('Build Summary') {
            steps {
                sh """
                    echo ""
                    echo "╔════════════════════════════════════════════════════════════╗"
                    echo "║               BUILD SUMMARY DASHBOARD                     ║"
                    echo "╠════════════════════════════════════════════════════════════╣"
                    echo "║ Image       : ${IMAGE_NAME}:${IMAGE_TAG}                  "
                    echo "║ Branch      : \${GIT_BRANCH}                               "
                    echo "║ Commit      : \${GIT_COMMIT}                               "
                    echo "║ Build URL   : ${BUILD_URL}                                 "
                    echo "║ Parameters  : DEPLOY=${params.DEPLOY_ENV}                  "
                    echo "╠════════════════════════════════════════════════════════════╣"
                    echo "║ SECURITY SCANS                                            ║"
                    echo "║   Secrets   : Gitleaks    (see Gitleaks Report)            "
                    echo "║   SAST      : SonarQube   (see SonarQube Dashboard)       "
                    echo "║   SCA       : OWASP DC    (see Dependency-Check Report)   "
                    echo "║   DAST      : OWASP ZAP   (see ZAP Report)               "
                    echo "║   Container : Trivy        (see Trivy Reports)            "
                    echo "╠════════════════════════════════════════════════════════════╣"
                    echo "║ ARTIFACTS                                                 ║"
                    echo "║   JAR       : Nexus ${NEXUS_URL}                          "
                    echo "║   Image     : ${NEXUS_DOCKER_REG}/${IMAGE_NAME}:${IMAGE_TAG}"
                    echo "╠════════════════════════════════════════════════════════════╣"
                    echo "║ DEPLOYMENTS                                               ║"
                    echo "║   Staging   : http://localhost:${STAGING_PORT}             "
                    echo "║   Production: http://localhost:${PRODUCTION_PORT}          "
                    echo "╚════════════════════════════════════════════════════════════╝"
                    echo ""
                """
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // POST: Runs after all stages complete (success or failure)
    // WHY: Cleanup resources, notify the team, and leave the
    //       workspace clean for the next build.
    // ═══════════════════════════════════════════════════════════
    post {
        always {
            sh """
                docker rmi ${IMAGE_NAME}:${IMAGE_TAG} ${NEXUS_DOCKER_REG}/${IMAGE_NAME}:${IMAGE_TAG} || true
                docker rmi ${IMAGE_NAME}:latest ${NEXUS_DOCKER_REG}/${IMAGE_NAME}:latest || true
            """
            cleanWs()
        }
        success {
            script {
                try {
                    slackSend(channel: '#builds', color: 'good',
                        message: "SUCCESS: ${IMAGE_NAME}:${IMAGE_TAG} - ${BUILD_URL}")
                } catch (Throwable t) {
                    echo "Slack notification skipped: ${t.message}"
                }
            }
        }
        failure {
            script {
                try {
                    slackSend(channel: '#builds', color: 'danger',
                        message: "FAILED: ${IMAGE_NAME}:${IMAGE_TAG} - ${BUILD_URL}")
                } catch (Throwable t) {
                    echo "Slack notification skipped: ${t.message}"
                }
            }
            emailext(
                subject: "FAILED: ${IMAGE_NAME} Build #${BUILD_NUMBER}",
                body: "Build failed. Check: ${BUILD_URL}",
                to: 'devops@example.com'
            )
        }
        unstable {
            script {
                try {
                    slackSend(channel: '#builds', color: 'warning',
                        message: "UNSTABLE: ${IMAGE_NAME}:${IMAGE_TAG} - ${BUILD_URL}")
                } catch (Throwable t) {
                    echo "Slack notification skipped: ${t.message}"
                }
            }
        }
    }
}

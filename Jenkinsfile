// Pipeline from SCM, branch development. Only run reviewed code with credentials.
def command(String linux, String windows, boolean allowFailure = false) {
    int status = isUnix() ?
        sh(script: linux, returnStatus: true)
        : bat(script: "@echo off\r\n${windows}", returnStatus: true)
    if (status != 0 && !allowFailure) {
        error("Comando fallido (exit ${status}); revisar Console Output.")
    }
    return status
}

def maven(String arguments, boolean allowFailure = false) {
    int status
    dir('backend') {
        status = command("bash ./mvnw ${arguments}", "call mvnw.cmd ${arguments}", allowFailure)
    }
    return status
}

pipeline {
    agent any
    tools {
        jdk 'JAVA'
    }
    options {
        skipDefaultCheckout(true)
        disableConcurrentBuilds()
        timestamps()
        timeout(time: 35, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20', artifactNumToKeepStr: '10'))
    }
    triggers {
        // Works even when local Jenkins cannot receive an Internet webhook.
        pollSCM('H/5 * * * *')
    }
    environment {
        CI_PROVIDER = 'jenkins'
        SONAR_HOST_URL = 'http://localhost:9000'
        TEST_DB_USER = 'convocatorias'
        TEST_DB_PASSWORD = 'convocatorias'
        TZ = 'America/Lima'
    }
    stages {
        stage('Git Checkout') {
            steps {
                script {
                    env.JENKINS_BACKEND_CHECKOUT_READY = 'false'
                    def revision = checkout scm
                    env.GIT_COMMIT = revision.GIT_COMMIT
                    // Clear only generated output; never publish a previous build's reports.
                    dir('backend/target') {
                        deleteDir()
                    }
                    env.JENKINS_BACKEND_CHECKOUT_READY = 'true'
                    echo "Commit: ${env.GIT_COMMIT}"
                }
            }
        }
        stage('Preparar herramientas') {
            steps {
                script {
                    command('java -version && python3 --version && docker version',
                            'java -version && python --version && docker version')
                    command("python3 -m unittest discover -s tools -p 'test_*.py'",
                            'python -m unittest discover -s tools -p "test_*.py"')
                    retry(3) {
                        // Retry only Maven startup/download, not tests or compilation.
                        maven('-B -ntp --version')
                    }
                }
            }
        }
        stage('Construccion Maven') {
            steps {
                script {
                    maven('-B -ntp -Pquality clean compile')
                }
            }
        }
        stage('PostgreSQL aislado') {
            steps {
                script {
                    env.TEST_DB_URL = isUnix() ?
                        sh(script: 'python3 tools/jenkins_ci.py db-start', returnStdout: true).trim()
                        : bat(script: '@echo off\r\npython tools/jenkins_ci.py db-start', returnStdout: true).trim()
                }
            }
        }
        stage('Pruebas JUnit y Mockito + JaCoCo') {
            steps {
                script {
                    int tests = maven('-B -ntp -Pquality verify', true)
                    int report = command('python3 tools/ci_backend_report.py',
                                         'python tools/ci_backend_report.py', true)
                    if (tests != 0 || report != 0) {
                        error('Pruebas o evidencia de cobertura fallidas.')
                    }
                }
            }
            post {
                always {
                    junit testResults: 'backend/target/surefire-reports/TEST-*.xml',
                          allowEmptyResults: false
                }
            }
        }
        stage('Preparar analisis Java') {
            steps {
                script {
                    maven('-B -ntp dependency:copy-dependencies -DincludeScope=test -DoutputDirectory=target/sonar-libraries')
                }
            }
        }
        stage('SonarQube y Quality Gate') {
            steps {
                withCredentials([string(credentialsId: 'sonarqube-token', variable: 'SONAR_TOKEN')]) {
                    script {
                        command('python3 tools/jenkins_ci.py sonar-check',
                                'python tools/jenkins_ci.py sonar-check')
                        env.SCANNER_HOME = tool name: 'sonar-scanner',
                            type: 'hudson.plugins.sonar.SonarRunnerInstallation'
                        // Never interpolate SONAR_TOKEN into Groovy or command arguments.
                        int scan = command(
                            '"$SCANNER_HOME/bin/sonar-scanner" -Dsonar.scm.revision="$GIT_COMMIT" -Dsonar.java.jdkHome="$JAVA_HOME"',
                            'call "%SCANNER_HOME%\\bin\\sonar-scanner.bat" "-Dsonar.scm.revision=%GIT_COMMIT%" "-Dsonar.java.jdkHome=%JAVA_HOME%"',
                            true)
                        env.SCANNER_OUTCOME = scan == 0 ? 'success' : 'failure'
                        int evidence = command('python3 tools/ci_sonar.py collect',
                                               'python tools/ci_sonar.py collect', true)
                        if (scan != 0 || evidence != 0) {
                            error('Analisis Sonar o Quality Gate fallido; revisar artefactos.')
                        }
                    }
                }
            }
        }
        stage('Publicar JAR') {
            steps {
                archiveArtifacts artifacts: 'backend/target/convocatorias-backend-*.jar',
                                 fingerprint: true, allowEmptyArchive: false
            }
        }
        stage('Probar JAR en 8083') {
            steps {
                script {
                    command('python3 tools/jenkins_ci.py smoke-test',
                            'python tools/jenkins_ci.py smoke-test')
                }
            }
        }
    }
    post {
        always {
            script {
                // Remove only this build's labelled PostgreSQL container.
                if (env.JENKINS_BACKEND_CHECKOUT_READY == 'true') {
                    int cleanup = command('python3 tools/jenkins_ci.py db-stop',
                                          'python tools/jenkins_ci.py db-stop', true)
                    if (cleanup != 0) {
                        unstable('No se pudo retirar PostgreSQL; revisar Console Output.')
                    }
                    archiveArtifacts artifacts: 'backend/target/ci-summary.md,backend/target/surefire-reports/**,backend/target/site/jacoco/**,backend/target/sonar/report-task.txt,backend/target/sonar-evidence/**,backend/target/jenkins-evidence/**',
                                     allowEmptyArchive: true
                }
            }
        }
    }
}

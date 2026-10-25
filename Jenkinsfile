// AI Cal — build the latest app and test it. Builds start only by hand
// (Build with Parameters); nothing is triggered by pushes or timers.
//
//   Build with Parameters (APP_BRANCH, SUITE, TARGET)
//     -> checks out the latest commit of APP_BRANCH in the app repo
//     -> iPhone on USB?  yes: builds a signed .ipa, runs smoke + locator health on the iPhone
//                        no:  builds the simulator .app, runs them on the simulator
//     -> iPhone there but unusable (locked, UI automation refused, .ipa won't sign)?
//        the same suites run on the simulator instead
//     -> publishes JUnit results, failure triage, screenshots and the Allure report
//
// SUITE=regression runs the full suite instead of smoke (about 1.5 h on the
// iPhone, 1 h on the simulator).
// Device and signing details come from the framework's own config
// (src/test/resources/config.properties, devices/*.properties) via ci/*.sh.

// True when the iPhone refused a session during run <label>; the framework
// writes this file (base/DeviceAvailability.java) and skips the rest of that run.
def iphoneRefused(String label) {
    return fileExists("reports/${label}/device-unavailable.txt")
}

// Moves to the simulator after the iPhone could not be used. The iPhone's
// partial reports (and Allure results) are kept, archived, under
// reports/unusable-iphone/ but not counted — the simulator run is this build's result.
def fallBackToSimulator(String reason) {
    if (params.TARGET == 'iphone') {
        error("The iPhone could not be used: ${reason}")
    }
    echo "The iPhone could not be used (${reason}) — running on the simulator instead."
    sh '''
        mkdir -p reports/unusable-iphone
        for d in reports/iphone-*; do [ -d "$d" ] && mv "$d" reports/unusable-iphone/; done
        [ -d allure-results ] && mv allure-results reports/unusable-iphone/allure-results
        true
    '''
    env.FALLBACK = 'true'
    currentBuild.description = "${env.SUITE_RESOLVED} · app ${env.APP_COMMIT} · simulator (iPhone unusable)"
}

pipeline {
    agent any

    options {
        disableConcurrentBuilds()   // one simulator, one phone: never two runs at once
        buildDiscarder(logRotator(numToKeepStr: '30'))
        timeout(time: 4, unit: 'HOURS')
    }

    parameters {
        string(name: 'APP_REPO_URL', defaultValue: 'http://127.0.0.1/team-lead/ai-calorie-app.git',
                description: 'App repository to build')
        string(name: 'APP_BRANCH', defaultValue: 'development', description: 'App branch to build and test')
        string(name: 'APP_REPO_CREDENTIALS', defaultValue: 'gitlab-qa',
                description: 'Jenkins credentials ID for the app repo (blank: use this Mac\'s saved Git login)')
        choice(name: 'SUITE', choices: ['smoke', 'regression'],
                description: 'smoke = the key checks plus locator health (about 20 min); '
                        + 'regression = the full suite (about 1.5 h on the iPhone, 1 h on the simulator)')
        choice(name: 'TARGET', choices: ['auto', 'iphone', 'simulator'],
                description: 'auto = the iPhone on USB if there is one and it can be used, otherwise the simulator; '
                        + 'iphone = iPhone only (fail if it cannot be used); simulator = simulator only')
    }

    environment {
        PATH = "/opt/homebrew/bin:/usr/local/bin:${env.PATH}"
        LANG = 'en_US.UTF-8'
    }

    stages {
        stage('Checkout app') {
            steps {
                dir('app') {
                    script {
                        def scm = [url: params.APP_REPO_URL, branch: params.APP_BRANCH, poll: false, changelog: true]
                        if (params.APP_REPO_CREDENTIALS?.trim()) {
                            scm.credentialsId = params.APP_REPO_CREDENTIALS.trim()
                        }
                        git(scm)
                        env.APP_COMMIT = sh(script: 'git log -1 --format="%h %s"', returnStdout: true).trim()
                    }
                }
                echo "App commit: ${env.APP_COMMIT}"
            }
        }

        stage('Plan') {
            steps {
                script {
                    env.SUITE_RESOLVED = params.SUITE == 'regression' ? 'regression' : 'smoke'
                    env.SUITE_ARGS = env.SUITE_RESOLVED == 'smoke' ? '-Psmoke' : ''
                    env.IPHONE_UDID = params.TARGET == 'simulator' ? ''
                            : sh(script: 'bash -c "source ci/lib.sh; wired_device_udid"', returnStdout: true).trim()
                    if (params.TARGET == 'iphone' && !env.IPHONE_UDID) {
                        error('TARGET is iphone, but no iPhone is connected by USB (paired, cable)')
                    }
                    env.TARGET_RESOLVED = env.IPHONE_UDID ? 'iphone' : 'simulator'
                    env.FALLBACK = 'false'
                    currentBuild.description = "${env.SUITE_RESOLVED} · app ${env.APP_COMMIT} · ${env.TARGET_RESOLVED}"
                    echo "Suite: ${env.SUITE_RESOLVED}; runs on: ${env.TARGET_RESOLVED}" +
                            (env.IPHONE_UDID ? " (${env.IPHONE_UDID})" : ' (no iPhone on USB)')
                }
                sh 'rm -rf reports allure-results'
                sh 'ci/ensure-appium.sh'
            }
        }

        stage('Build app (iPhone)') {
            when { expression { env.TARGET_RESOLVED == 'iphone' } }
            steps {
                script {
                    try {
                        env.IPA = sh(script: 'ci/build-app.sh device app build/device', returnStdout: true).trim()
                        echo "Built ${env.IPA}"
                    } catch (hudson.AbortException err) {   // the script failed; an abort or timeout still stops the build
                        // Not the phone's fault: signing or the app's build broke. Flag it, still test the push.
                        if (params.TARGET != 'iphone') {
                            unstable('The signed .ipa could not be built — see build/device/build-*.log')
                        }
                        fallBackToSimulator('the signed .ipa could not be built')
                    }
                }
            }
        }

        stage('Test on iPhone') {
            when { expression { env.TARGET_RESOLVED == 'iphone' && env.FALLBACK != 'true' } }
            steps {
                sh 'ci/run-tests.sh "iphone-$SUITE_RESOLVED" $SUITE_ARGS -Diosautomation.executionMode=real "-Diosautomation.real.appPath=$IPA" "-Diosautomation.real.udid=$IPHONE_UDID"'
                script {
                    def suite = "iphone-${env.SUITE_RESOLVED}"
                    if (!iphoneRefused(suite)) {
                        sh 'ci/run-tests.sh iphone-health -Phealth -Diosautomation.executionMode=real "-Diosautomation.real.appPath=$IPA" "-Diosautomation.real.udid=$IPHONE_UDID"'
                    }
                    if (iphoneRefused(suite) || iphoneRefused('iphone-health')) {
                        fallBackToSimulator(sh(script: 'cat reports/iphone-*/device-unavailable.txt | head -1',
                                returnStdout: true).trim())
                    }
                }
            }
        }

        stage('Build app (simulator)') {
            when { expression { env.TARGET_RESOLVED == 'simulator' || env.FALLBACK == 'true' } }
            steps {
                script {
                    env.SIM_APP = sh(script: 'ci/build-app.sh simulator app build/simulator', returnStdout: true).trim()
                }
                echo "Built ${env.SIM_APP}"
            }
        }

        stage('Test on simulator') {
            when { expression { env.TARGET_RESOLVED == 'simulator' || env.FALLBACK == 'true' } }
            steps {
                sh 'ci/prepare-simulator.sh "$SIM_APP"'
                sh 'ci/run-tests.sh "simulator-$SUITE_RESOLVED" $SUITE_ARGS'
                sh 'ci/run-tests.sh simulator-health -Phealth'
            }
        }
    }

    post {
        always {
            // reports/<run>/ only: an unusable iPhone's partial run (reports/unusable-iphone/) is archived, not counted.
            junit(testResults: 'reports/*/surefire-reports/*.xml', allowEmptyResults: true)
            // The Allure report as one HTML file, downloadable from the build page and
            // easy to forward: it opens in any browser with no Jenkins login (ci/report-file.sh).
            sh 'ci/report-file.sh "reports/AI-Cal-Test-Report-build-$BUILD_NUMBER.html"'
            archiveArtifacts(artifacts: 'reports/**, build/*/build-*.log', allowEmptyArchive: true)
            script {
                // Failure triage per run: "UI changed" / "Possible bug" / "Environment".
                sh 'cat reports/*/failure-triage.txt 2>/dev/null || echo "[ci] no failures to triage"'
                try {
                    allure(results: [[path: 'allure-results']])
                } catch (Throwable err) {   // a missing plugin step is an Error, not an Exception
                    echo "Allure report skipped (install the Allure Jenkins plugin to get it): ${err.message}"
                }
            }
        }
    }
}

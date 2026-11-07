// Pipeline: API tests, k6 load test, UI + end-to-end tests.
// The Jenkins agent (Linux) needs JDK 25, Maven, Chrome, k6 and curl installed.
pipeline {
  agent any
  options {
    timestamps()
    timeout(time: 30, unit: 'MINUTES')
    disableConcurrentBuilds()
  }

  stages {
    stage('Checkout') {
      steps { checkout scm }
    }
    stage('API tests') {
      steps { sh 'mvn -B clean test -DsuiteFile=testng-api.xml -Dheadless=true' }
    }
    stage('k6 load test') {
      steps {
        sh '''
          mvn -B -q compile
          java -cp target/classes com.ganesna.wms.WarehouseApp > app.log 2>&1 &
          APP_PID=$!
          trap "kill $APP_PID" EXIT
          for i in $(seq 1 30); do curl -sf http://127.0.0.1:8080/ > /dev/null && break; sleep 1; done
          k6 run --summary-export=k6-summary.json -e BASE_URL=http://127.0.0.1:8080 k6/load-test.js
        '''
      }
    }
    stage('UI + end-to-end tests') {
      steps { sh 'mvn -B test -DsuiteFile=testng-ui.xml -Dheadless=true' }
    }
  }

  post {
    always {
      junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'
      archiveArtifacts artifacts: 'target/allure-results/**, k6-summary.json, app.log', allowEmptyArchive: true
      // needs the Allure Jenkins plugin:
      // allure includeProperties: false, results: [[path: 'target/allure-results']]
    }
    failure {
      echo 'Tests failed - add a mail or Slack step here to notify the team.'
    }
  }
}

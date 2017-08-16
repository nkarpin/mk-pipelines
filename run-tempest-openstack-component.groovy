/**
 *
 * Run tempest tests and trigger results processing pipeline
 *
 * Expected parameters:
 *   SALT_MASTER_URL              URL of Salt master
 *   SALT_MASTER_CREDENTIALS      Credentials to the Salt API
 *   TEST_TEMPEST_IMAGE           Docker image to run tempest
 *   TEST_TEMPEST_TARGET          Salt target to run tempest on
 *   TEST_TEMPEST_PATTERN         Tests to execute
 *   TEST_DOCKER_INSTALL          Whether install docker
 *   TESTRAIL                     Whether upload results to testrail or not
 *   TEST_MILESTONE               Product version for tests
 *   TEST_MODEL                   Salt model used in environment
 *   OPENSTACK_VERSION            Version of openstack which was tested
 *   PROC_RESULTS_JOB             Name of job for test results processing
 *   COMPONENT                    Name of openstack component being tested
 *
 */

common = new com.mirantis.mk.Common()
git = new com.mirantis.mk.Git()
salt = new com.mirantis.mk.Salt()
test = new com.mirantis.mk.Test()

// Define global variables
def saltMaster

node("python") {
    try {

        stage ('Connect to salt master') {
            // Connect to Salt master
            saltMaster = salt.connection(SALT_MASTER_URL, SALT_MASTER_CREDENTIALS)
        }

        def artifacts_dir = '_artifacts/'
        def log_dir = "/home/rally/rally_reports/${COMPONENT}"
        def reports_dir = "/root/rally_reports/${COMPONENT}"
        def date = sh(script: "date +%Y-%m-%d", returnStdout: true).trim()

        if (common.checkContains('TEST_DOCKER_INSTALL', 'true')) {
            test.install_docker(saltMaster, TEST_TEMPEST_TARGET)
        }

        stage('Run OpenStack tests') {
            test.runTempestTests(saltMaster, TEST_TEMPEST_IMAGE, TEST_TEMPEST_TARGET, TEST_TEMPEST_PATTERN, log_dir)
        }

        stage('Archive rally artifacts') {
            test.archiveRallyArtifacts(saltMaster, TEST_TEMPEST_TARGET, reports_dir)
        }

        stage('Processing results') {
            build(job: PROC_RESULTS_JOB, parameters: [
                [$class: 'StringParameterValue', name: 'TARGET_JOB', value: "${env.JOB_NAME}"],
                [$class: 'StringParameterValue', name: 'TARGET_BUILD_NUMBER', value: "${env.BUILD_NUMBER}"],
                [$class: 'BooleanParameterValue', name: 'TESTRAIL', value: TESTRAIL.toBoolean()],
                [$class: 'StringParameterValue', name: 'TEST_MILESTONE', value: TEST_MILESTONE],
                [$class: 'StringParameterValue', name: 'TEST_MODEL', value: TEST_MODEL],
                [$class: 'StringParameterValue', name: 'OPENSTACK_VERSION', value: OPENSTACK_VERSION],
                [$class: 'StringParameterValue', name: 'TEST_DATE', value: date]
            ])
        }

    } catch (Throwable e) {
        currentBuild.result = 'FAILURE'
        throw e
    }
}

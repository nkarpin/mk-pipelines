/**
 *
 * Test results processing pipeline
 *
 * Expected parameters:
 *   TARGET_JOB                   Name of the testing job
 *   TARGET_BUILD_NUMBER          Number of the testing build
 *   TEST_REPORTER_IMAGE          Docker image for testrail reporter
 *   TESTRAIL                     Whether upload results to testrail or not
 *   TEST_MILESTONE               Product version for tests
 *   TEST_MODEL                   Salt model used in environment
 *   OPENSTACK_VERSION            Version of Openstack being tested
 *   TEST_SUITE                   Testrail test suite
 *   TEST_PLAN                    Testrail test plan
 *   TEST_GROUP                   Testrail test group
 *   TESTRAIL_QA_CREDENTIALS      Credentials for upload to testrail
 *   TEST_DATE                    Date of test run
 *   FAIL_ON_TESTS                Whether to fail build on tests failures or not
 *
 */

common = new com.mirantis.mk.Common()
test = new com.mirantis.mk.Test()

node("docker") {
    try {
        testOutputDir = "${TARGET_JOB}-${TARGET_BUILD_NUMBER}"
        sh("mkdir -p ${testOutputDir}")

        //TODO: Implement support for stepler run artifacts
        stage('Get tests artifacts') {
            selector = [$class: 'SpecificBuildSelector', buildNumber: "${TARGET_BUILD_NUMBER}"];

            step ([$class: 'CopyArtifact',
                   projectName: TARGET_JOB,
                   selector: selector,
                   filter: '_artifacts/rally_reports.tar',
                   target: testOutputDir,
                   flatten: true]);

            dir(testOutputDir) {
                sh("tar -xf rally_reports.tar")
            }
        }

        report = sh(script: "find ${testOutputDir} -name *.xml", returnStdout: true).trim()
        reportPath = "${WORKSPACE}/${report}"

        if (TESTRAIL.toBoolean() == true) {
            stage('Upload tests results to Testrail'){

                def plan = TEST_PLAN ?: "${TEST_MILESTONE}-${TEST_MODEL}-${OPENSTACK_VERSION}-${TEST_DATE}"
                def group = TEST_GROUP ?: "${TEST_MODEL}"

                //ensures that we have up to date image on jenkins slave
                sh("doker pull ${TEST_REPORTER_IMAGE}")

                test.uploadResultsTestrail(reportPath, TEST_REPORTER_IMAGE, group, TESTRAIL_QA_CREDENTIALS,
                    plan, TEST_MILESTONE, TEST_SUITE)
            }
        }

        stage('Check tests results'){
            fileContents = new File(reportPath)
            parsed = new XmlParser().parse(fileContents)
            res = parsed['testsuite'][0].attributes()
            failures = res.failures.toInteger()

            test_info = """
                        Failed:  ${failures}
                        Errors:  ${res.errors}
                        Skipped: ${res.skipped}
                        Tests:   ${res.tests}
                        """
            println test_info

            if (failures > 0 && FAIL_ON_TESTS){
                error("${failures} TEMPEST TESTS HAVE FAILED")
            }
        }
    } catch (Throwable e) {
        currentBuild.result = 'FAILURE'
        throw e
    }
}
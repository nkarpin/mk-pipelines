/**

 * Launch system tests against new package.

 * Flow parameters:
 *   CREDENTIALS_ID
 *   EXTRA_FORMULAS
 *   FORMULAS_REVISION
 *   FORMULAS_SOURCE
 *   SALT_OPTS
 *   STACK_DEPLOY_JOB

**/
def common = new com.mirantis.mk.Common()
def gerrit = new com.mirantis.mk.Gerrit()

node {
    def cred = common.getCredentials(CREDENTIALS_ID, 'key')
    def gerritChange = gerrit.getGerritChange(cred.username, GERRIT_HOST, GERRIT_CHANGE_NUMBER, CREDENTIALS_ID, true)
    def deployJobName = STACK_DEPLOY_JOB
    def deployBuild
    def deployBuildNumber
    def testOutputDir

    stage('Trigger deploy job') {
        deployBuild = build(job: "${deployJobName}", parameters: [
            [$class: 'StringParameterValue', name: 'OPENSTACK_API_PROJECT', value: 'mcp-oscore'],
            [$class: 'StringParameterValue', name: 'STACK_TEST', value: ''],
            [$class: 'BooleanParameterValue', name: 'TEST_DOCKER_INSTALL', value: false]
        ])
    }

    deployBuildNumber = deployBuild.getNumber()
    testOutputDir = "output_${deployJobName}-${deployBuildNumber}"
    sh("mkdir -p ${testOutputDir}")

    stage('Get tests artifacts') {
        selector = [$class: 'SpecificBuildSelector', buildNumber: deployBuildNumber];

        step ([$class: 'CopyArtifact',
               projectName: deployJobName,
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

            uploadResultsTestrail(reportPath, TESTRAIL_REPORTER_IMAGE, TEST_GROUP, TESTRAIL_QA_CREDS,
                TEST_PLAN, TEST_MILESTONE, TEST_SUITE)
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

        if (failures > 0){
            error("${failures} TEMPEST TESTS HAVE FAILED")
        }
    }
}

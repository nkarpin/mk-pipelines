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

def uploadResultsTestrail(report, image, testGroup, credentialsId, plan, milestone, suite, type = 'sh', master = null, target = null) {
    def salt = new com.mirantis.mk.Salt()
    def common = new com.mirantis.mk.Common()
    creds = common.getPasswordCredentials(credentialsId)
    command =  "docker run --rm --net=host " +
                           "-v ${report}:/srv/report.xml " +
                           "-e TESTRAIL_USER=${creds.username} " +
                           "-e PASS=${creds.password.toString()} " +
                           "-e TESTRAIL_PLAN_NAME=${plan} " +
                           "-e TESTRAIL_MILESTONE=${milestone} " +
                           "-e TESTRAIL_SUITE=${suite} " +
                           "-e SHORT_TEST_GROUP=${testGroup} " +
                           "${image}"
    if (type == 'sh') {
      sh("${command}")
    }
    if (type == 'salt') {
      salt.cmdRun(master, "${target}", "${command}")
    }
}

node {
    //def cred = common.getCredentials(CREDENTIALS_ID, 'key')
    //def gerritChange = gerrit.getGerritChange(cred.username, GERRIT_HOST, GERRIT_CHANGE_NUMBER, CREDENTIALS_ID, true)
    def deployJobName = STACK_DEPLOY_JOB
    def deployBuild
    def deployBuildNumber
    def testOutputDir

    stage('Trigger deploy job') {
        deployBuild = build(job: "${deployJobName}", parameters: [
            [$class: 'StringParameterValue', name: 'OPENSTACK_API_PROJECT', value: 'mcp-oscore'],
            [$class: 'StringParameterValue', name: 'STACK_TEST', value: 'openstack'],
            [$class: 'StringParameterValue', name: 'TEST_TEMPEST_IMAGE', value: 'sandriichenko/rally_tempest_docker:docker_aio'],
           // [$class: 'StringParameterValue', name: 'TEST_TEMPEST_PATTERN', value: '63dc64e6-2e79-4fdf-868f-85500d308d66'],
            [$class: 'StringParameterValue', name: 'TEST_TEMPEST_PATTERN', value: 'set=smoke'],
            [$class: 'StringParameterValue', name: 'TEST_TEMPEST_TARGET', value: 'cfg*'],
            [$class: 'StringParameterValue', name: 'STACK_INSTALL', value: 'core,openstack'],
            [$class: 'StringParameterValue', name: 'STACK_TYPE', value: 'heat'],
            [$class: 'StringParameterValue', name: 'SALT_MASTER_URL', value: ''],
            [$class: 'BooleanParameterValue', name: 'STACK_DELETE', value: false],
            [$class: 'BooleanParameterValue', name: 'TEST_DOCKER_INSTALL', value: true]
        ])
    }

    deployBuildNumber = deployBuild.getNumber()
    testOutputDir = "output_${deployJobName}-${deployBuildNumber}"
    sh("mkdir -p ${testOutputDir}")

    stage('Get tests artifacts') {
        selector = [$class: 'SpecificBuildSelector', buildNumber: "${deployBuildNumber}"];

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

/**

 * Launch system tests against new package.

 * Flow parameters:
 *   CREDENTIALS_ID
 *   COMPONENT
 *   EXTRA_FORMULAS
 *   FORMULAS_REVISION
 *   FORMULAS_SOURCE
 *   GERRIT_CHECK
 *_  MILESTONE
 *   OPENSTACK_VERSION
 *   OPENSTACK_API_URL
 *   OPENSTACK_API_CREDENTIALS
 *   OPENSTACK_API_PROJECT
 *   OPENSTACK_API_PROJECT_DOMAIN
 *   OPENSTACK_API_PROJECT_ID
 *   OPENSTACK_API_USER_DOMAIN
 *   OPENSTACK_API_CLIENT
 *   OPENSTACK_API_VERSION
 *   SALT_MASTER_CREDENTIALS
 *   SALT_MASTER_URL
 *   SALT_OVERRIDES
 *   SALT_OPTS
 *   STACK_DEPLOY_JOB
 *   STACK_DELETE
 *   STACK_TEST_JOB
 *   STACK_TYPE
 *   TESTRAIL
 *   TEST_TEMPEST_TARGET
 *   TEST_TEMPEST_PATTERN
 *   TEST_MODEL


**/
def common = new com.mirantis.mk.Common()
def gerrit = new com.mirantis.mk.Gerrit()

node {
    try {

        if (GERRIT_CHECK == true) {
            def cred = common.getCredentials(CREDENTIALS_ID, 'key')
            def gerritChange = gerrit.getGerritChange(cred.username, GERRIT_HOST, GERRIT_CHANGE_NUMBER, CREDENTIALS_ID, true)
            def testrail = false
        } else {
            def testrail = TESTRAIL.toBoolean()
        }

        stage('Trigger deploy job') {
            deployBuild = build(job: STACK_DEPLOY_JOB, parameters: [
                [$class: 'StringParameterValue', name: 'OPENSTACK_API_PROJECT', value: 'mcp-oscore'],
                [$class: 'StringParameterValue', name: 'STACK_INSTALL', value: 'core,openstack,ovs'],
                [$class: 'StringParameterValue', name: 'STACK_TEST', value: ''],
                [$class: 'StringParameterValue', name: 'STACK_TYPE', value: STACK_TYPE],
                [$class: 'BooleanParameterValue', name: 'STACK_DELETE', value: false],
            ])
        }

        // get SALT_MASTER_URL
        def deployBuildParams = deployBuild.description.tokenize( ' ' )
        SALT_MASTER_URL = "http://${deployBuildParams[1]}:6969"
        STACK_NAME = "${deployBuildParams[0]}"
        echo "Salt API is accessible via ${SALT_MASTER_URL}"

        if (GERRIT_CHECK == true) {
            stage('Run Smoke tests') {
                build(job: STACK_TEST_JOB, parameters: [
                    [$class: 'StringParameterValue', name: 'SALT_MASTER_URL', value: SALT_MASTER_URL],
                    [$class: 'StringParameterValue', name: 'SALT_MASTER_CREDENTIALS', value: SALT_MASTER_CREDENTIALS],
                    [$class: 'StringParameterValue', name: 'TEST_TEMPEST_TARGET', value: TEST_TEMPEST_TARGET],
                    [$class: 'StringParameterValue', name: 'TEST_TEMPEST_PATTERN', value: 'set=smoke'],
                    [$class: 'BooleanParameterValue', name: 'TESTRAIL', value: false],
                    [$class: 'StringParameterValue', name: 'COMPONENT', value: 'smoke']
                ])
            }
        }

        stage("Run ${COMPONENT} tests") {
            build(job: STACK_TEST_JOB, parameters: [
                [$class: 'StringParameterValue', name: 'SALT_MASTER_URL', value: SALT_MASTER_URL],
                [$class: 'StringParameterValue', name: 'SALT_MASTER_CREDENTIALS', value: SALT_MASTER_CREDENTIALS],
                [$class: 'StringParameterValue', name: 'TEST_TEMPEST_TARGET', value: TEST_TEMPEST_TARGET],
                [$class: 'StringParameterValue', name: 'TEST_TEMPEST_PATTERN', value: TEST_TEMPEST_PATTERN],
                [$class: 'StringParameterValue', name: 'TEST_MILESTONE', value: MILESTONE],
                [$class: 'StringParameterValue', name: 'TEST_MODEL', value: TEST_MODEL],
                [$class: 'StringParameterValue', name: 'OPENSTACK_VERSION', value: OPENSTACK_VERSION],
                [$class: 'BooleanParameterValue', name: 'TESTRAIL', value: testrail],
                [$class: 'StringParameterValue', name: 'COMPONENT', value: COMPONENT]
            ])
        }
    } catch (Throwable e) {
        currentBuild.result = 'FAILURE'
        throw e
    } finally {

        //
        // Clean
        //
        if (common.validInputParam('STACK_DELETE') && STACK_DELETE.toBoolean() == true) {
            stage('Trigger cleanup job') {
                common.errorMsg('Stack cleanup job triggered')
                build(job: STACK_CLEANUP_JOB, parameters: [
                    [$class: 'StringParameterValue', name: 'STACK_NAME', value: STACK_NAME],
                    [$class: 'StringParameterValue', name: 'STACK_TYPE', value: STACK_TYPE],
                    [$class: 'StringParameterValue', name: 'OPENSTACK_API_URL', value: OPENSTACK_API_URL],
                    [$class: 'StringParameterValue', name: 'OPENSTACK_API_CREDENTIALS', value: OPENSTACK_API_CREDENTIALS],
                    [$class: 'StringParameterValue', name: 'OPENSTACK_API_PROJECT', value: OPENSTACK_API_PROJECT],
                    [$class: 'StringParameterValue', name: 'OPENSTACK_API_PROJECT_DOMAIN', value: OPENSTACK_API_PROJECT_DOMAIN],
                    [$class: 'StringParameterValue', name: 'OPENSTACK_API_PROJECT_ID', value: OPENSTACK_API_PROJECT_ID],
                    [$class: 'StringParameterValue', name: 'OPENSTACK_API_USER_DOMAIN', value: OPENSTACK_API_USER_DOMAIN],
                    [$class: 'StringParameterValue', name: 'OPENSTACK_API_CLIENT', value: OPENSTACK_API_CLIENT],
                    [$class: 'StringParameterValue', name: 'OPENSTACK_API_VERSION', value: OPENSTACK_API_VERSION]
                ])
            }
        }
    }
}

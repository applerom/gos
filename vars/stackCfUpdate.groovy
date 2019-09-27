#!groovy

// stackCfUpdate

import aws.AwsAccountClass
import stack.StackClass

def call( Map Var = [:] ) {
  def Stack             = Var.get('stack'         , StackClass.instance.get() )
  def StackType         = Var.get('stackType'     , '' )
  def StackName         = Var.get('stackName'     , Stack[StackType]['name'] )
  def StackFile         = Var.get('stackFile'     , Stack[StackType]['file'] )
  def StackTimeout      = Var.get('stackTimeout'  , Stack[StackType]['timeout'] )
  def AwsAccount        = Var.get('account'       , AwsAccountClass.instance.get() )
  def AwsAccountType    = Var.get('accountType'   , 'Target' )
  def AwsAccountId      = Var.get('accountId'     , AwsAccount[AwsAccountType]['id'    ] )
  def AwsAccountRegion  = Var.get('accountRegion' , AwsAccount[AwsAccountType]['region'] )
  def AwsAccountRole    = Var.get('accountRole'   , AwsAccount[AwsAccountType]['role'  ] )
  def AwsAccountExtId   = Var.get('externalId'    , AwsAccount[AwsAccountType].get('externalId','') )
  def ProjectName       = Var.get('projectName'       , StackClass.instance.getProjectName() )
  def ProjectConfigName = Var.get('projectConfigName' , StackClass.instance.getProjectConfigName() )
  def StackPollInterval = Var.get('stackPollInterval' , Stack[StackType].get('pollInterval', 5000 ) )
  def StackTerminationProtection = Var.get('stackTerminationProtection',
                                            Stack[StackType].get('terminationProtection', false ) )
  println 'stackCfUpdate: '+StackName+' to '+AwsAccountType+' in '+AwsAccountRegion

  def Params=[]
  
  script {
    Stack[StackType]['params'].each{ key, value -> Params.add( key+'='+value ) }
    println 'Params: '+Params.toString()

    withAWS(  roleAccount:  AwsAccountId,
              region:       AwsAccountRegion,
              role:         AwsAccountRole,
              externalId:   AwsAccountExtId )
    {
      Stack[StackType]['outputs'] = cfnUpdate (
        stack:            StackName,
        file:             StackFile,
        params:           Params,
        timeoutInMinutes: StackTimeout,
        pollInterval:     StackPollInterval,
        enableTerminationProtection: StackTerminationProtection,
      )
    }
    println 'Created stack outputs: ' + Stack[StackType]['outputs'].toString()

    Stack[StackType]['status'] = 'created'
    Stack[StackType]['AwsAccount'] = AwsAccount[AwsAccountType]

    orcfSave ( varName: StackName, varValue: Stack[StackType], projectName: ProjectName, projectConfigName: ProjectConfigName )

  } // end of script block

}

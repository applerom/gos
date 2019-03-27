#!groovy

// stackCfDelete

import aws.AwsAccountClass
import stack.StackClass

// *** new

def call( Map Var = [:] ) {
  def Stack           = Var.get('stack'       , StackClass.instance.get() )
  def StackType       = Var.get('stackType'   , '' )
  def StackName       = Var.get('stackName'   , (StackType) ? Stack[StackType]['name'] : 'stackType or stackName must be defined' )
  def AwsAccount      = Var.get('account'     , AwsAccountClass.instance.get() )
  def AwsAccountType  = Var.get('accountType' , 'Target' )
  def ProjectName       = Var.get('projectName'       , StackClass.instance.getProjectName() )
  def ProjectConfigName = Var.get('projectConfigName' , StackClass.instance.getProjectConfigName() )
  def StackPollInterval = Var.get('stackPollInterval' , Stack[StackType].get('pollInterval', 5000 ) )
  def TimeoutInMinutes = Var.get('timeoutInMinutes' , Stack[StackType].get('timeoutInMinutes', 30 ) )
  println 'stackCfDelete: '+StackName+' at '+AwsAccountType
  
  withAWS(  roleAccount:  AwsAccount[AwsAccountType]['id'],
            region:       AwsAccount[AwsAccountType]['region'],
            role:         AwsAccount[AwsAccountType]['role'],
            externalId:   AwsAccount[AwsAccountType].get('externalId','') )
  {
      cfnDelete( stack: StackName, pollInterval: StackPollInterval, timeoutInMinutes: TimeoutInMinutes )
  }
  script {
    try{
      def StackLoad = orcfLoad( varName: StackName, projectName: ProjectName, projectConfigName: ProjectConfigName )
      StackLoad['status'] = 'deleted'
      orcfSave ( varName: StackName, varValue: StackLoad, projectName: ProjectName, projectConfigName: ProjectConfigName )
    }
    catch ( all ) {
      println 'Error during setting status delete '+StackName+' at '+AwsAccountType
    }

  } // end of script block

}

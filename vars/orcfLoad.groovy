#!groovy

// orcfLoad

import aws.AwsAccountClass
import stack.StackClass


def call( Map Var = [:] ) {
  def AwsAccount        = Var.get('account'           , AwsAccountClass.instance.get() )
  def AwsAccountType    = Var.get('accountType'       , 'Shared' )
  def Store             = Var.get('store'             , 's3' )
  def VarName           = Var.get('varName'           , '' )
  def ProjectName       = Var.get('projectName'       , StackClass.instance.getProjectName() )
  def ProjectConfigName = Var.get('projectConfigName' , StackClass.instance.getProjectConfigName() )
  println 'orcfLoad: '+VarName+' for '+ProjectName+'('+ProjectConfigName+') from '+Store

  def Result
  def ResultJson
  def FileTmp     = ProjectName+'-'+ProjectConfigName+'-'+VarName+'-'+System.currentTimeMillis()+'.yml'
  def FileResult  = ProjectName+'/'+ProjectConfigName+'/'+VarName+'.yml'

  script {
    withAWS(  roleAccount:  AwsAccount[AwsAccountType]['id'],
              region:       AwsAccount[AwsAccountType]['region'],
              role:         AwsAccount[AwsAccountType]['role'],
              externalId:   AwsAccount[AwsAccountType].get('externalId','') )
    {
      if ( Store == 'secrets' )
      {
        ShCmd = 'aws secretsmanager get-secret-value --secret-id '+VarName
        Result = sh( script: ShCmd, returnStdout: true )
        ResultJson = readJSON ( text: Result )
        Result = readJSON ( text: ResultJson['SecretString'] )
      }
      else // 's3' by default
      {
        s3Download( bucket: AwsAccount['Shared']['bucket'],
                    path:   FileResult,
                    file:   FileTmp,
                    force:  true )
        Result = readYaml ( file: FileTmp )
        sh( 'rm -rf '+FileTmp )
      }      
    }
  }

  return Result
}

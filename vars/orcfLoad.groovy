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
  println 'orcfLoad v.0.4.0: '+VarName+' for '+ProjectName+'('+ProjectConfigName+') from '+Store

  def Result
  def ResultJson
  def FileTmp     = ProjectName+'-'+ProjectConfigName+'-'+VarName+'-'+System.currentTimeMillis()+'.yml'
  def FileResult  = ProjectName+'/'+ProjectConfigName+'/'+VarName+'.yml'

  script {
    withAWS(  roleAccount:  AwsAccount[AwsAccountType]['id'],
              region:       AwsAccount[AwsAccountType]['region'],
              role:         AwsAccount[AwsAccountType]['role'] )
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


// *** old

def call( String StackType ) {
  println 'orcfLoad v.0.1 (DEPRICATED - use v.0.2+ instead) '+StackType

  def AwsAccount  = AwsAccountClass.instance.get()
  def FileTmp     = 'Stack-'+StackType+'-'+System.currentTimeMillis()+'.yml'
  def Result

  script {
    withAWS(  roleAccount:  AwsAccount['Shared']['id'],
              region:       AwsAccount['Shared']['region'],
              role:         AwsAccount['Shared']['role'] )
    {
      s3Download( bucket: AwsAccount['Shared']['bucket'],
                  path:   env.JOB_NAME+'/'+StackType+'.yml',
                  file:   FileTmp,
                  force:  true )
    }
    Result = readYaml ( file: FileTmp )
  }

  // remove orcf datas
  sh( 'rm -rf '+FileTmp )

  return Result
}

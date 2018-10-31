#!groovy

// orcfSave

import aws.AwsAccountClass
import stack.StackClass

import groovy.json.JsonOutput


def call( Map Var = [:] ) {
  def AwsAccount        = Var.get('account'           , AwsAccountClass.instance.get() )
  def AwsAccountType    = Var.get('accountType'       , 'Shared' )
  def Store             = Var.get('store'             , 's3' )
  def VarName           = Var.get('varName'           , '' )
  def VarValue          = Var.get('varValue'          , '' )
  def ProjectName       = Var.get('projectname'       , StackClass.instance.getProjectName() )
  def ProjectConfigName = Var.get('projectConfigName' , StackClass.instance.getProjectConfigName() )
  println 'orcfSave v.0.5.0: '+VarName+' for '+ProjectName+'('+ProjectConfigName+') to '+Store

  def ShCmd
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
        Result = JsonOutput.toJson( VarValue )
        //writeJSON( file: FileJson, json: VarValue )
        writeFile( file: FileTmp, text: Result )
        ShCmd = 'aws secretsmanager update-secret --kms-key-id '+AwsAccount[AwsAccountType]['kms']+' --secret-id '+VarName+' --secret-string file://'+FileTmp
        Result = sh( script: ShCmd, returnStdout: true )
        ResultJson = readJSON ( text: Result )
        Result = ResultJson['VersionId']
      }

      else // 's3' by default
      {
        // write map Stack to yaml-file
        writeYaml( file: FileTmp, data: VarValue )
        // upload to Shared bucket
        s3Upload( bucket: AwsAccount['Shared']['bucket'], path: FileResult, file: FileTmp )
      }

    }
  }
  sh( 'rm -rf '+FileTmp )

  return Result
}


// *** old

def call( Stack, StackType ) {
  println 'orcfSave v.0.1 (DEPRICATED - use v.0.2+ instead): '+StackType

  def AwsAccount = AwsAccountClass.instance.get()
  def FileResult = env.JOB_NAME+'/'+StackType+'.yml'
  sh( 'rm -rf '+FileResult )
  // write map Stack to yaml-file
  writeYaml( file: FileResult, data: Stack[StackType] )
  // upload to Shared bucket

  withAWS(  roleAccount:  AwsAccount['Shared']['id'],
            region:       AwsAccount['Shared']['region'],
            role:         AwsAccount['Shared']['role'] )
  {
    s3Upload( bucket: AwsAccount['Shared']['bucket'], path: FileResult, file: FileResult )
  }
}

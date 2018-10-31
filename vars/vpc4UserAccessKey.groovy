#!groovy

// vpc4UserAccessKey

import aws.AwsAccountClass
import stack.StackClass

def call( Map Var = [:] ) {
  def AwsAccount      = Var.get('account'     , AwsAccountClass.instance.get() )
  def AwsAccountType  = Var.get('accountType' , 'Target' )
  def ActionType      = Var.get('actionType'  , 'create/update' ) // delete
  def UserName        = Var.get('userName'    , 'vpc4User' )
  println 'vpc4User '+UserName+' '+ActionType+' at '+AwsAccountType

  def Result
  def ResultJson

  def ShCmd
  def CheckVar

  def MapTmp = [:]

// *********************************************************************
script {
// +++++++++++++++++++++++++++ create/update +++++++++++++++++++++++++++
if ( ActionType == 'create/update' )
{
  withAWS(  roleAccount:  AwsAccount[AwsAccountType]['id'],
            region:       AwsAccount[AwsAccountType]['region'],
            role:         AwsAccount[AwsAccountType]['role'] )
  {
    // check keys for UserName
    ShCmd = 'aws iam list-access-keys --user-name '+UserName
    Result = sh( script: ShCmd, returnStdout: true )
    ResultJson = readJSON ( text: Result )
    println 'ResultJson: '+ResultJson.toString()
    if ( ResultJson['AccessKeyMetadata'] )
    {
      CheckVar = 'getkeys'
    }
    else
    {
      CheckVar = 'createkeys'
      // create keys for UserName
      ShCmd = 'aws iam create-access-key --user-name '+UserName
      Result = sh( script: ShCmd, returnStdout: true )
      ResultJson = readJSON ( text: Result )
      // store keys for UserName
      MapTmp = [ 'AccessKey': [:] ]
      MapTmp['AccessKey']['AccessKeyId'    ] = ResultJson['AccessKey']['AccessKeyId']
      MapTmp['AccessKey']['SecretAccessKey'] = ResultJson['AccessKey']['SecretAccessKey']
    }
  }
  if ( CheckVar == 'getkeys' )
  {
    // get keys for UserName
    try {
      MapTmp = orcfLoad ( varName: UserName+'AccessKey' )
    }
    catch ( all ) {
      println 'cannot load '+UserName+'AccessKey - delete it and create new'
      withAWS(  roleAccount:  AwsAccount[AwsAccountType]['id'],
                region:       AwsAccount[AwsAccountType]['region'],
                role:         AwsAccount[AwsAccountType]['role'] )
      {
        sh( 'aws iam delete-access-key --access-key-id '+ResultJson['AccessKeyMetadata']['AccessKeyId'][0]+' --user-name '+UserName )
      }
      MapTmp = vpc4UserAccessKey( accountType: AwsAccountType, userName: UserName )
    }
  }
  else
  {
    // store keys for UserName
    orcfSave ( varName: UserName+'AccessKey', varValue: MapTmp )
  }
} // end of ActionType == 'create/update' ++++++++++++++++++++++++++++++

// --------------------------- delete ----------------------------------
if ( ActionType == 'delete' )
{
  try
  {
    MapTmp = orcfLoad ( varName: UserName+'AccessKey' )
    withAWS(  roleAccount:  AwsAccount[AwsAccountType]['id'],
              region:       AwsAccount[AwsAccountType]['region'],
              role:         AwsAccount[AwsAccountType]['role'] )
    {
      sh( 'aws iam delete-access-key --access-key-id '+MapTmp['AccessKey']['AccessKeyId']+' --user-name '+UserName+' || true' )
    }
  }
  catch ( all )
  {
    println 'Error during delete access key'
  }
  
} // end of ActionType == 'delete' -------------------------------------

// === end of script block =============================================
}

return MapTmp

} // end of call


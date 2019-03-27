#!groovy

// vpc4KeyPair


import aws.AwsAccountClass
import stack.StackClass


def call( Map Var = [:] ) {
  def AwsAccount      = Var.get('account'     , AwsAccountClass.instance.get() )
  def AwsAccountType  = Var.get('accountType' , 'Target' )
  def ActionType      = Var.get('actionType'  , 'create/update' )
  def KeyPair         = Var.get('keyPair'     , '' )
  def KeyPairFile     = Var.get('keyPairFile' , AwsAccount[AwsAccountType]['name']+'/'+KeyPair+'.pem' )
  println 'vpc4KeyPair '+KeyPair+' '+ActionType+' at '+AwsAccountType

  def Result
  def ResultJson

  def ShCmd
  def CheckVar
  def KeyMaterialFile = KeyPair+'.pem'

// *********************************************************************
script {
// +++++++++++++++++++++++++++ create/update +++++++++++++++++++++++++++
if ( ActionType == 'create/update' )
{
  // *** check / create / upload KeyPair
  withAWS(  roleAccount:  AwsAccount[AwsAccountType]['id'],
            region:       AwsAccount[AwsAccountType]['region'],
            role:         AwsAccount[AwsAccountType]['role'],
            externalId:   AwsAccount[AwsAccountType].get('externalId','') )
  {
    // check if keypair already exists
    ShCmd = 'aws ec2 describe-key-pairs --key-name '+KeyPair+' || echo "nokey" '
    CheckVar = sh( script: ShCmd, returnStdout: true )
    println 'CheckVar: |'+CheckVar+'|'
    if ( CheckVar =~ /^nokey/ )
    {
      // create if keypair not exists
      ShCmd = 'aws ec2 create-key-pair --key-name '+KeyPair
      Result = sh( script: ShCmd, returnStdout: true )
      ResultJson = readJSON( text: Result )
      // store created keypair
      sh( 'rm -rf '+KeyMaterialFile )
      writeFile( file: KeyMaterialFile, text: ResultJson['KeyMaterial'] )
    }
  }
  // upload KeyPair to Shared bucket
  if ( CheckVar =~ /^nokey/ )
  {
    withAWS(  roleAccount:  AwsAccount['Shared']['id'],
              region:       AwsAccount['Shared']['region'],
              role:         AwsAccount['Shared']['role'],
              externalId:   AwsAccount['Shared'].get('externalId','') )
    {
      s3Upload( bucket: AwsAccount['Shared']['bucket'], path: KeyPairFile, file: KeyMaterialFile )
    }
  }
} // end of ActionType == 'create/update' ++++++++++++++++++++++++++++++

// --------------------------- delete ----------------------------------
if ( ActionType == 'import' )
{
  KeyMaterialFile = KeyPair+'.pub'
  withAWS( region: AwsAccount['Shared']['region'] )
  {
    s3Download( bucket: AwsAccount['Shared']['bucket'],
                path:   KeyPairFile,
                file:   KeyMaterialFile,
                force:  true )
  }
  withAWS(  roleAccount:  AwsAccount[AwsAccountType]['id'],
            region:       AwsAccount[AwsAccountType]['region'],
            role:         AwsAccount[AwsAccountType]['role'],
            externalId:   AwsAccount[AwsAccountType].get('externalId','') )
  {
    sh( 'aws ec2 import-key-pair --key-name '+KeyPair+' --public-key-material file://'+KeyMaterialFile+' || true' )
  }
} // end of ActionType == 'delete' -------------------------------------

// --------------------------- delete ----------------------------------
if ( ActionType == 'delete' )
{
  withAWS(  roleAccount:  AwsAccount[AwsAccountType]['id'],
            region:       AwsAccount[AwsAccountType]['region'],
            role:         AwsAccount[AwsAccountType]['role'],
            externalId:   AwsAccount[AwsAccountType].get('externalId','') )
  {
    sh( 'aws ec2 delete-key-pair --key-name '+KeyPair )
  }
} // end of ActionType == 'delete' -------------------------------------

// === end of script block =============================================
}

} // end of call

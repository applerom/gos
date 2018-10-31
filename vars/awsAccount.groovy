// awsAccount

import aws.AwsAccountClass
def AwsAccount = AwsAccountClass.instance.get()


def load( Map Var = [:] ) {
  String SecretName = Var.get('secret', 'AwsAccount')
  println 'awsAccount.load: '+SecretName

  def ShCmd
  def Result
  def ResultJson
  def ResultYaml

  withAWS(  roleAccount:  env.AwsSecretsAccount,
            region:       env.AwsSecretsRegion,
            role:         env.AwsSecretsRole )
  {
    script {
      ShCmd = 'aws secretsmanager get-secret-value --secret-id '+SecretName
      Result = sh( script: ShCmd, returnStdout: true )
      ResultJson = readJSON ( text: Result )
      ResultYaml = readYaml ( text: ResultJson['SecretString'] )
    }
  }
  return ResultYaml
}

import groovy.json.JsonOutput

def save( SomeMap ) {
  println 'awsAccount.save'
  // create default keys if not exist
  def DefaultKeys = [
    mapToSave: '',
    kms: env.AwsAccountKms,
    secret: 'AwsAccount',
  ].each{ key, value -> if( ! SomeMap.containsKey( key ) ) { SomeMap[key] = value } }

  def ShCmd
  def Result
  def ResultJson
  //def FileJson = 'save'+SomeMap['secret']+'.json'
  def FileYaml = 'save'+SomeMap['secret']+'.yml'
  //def TextJson = JsonOutput.toJson( SomeMap['mapToSave'] )

  //sh( 'rm -rf '+FileJson+' || true' )
  sh( 'rm -rf '+FileYaml+' || true' )
  //writeFile( file: FileJson, text: TextJson )
  writeYaml( file: FileYaml, data: SomeMap['mapToSave'] )
//  ResultJson = readJSON ( file: FileJson )
//  writeJSON( file: FileJson, json: ResultJson, pretty: 2 )

  withAWS(  roleAccount:  env.AwsSecretsAccount,
            region:       env.AwsSecretsRegion,
            role:         env.AwsSecretsRole )
  {
    script {
      //ShCmd = 'aws secretsmanager update-secret --secret-id '+SecretName+' --secret-string file://'+FileJson
      // If the secret is in a different account, then you must create a custom CMK and provide the ARN in this field.
      // ShCmd = 'aws secretsmanager update-secret --kms-key-id '+SomeMap['kms']+' --secret-id '+SomeMap['secret']+' --secret-string '+TextJson
      ShCmd = 'aws secretsmanager update-secret --kms-key-id '+SomeMap['kms']+' --secret-id '+SomeMap['secret']+' --secret-string file://'+FileYaml
      Result = sh( script: ShCmd, returnStdout: true )
      ResultJson = readJSON ( text: Result )
    }
  }
  return ResultJson['VersionId']
}

// awsAccountLoad

def call( Map Var = [:] ) {
  println 'awsAccountLoad'

  def AwsAccountSource     = Var.get('source' , 'secrets'             ) // 's3', 'resources' or 'secrets' (default and if none)
  def AwsAccountSourceType = Var.get('type'   , 'yaml'                ) // 'json' or 'yaml' (default and if none)
  def AwsAccountName       = Var.get('name'   , 'AwsAccount'          ) // file name for 's3' or 'resources, secret-id for 'secrets'
  def AwsAccountBucket     = Var.get('bucket' , 'some-bucket'         ) // S3 bucket for 's3' (skip for other type of source)
  def AwsAccountBucketPath = Var.get('path'   , '/some/file.yml'      ) // /path/to/file.yml in S3 bucket (skip for other type of source)
  def AwsAccountId         = Var.get('account', ''                    ) // some AWS Account ID to ex. '123456789012' or '' for current
  def AwsAccountRegion     = Var.get('region' , ''                    ) // some AWS region to ex. 'us-east-1' or '' for current
  def AwsAccountRole       = Var.get('role'   , ''                    ) // some IAM role to ex. 'some_role' or '' for current
  def AwsAccountExtId      = Var.get('externalId', ''                 )

  def ShCmd
  def Result
  def ResultJson
  def ResultAwsAccount

  script {
    if( AwsAccountSource == 'resources' )
    {
      if ( AwsAccountSourceType == 'json' ) { ResultAwsAccount = readJSON ( text: libraryResource(AwsAccountName) ) }
      else                                  { ResultAwsAccount = readYaml ( text: libraryResource(AwsAccountName) ) }
    }
    else
    {
      withAWS(  roleAccount: AwsAccountId,
                region:      AwsAccountRegion,
                role:        AwsAccountRole,
                externalId:  AwsAccountExtId )
      {
        if ( AwsAccountSource == 's3' )
        {
          s3Download( bucket: AwsAccountBucket, path: AwsAccountBucketPath, file: AwsAccountName, force:true )
          if ( AwsAccountSourceType == 'json' ) { ResultAwsAccount = readJSON ( file: AwsAccountName ) }
          else                                  { ResultAwsAccount = readYaml ( file: AwsAccountName ) }
          sh( 'rm -rf '+AwsAccountName )
        }
        else // 'secrets' (default)
        {
          ShCmd = 'aws secretsmanager get-secret-value --secret-id '+AwsAccountName
          Result = sh( script: ShCmd, returnStdout: true )
          ResultJson = readJSON ( text: Result )
          if ( AwsAccountSourceType == 'json' ) { ResultAwsAccount = readJSON ( text: ResultJson['SecretString'] ) }
          else                                  { ResultAwsAccount = readYaml ( text: ResultJson['SecretString'] ) }
        }
      }
    }
  }
  return ResultAwsAccount
}

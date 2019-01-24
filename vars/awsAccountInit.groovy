#!groovy

// awsAccountInit

import aws.AwsAccountClass

def call( Map Var = [:] ) {
  def TargetRegion    = Var.get('targetRegion', '' )
  def LinkAwsAccount  = Var.get('linkAwsAccount', [
                                  'Target':     'Target',
                                  'Management': 'Management',
                                  'Shared':     'Shared',
                                ] )
  def MainDomain      = Var.get('mainDomain', '' )
  println 'awsAccountInit ver.0.2 '+LinkAwsAccount.toString()

  def AwsAccount
  
  if ( env.AwsAccountSource == 's3' )
  {
    AwsAccount = awsAccountLoad (
      source:  env.AwsAccountSource,
      bucket:  env.AwsAccountBucket,
      path:    env.AwsAccountBucketPath,
      type:    'yaml',
      account: env.AwsSecretsAccount,
      region:  env.AwsSecretsRegion,
      role:    env.AwsSecretsRole,
    )
  }
  else // 'secrets' (default)
  {
    AwsAccount = awsAccountLoad (
      source:  'secrets',
      type:    'yaml',
      account: env.AwsSecretsAccount,
      region:  env.AwsSecretsRegion,
      role:    env.AwsSecretsRole,
    )
  }

  // link for Domain
  if ( MainDomain != '' && MainDomain != 'null' && ( ! LinkAwsAccount.containsKey('Domain') ) )
  {
    AwsAccount.each{ key, value -> 
      if ( value.containsKey('domains') && value['domains'].find { it == MainDomain } )
      {
        LinkAwsAccount['Domain'] = key
      }
    }
  }

  if ( ! LinkAwsAccount.containsKey('Domain') )
  {
    LinkAwsAccount['Domain'] = 'Shared'
  }

  if ( MainDomain )
  {
    println 'Domain account for "'+MainDomain+'" linked to '+LinkAwsAccount['Domain']
  }
  else
  {
    println 'WARNING: no MainDomain so linked to '+LinkAwsAccount['Domain']
  }

  //LinkAwsAccount.each{ key, value -> AwsAccount[key] = AwsAccount[value] }
  // There is a problem here - we have to use deeeeeeep copy
  LinkAwsAccount.each{ key, value ->
    if ( ! AwsAccount.containsKey( key ) )
    {
      AwsAccount[key] = [:]
    }
    AwsAccount[value].each{ key2, value2 -> AwsAccount[key][key2] = value2 }
  }
  //AwsAccount['Target']['region'] = (TargetRegion != '') ? TargetRegion : AwsAccount['Target']['region']
  //AwsAccount['Target']['region'] = TargetRegion ?: AwsAccount['Target']['region']

  if ( TargetRegion != '' && TargetRegion != 'null' && TargetRegion != 'default' )
  //if ( TargetRegion )
  {
    println 'Override Target region from '+AwsAccount['Target']['region']+' to '+TargetRegion
    AwsAccount['Target']['region'] = TargetRegion
  }

  AwsAccountClass.instance.set( AwsAccount )
  return AwsAccount
}

// // old ver.0.1
// def call() {
//   println 'awsAccountInit ver.0.1 (DEPRICATED)'
// 
//   def AwsAccount = [:]
//   AwsAccount = awsAccountLoad (
//     source:  'secrets',
//     type:    'yaml',
//     account: env.AwsSecretsAccount,
//     region:  env.AwsSecretsRegion,
//     role:    env.AwsSecretsRole
//   )
//   AwsAccountClass.instance.set( AwsAccount )
//   return AwsAccount
// }
// 

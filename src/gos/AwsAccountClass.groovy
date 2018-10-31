#!groovy

// aws.AwsAccountClass

package aws

@Singleton
class AwsAccountClass {

  private Map AwsAccount = [:]

  public get () {
    return this.AwsAccount
  }

  public set ( Map AwsAccount ) {
    this.AwsAccount = AwsAccount
  }

}

#!groovy

import aws.Account

def call() {
  println 'awsInit:'
    return awsInit()
}

def call( String Str1 ) {
  println 'awsInit:'
    return awsInitialize( Str1 )
}

def awsInit() {
    println Account.instance.AwsAccount
    return Account.instance
}

def awsInitialize( String Str1 ) {
    Account.instance.AwsAccount = Str1
    return
}

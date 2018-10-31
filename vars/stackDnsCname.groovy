#!groovy

// stackDnsCname

import aws.AwsAccountClass
import stack.StackClass

def call( Map Var = [:] ) {
  def AwsAccount      = Var.get('account'     , AwsAccountClass.instance.get() )
  def AwsAccountType  = Var.get('accountType' , 'Target' )
  def ConfirmType     = Var.get('confirmType' , 'Domain' )
  def ActionType      = Var.get('actionType'  , 'create/update' )
  def CnameVarName    = Var.get('cnameVarName', 'dns1' )
  def CnameValue      = Var.get('cnameValue'  , '' )
  def DnsType         = Var.get('dnsType'     , 'dns' ) // alias
  def ForStack        = Var.get('forStack'    , ( CnameValue ) ? CnameVarName : '' )
  def StackType       = Var.get('stackType'   , DnsType+'-'+ForStack+'-'+CnameVarName )
  //def StackName       = Var.get('stackName'   , DnsType+'-for-'+AwsAccount[AwsAccountType]['name']  +'-'+
  //                                                              AwsAccount[AwsAccountType]['region']+'-'+
  //                                                              ForStack+'-'+CnameVarName )
  def StackName       = Var.get('stackName'   , ( AwsAccount['Domain']['id'] != AwsAccount[AwsAccountType]['id'] )
    ? DnsType+'-for-'+AwsAccount[AwsAccountType]['name']+'-'+AwsAccount[AwsAccountType]['region']+'-'+ForStack+'-'+CnameVarName
    : DnsType+'-'+ForStack+'-'+CnameVarName
    )
  def ServiceName     = Var.get('serviceName' , ForStack )
  def StackFile       = Var.get('stackFile'   , DnsType+'.yml' )
  def MainDomain      = Var.get('mainDomain'  , env.MainDomain )
  def SubDomain       = Var.get('subDomain'   , '' )
  println 'stackDnsCname '+StackName+' '+ActionType

  def Stack = [
    (StackType):[
      name: StackName,
      file: StackFile,
      params: [
        Cname1:                 'get',
        MainDomain:             MainDomain,
        SubDomain:              SubDomain,
      ],
      timeout: 15
    ],
  ]
  def StackLoad = [:]
  def FileTmp = StackName+'-'+System.currentTimeMillis()+'.yml'

// *********************************************************************
script {
// +++++++++++++++++++++++++++ create/update +++++++++++++++++++++++++++
if ( ActionType == 'create/update' )
{
  writeFile( file: FileTmp, text: libraryResource(StackFile) )
  if ( CnameValue )
  {
    Stack[StackType]['params']['Cname1'] = CnameValue
  }
  else if ( ForStack )
  {
    StackLoad = orcfLoad( varName: ForStack )
    Stack[StackType]['params']['Cname1'] = StackLoad['outputs'][CnameVarName]
  }
  else
  {
    error ( 'stackDnsCname ERROR: You must setup "forStack" or "cnameValue"!' )
  }
  def EnterDomain = (SubDomain) ? SubDomain+'.'+MainDomain : MainDomain
  sh ("sed -i 's|DNS some-service for some-aws-account|"+DnsType+
    ' for '+ServiceName+' ('+CnameVarName+') '+
    ' for '+AwsAccount[AwsAccountType]['name']+' ('+AwsAccount[AwsAccountType]['id']+') '+
    ' for '+EnterDomain+', CNAME '+Stack[StackType]['params']['Cname1']+
    "|' "+FileTmp)
  stackCfUpdate ( stackFile: FileTmp, accountType: ConfirmType, stack: Stack, stackType: StackType  )
  sh( 'rm -rf '+FileTmp )
} // end of ActionType == 'create/update' ++++++++++++++++++++++++++++++

// --------------------------- delete ----------------------------------
if ( ActionType == 'delete' )
{
  stackCfDelete ( accountType: ConfirmType, stack: Stack, stackType: StackType )
} // end of ActionType == 'delete' -------------------------------------

// === end of script block =============================================
}

} // end of call

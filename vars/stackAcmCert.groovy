#!groovy

// stackAcmCert

import aws.AwsAccountClass
import stack.StackClass

// *** new

def call( Map Var = [:] ) {
  def AwsAccount        = Var.get('account'     , AwsAccountClass.instance.get() )
  def AwsAccountType    = Var.get('accountType' , 'Target' )
  def AwsAccountRegion  = Var.get('region'      , AwsAccount[AwsAccountType]['region'] )
  def ConfirmType       = Var.get('confirmType' , 'Domain' )
  def ActionType        = Var.get('actionType'  , 'create/update' )
    
  def StackType         = Var.get('stackType'   , 'confirm-certificate' )
  def StackName         = Var.get('stackName'   , StackType+'-for-'+AwsAccount[AwsAccountType]['name']+'-'+AwsAccountRegion )
  def StackFile         = Var.get('stackFile'   , StackType+'.yml' )
    
  def MainDomain        = Var.get('mainDomain'  , env.MainDomain )
  println 'stackAcmCert '+ActionType+' at '+AwsAccountType

  def Stack = [
    (StackType): [
      name: StackName,
      file: StackFile,
      params: [
        MainDomain:     MainDomain,
        CertificateAcm: 'get'
      ],
      timeout: 15
    ],
  ]
  def StackLoad = [:]

  def FileJson = 'request-certificate.json'
  def FileTmp  = StackType+'-'+System.currentTimeMillis()+'.yml'

  def Result
  def ResultJson

  def CertConfirmName   = []
  def CertConfirmCname  = []

  def ShCmd

  def CertIssued = ''
  def CertificateAcm = ''
  def NeedToUpdate = 'no'

// *********************************************************************
script {
// +++++++++++++++++++++++++++ create/update +++++++++++++++++++++++++++
if ( ActionType == 'create/update' )
{
  try {
    StackLoad = orcfLoad( varName: StackName )
    CertificateAcm = StackLoad['params']['CertificateAcm']
    Stack[StackType]['params']['CertificateAcm'] = CertificateAcm
  }
  catch ( all ) {
    println 'cannot load '+StackName
    CertificateAcm = ''
  }
  // Request certificate in Target account
  withAWS(  roleAccount:  AwsAccount[AwsAccountType]['id'],
            region:       AwsAccountRegion,
            role:         AwsAccount[AwsAccountType]['role'] )
  {
    if ( CertificateAcm != '' )
    {
      // check if cert already exists
      def NoCertJson = '{\\"Certificate\\": {\\"Status\\": \\"no-cert\\" }}'
      ShCmd = 'aws acm describe-certificate --certificate-arn '+CertificateAcm+' || echo " '+NoCertJson+' " '
      Result = sh( script: ShCmd, returnStdout: true )
      println 'describe-certificate: '+Result
      ResultJson = readJSON( text: Result )
      if ( ResultJson['Certificate']['Status'] == 'ISSUED' )
      {
        CertIssued = "ISSUED"
      }
    }
    else
    {
      ShCmd = 'aws acm list-certificates --certificate-statuses ISSUED'
      Result = sh( script: ShCmd, returnStdout: true )
      println 'list-certificates ISSUED: '+Result
      ResultJson = readJSON( text: Result )
      //ResultJson['CertificateSummaryList'].find { it['IsDefault'] ; return it['CertificateArn'] }
      ResultJson['CertificateSummaryList'].each{
        if ( it['DomainName'] == MainDomain )
        {
          CertificateAcm = it['CertificateArn']
          CertIssued = "ISSUED"
          Stack[StackType]['params']['CertificateAcm'] = CertificateAcm
          NeedToUpdate = 'yes'
        }
      }
    }
    // get new cert
    if ( CertIssued != "ISSUED" )
    {
      // prepare json file with Record Records for request
      sh( 'rm -rf '+FileJson )
      writeFile( file: FileJson, text: libraryResource(FileJson) )
      sh( 'sed -i "s|some.domain|'+MainDomain+'|g" '+FileJson )
      // send request
      ShCmd = 'aws acm request-certificate --cli-input-json file://'+FileJson
      Result = sh( script: ShCmd, returnStdout: true )
      println 'Output: '+Result
      // get CertificateArn from responce
      ResultJson = readJSON( text: Result )
      CertificateAcm = ResultJson['CertificateArn']

      // describe requested certificate to get current status
      ShCmd = 'aws acm describe-certificate --certificate-arn '+CertificateAcm
      Result = sh( script: ShCmd, returnStdout: true )
      println 'Output: '+Result
      ResultJson = readJSON( text: Result )
      CertIssued = ResultJson['Certificate']['Status']
      if ( CertIssued == "PENDING_VALIDATION" )
      {
        def NotReady = true
        while ( NotReady )
        {
          // waitnig for ALL DomainValidationOptions elements have ResourceRecord (or may be simply waiting more)
          NotReady = false
          ResultJson['Certificate']['DomainValidationOptions'].each{
            if ( ! it.containsKey('ResourceRecord') )
            {
              NotReady = true
            } 
          }

          println 'waiting for ready PENDING_VALIDATION ResourceRecord'
          sleep 5

          ShCmd = 'aws acm describe-certificate --certificate-arn '+CertificateAcm
          Result = sh( script: ShCmd, returnStdout: true )
          println 'Output: '+Result
          ResultJson = readJSON( text: Result )
        }
        // store ResourceRecord when they all are ready to CertConfirmName and CertConfirmCname
        ResultJson['Certificate']['DomainValidationOptions'].each{
          CertConfirmName.add(  it['ResourceRecord']['Name' ] )
          CertConfirmCname.add( it['ResourceRecord']['Value'] )
          println 'ResourceRecord.Name: ' +it['ResourceRecord']['Name' ]
          println 'ResourceRecord.Value: '+it['ResourceRecord']['Value']
        }
        CertIssued = 'PENDING_VALIDATION'
      }
    }
  } // end of withAWS

  if ( CertIssued != "ISSUED" )
  {
    // Confirm certificate in Shared account
    writeFile( file: FileTmp, text: libraryResource(StackFile) )
    sh( 'sed -i "s|some.domain|'+MainDomain+'|g" '+FileTmp )
    sh( 'sed -i "s|some.account|'+AwsAccount[AwsAccountType]['name']+' ('+AwsAccount[AwsAccountType]['id']+') '+'|g" '+FileTmp )
    CertConfirmName.eachWithIndex{  item, index -> Stack[StackType]['params']['Name'+index]  = item }
    CertConfirmCname.eachWithIndex{ item, index -> Stack[StackType]['params']['Cname'+index] = item }
    Stack[StackType]['params']['CertificateAcm'] = CertificateAcm
    stackCfUpdate ( stackFile: FileTmp, accountType: ConfirmType, stack: Stack, stackType: StackType  )
    sh( 'rm -rf '+FileTmp )

    // Waiting for certificate issue in Target account
    withAWS(  roleAccount:  AwsAccount[AwsAccountType]['id'],
              region:       AwsAccountRegion,
              role:         AwsAccount[AwsAccountType]['role'] )
    {
      ShCmd = 'aws acm describe-certificate --certificate-arn '+CertificateAcm
      Result = sh( script: ShCmd, returnStdout: true )
      println 'Output: '+Result
      ResultJson = readJSON( text: Result )
      while ( ResultJson['Certificate']['Status'] != "ISSUED" )
      {
        echo 'waiting for сertificate issue'
        sleep 120
        ShCmd = 'aws acm describe-certificate --certificate-arn '+CertificateAcm
        Result = sh( script: ShCmd, returnStdout: true )
        println 'Output: '+Result
        ResultJson = readJSON( text: Result )
      }
    } // end of withAWS
  } // end of if ISSUED

  if ( NeedToUpdate == 'yes' )
  {
    orcfSave ( varName: StackName, varValue: Stack[StackType] )
  }

} // end of ActionType == 'create/update' ++++++++++++++++++++++++++++++

// --------------------------- delete ----------------------------------
if ( ActionType == 'delete' )
{
  try
  {
    stackCfDelete ( accountType: ConfirmType, stack: Stack, stackType: StackType  )
    StackLoad = orcfLoad( varName: StackName )
    withAWS(  roleAccount:  AwsAccount[AwsAccountType]['id'],
              region:       AwsAccountRegion,
              role:         AwsAccount[AwsAccountType]['role'] )
    {
      sh( 'aws acm delete-certificate --certificate-arn '+StackLoad['params']['CertificateAcm']+' || true' )
    }
  }
  catch ( all )
  {
    println 'Error during delete AcmCert'
  }
} // end of ActionType == 'delete' -------------------------------------

// --------------------------- load ----------------------------------
if ( ActionType == 'load' )
{
  try
  {
    StackLoad = orcfLoad( varName: StackName )
    CertificateAcm = StackLoad['params']['CertificateAcm']
  }
  catch ( all )
  {
    println 'Error during load AcmCert'
    CertificateAcm = stackAcmCert( accountType: AwsAccountType, region: AwsAccountRegion )
  }
} // end of ActionType == 'load' -------------------------------------

// === end of script block =============================================
}
  return CertificateAcm
} // end of call

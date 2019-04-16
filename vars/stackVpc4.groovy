#!groovy

// stackVpc4

import aws.AwsAccountClass
import stack.StackClass

def call( Map Var = [:] ) {
  def AwsAccount            = Var.get('account'               , AwsAccountClass.instance.get() )
  def AwsAccountType        = Var.get('accountType'           , 'Target' )
  def ActionType            = Var.get('actionType'            , 'create/update' )
              
  def CidrPre               = Var.get('cidrPre'               , 40  )
  def CidrBeginDmz          = Var.get('cidrBeginDmz       '   , 11  )
  def CidrBeginPrivateApp   = Var.get('cidrBeginPrivateApp'   , 21  ) // 20
  def CidrBeginPrivateDb    = Var.get('cidrBeginPrivateDb '   , 201 ) // 20

  def MainDomain            = Var.get('mainDomain'            , '' ) // some.domain
  def TagEnvironment        = Var.get('tagEnvironment'        , '' ) // some-tag
  def VpcManagement         = Var.get('vpcManagement'         , '' ) // vpc-123123
  def RtbManagement         = Var.get('rtbManagement'         , '' ) // rtb-123123
  def CidrBlockManagement   = Var.get('cidrBlockManagement'   , '' ) // 10.10.0.0/16
    
  def CreateVpc             = Var.get('createVpc'             , 'yes' )
  def CreatePeer            = Var.get('createPeer'            , 'no'  )
  def CreateNat             = Var.get('createNat'             , 'yes' )

  def CountZones            = Var.get('countZones'            , 2 )

  println 'stackVpc4 '+ActionType+' at '+AwsAccountType
  println 'CreateVpc - '+CreateVpc
  println 'CreatePeer - '+CreatePeer

  def StackFile = 'peer.yml'
  def Stack = [
    'peer': [
      name: 'vpc-peer-to-'+AwsAccount[AwsAccountType]['name']+'-'+AwsAccount[AwsAccountType]['region'],
      file: StackFile,
      params: [
        VpcManagement:  VpcManagement,
        RtbManagement:  RtbManagement,
        AwsAccountVpc4: AwsAccount[AwsAccountType]['id'],
        //RoleInVpc4:     AwsAccount[AwsAccountType]['role'],
        RoleInVpc4:     'arn:aws:iam::'+AwsAccount[AwsAccountType]['id']+':role/'+AwsAccount[AwsAccountType]['role'],
        Vpc4:           'get',
        CidrBlockVpc4:  'get',
      ],
      timeout: 15
    ],
  ]
  def StackLoad = [:]

  def Result
  def ResultJson
  def ShCmd

  def Vpc4rtb
  def AvailabilityZones

  // upload stack file to workspace
  sh( 'rm -rf '+StackFile )
  writeFile( file: StackFile, text: libraryResource(StackFile) )

// *********************************************************************
script {
// +++++++++++++++++++++++++++ create/update +++++++++++++++++++++++++++
if ( ActionType == 'create/update' )
{
  // vpc4
  println 'CreateVpc: '+CreateVpc
  if ( CreateVpc == 'yes' )
  {
    withAWS(  roleAccount:  AwsAccount[AwsAccountType]['id'],
              region:       AwsAccount[AwsAccountType]['region'],
              role:         AwsAccount[AwsAccountType]['role'],
              externalId:   AwsAccount[AwsAccountType].get('externalId','') )
    {
      Result = sh( script: 'aws ec2 describe-availability-zones', returnStdout: true )
      ResultJson = readJSON( text: Result )
      AvailabilityZones = ResultJson['AvailabilityZones'].collect { it['ZoneName'] }
    }
    def CidrParams = []
    for (i = 0; i < CountZones; i++) {
      def CurAz = AvailabilityZones[i]
      def AzS = CurAz.charAt( CurAz.length() - 1 ).toUpperCase()
      CidrParams.add( ('CidrBlockVpc4Dmz'       +AzS): '.'+(i+BeginCidrDmz).toString()        +'.0/24' )
      CidrParams.add( ('CidrBlockVpc4PrivateApp'+AzS): '.'+(i+BeginCidrPrivateApp).toString() +'.0/24' )
      CidrParams.add( ('CidrBlockVpc4PrivateDb' +AzS): '.'+(i+BeginCidrPrivateDb).toString()  +'.0/24' )
    }

    stackDef (
      stackType: 'vpc4',
      params: [
        MainDomain:     MainDomain,
        TagEnvironment: TagEnvironment,
        CreateVpc:      CreateVpc,
        CreatePeer:     CreatePeer,
        CreateNat:      CreateNat,
        //CidrBlockVpc4:  '10.'+CidrPre+'.0.0/16',
        CidrPre:        '10.'+AwsAccount['Management']['cidrPre'],
        CidrBlockVpc4:  '.0.0/16',
      ]+CidrParams,
    )    
  }
  else if ( CreateVpc == 'shared' )
  {
    withAWS(  roleAccount:  AwsAccount['Management']['id'],
              region:       AwsAccount['Management']['region'],
              role:         AwsAccount['Management']['role'],
              externalId:   AwsAccount['Management'].get('externalId','') )
    {
      Result = sh( script: 'aws ec2 describe-availability-zones', returnStdout: true )
      ResultJson = readJSON( text: Result )
      AvailabilityZones = ResultJson['AvailabilityZones'].collect { it['ZoneName'] }
      //println 'AvailabilityZones: ' + AvailabilityZones.toString()

      Result = sh( script: 'aws ec2 describe-subnets', returnStdout: true )
      println 'Result: '+Result
      ResultJson = readJSON( text: Result )

      def CidrParams = []
      for (i = 0; i < CountZones; i++) {
        def CurAz = AvailabilityZones[i]
        def AzS = CurAz.charAt( CurAz.length() - 1 ).toUpperCase()

        def Cidr = (i+BeginCidrDmz).toString() +'.0/24'
        def Subnet = ResultJson['Subnets'].find { it['VpcId'] == VpcManagement && it['CidrBlock'] == Cidr }
        CidrParams.add( ('CidrBlockVpc4Dmz'+AzS): Cidr )
        CidrParams.add( ('SubnetVpc4Dmz'   +AzS): Subnet )

        Cidr =  (i+BeginCidrPrivateApp).toString() +'.0/24'
        Subnet = ResultJson['Subnets'].find { it['VpcId'] == VpcManagement && it['CidrBlock'] == Cidr }
        CidrParams.add( ('CidrBlockVpc4PrivateApp'+AzS): Cidr )
        CidrParams.add( ('SubnetVpc4PrivateApp'   +AzS): Subnet )

        Cidr = (i+BeginCidrPrivateDb).toString() +'.0/24'
        Subnet = ResultJson['Subnets'].find { it['VpcId'] == VpcManagement && it['CidrBlock'] == Cidr }
        CidrParams.add( ('CidrBlockVpc4PrivateDb'+AzS): Cidr )
        CidrParams.add( ('SubnetVpc4PrivateDb'   +AzS): Subnet )
      }

      Result = sh( script: 'aws ec2 describe-route-tables', returnStdout: true )
      //println 'Result: '+Result
      ResultJson = readJSON( text: Result )
      Vpc4rtb = ResultJson['RouteTables'].find { it['VpcId'] == Vpc4 }
    }
    stackDef (
      stackType: 'vpc4af-shared',
      stackName: 'vpc4',
      params: [
        MainDomain:               MainDomain,
        TagEnvironment:           TagEnvironment,
        Vpc4:                     Vpc4,
        CidrPre:                  '10.'+AwsAccount['Management']['cidrPre'],
        CidrBlockVpc4:            '.0.0/16',
        RtbVpc4Dmz:               Vpc4rtb['RouteTableId'],
        RtbVpc4Private:           Vpc4rtb['RouteTableId'],
      ],
    )    
  }
  else if ( CreateVpc == 'no' || CreateVpc == 'get default' )
  {
    withAWS(  roleAccount:  AwsAccount[AwsAccountType]['id'],
              region:       AwsAccount[AwsAccountType]['region'],
              role:         AwsAccount[AwsAccountType]['role'],
              externalId:   AwsAccount[AwsAccountType].get('externalId','') )
    {

      Result = sh( script: 'aws ec2 describe-vpcs', returnStdout: true )
      println 'Result: '+Result
      ResultJson = readJSON( text: Result )
      def VpcDef = ResultJson['Vpcs'].find { it['IsDefault'] }
      if ( VpcDef )
      {
        Vpc4 = VpcDef['VpcId']
        CidrBlockVpc4 = VpcDef['CidrBlock']

        Result = sh( script: 'aws ec2 describe-availability-zones', returnStdout: true )
        ResultJson = readJSON( text: Result )
        AvailabilityZones = ResultJson['AvailabilityZones'].collect { it['ZoneName'] }
        //println 'AvailabilityZones: ' + AvailabilityZones.toString()

        Result = sh( script: 'aws ec2 describe-subnets', returnStdout: true )
        //println 'Result: '+Result
        ResultJson = readJSON( text: Result )

        def Eparams = []
        for (i = 0; i < CountZones; i++) {
          def CurAz = AvailabilityZones[i]
          def AzS = CurAz.charAt( CurAz.length() - 1 ).toUpperCase()
          def CurVpc = ResultJson['Subnets'].find{
            it['VpcId'] == Vpc4 && it['AvailabilityZone'] == AvailabilityZones[i]
          }
          Eparams.add( ('CidrBlockVpc4Dmz'+AzS):        CurVpc['CidrBlock'] )
          Eparams.add( ('CidrBlockVpc4PrivateApp'+AzS): CurVpc['CidrBlock'] ) // =DMZ
          Eparams.add( ('CidrBlockVpc4PrivateDb'+AzS):  CurVpc['CidrBlock'] ) // =DMZ
          Eparams.add( ('SubnetBlockVpc4Dmz'+AzS):        CurVpc['SubnetId'] )
          Eparams.add( ('SubnetBlockVpc4PrivateApp'+AzS): CurVpc['SubnetId'] ) // =DMZ
          Eparams.add( ('SubnetBlockVpc4PrivateDb'+AzS):  CurVpc['SubnetId'] ) // =DMZ
        }

        Result = sh( script: 'aws ec2 describe-route-tables', returnStdout: true )
        //println 'Result: '+Result
        ResultJson = readJSON( text: Result )
        Vpc4rtb = ResultJson['RouteTables'].find { it['VpcId'] == Vpc4 }
      }
    }
    stackDef (
      stackType: 'vpc4af-existed',
      stackName: 'vpc4',
      params: [
        MainDomain:               MainDomain,
        TagEnvironment:           TagEnvironment,
        Vpc4:                     Vpc4,
        RtbVpc4Dmz:               Vpc4rtb['RouteTableId'],
        RtbVpc4Private:           Vpc4rtb['RouteTableId'],
        CreateVpc:                CreateVpc,
        CreatePeer:               CreatePeer,
        CreateNat:                CreateNat,
        CidrBlockVpc4:            CidrBlockVpc4,
      ]+Eparams,
    )    
  }
  else
  {
    error 'Set CreateVpc to "yes", "no", "shared" or "get default" (current is "' +CreateVpc+'")!'
    continuePipeline = false
    currentBuild.result = 'SUCCESS'
  }
  // vpc4-resources
  stackDef (
    stackType: 'vpc4af-resources.yml',
    stackName: 'vpc4-resources',
    params: ( CidrBlockManagement ) ? [ CidrBlockManagement:  CidrBlockManagement ] : []
  )
  // peer
  if ( CreatePeer == 'yes' )
  {
    StackLoad = orcfLoad( varName: 'vpc4' )
    Stack['peer']['params']['Vpc4']           = StackLoad['outputs']['vpc4']
    Stack['peer']['params']['CidrBlockVpc4']  = StackLoad['outputs']['CidrBlockVpc4']
    
    sh( 'sed -i "s|some-aws_account_name|'+AwsAccount[AwsAccountType]['name']+' ('+AwsAccount[AwsAccountType]['id']+') '+'|g" '+StackFile )

    // TODO: for inter-region peering
    if ( AwsAccount[AwsAccountType]['region'] != AwsAccount['Management']['region'] )
    {
      withAWS(  roleAccount:  AwsAccount['Management']['id'],
                region:       AwsAccount['Management']['region'],
                role:         AwsAccount['Management']['role'],
                externalId:   AwsAccount['Management'].get('externalId','') )
      {
        ShCmd = 'aws ec2 create-vpc-peering-connection --vpc-id '+AwsAccount['Management']['vpc']+' --peer-vpc-id '+StackLoad['outputs']['vpc4']+' --peer-region '+AwsAccount[AwsAccountType]['region']
        Result = sh( script: ShCmd, returnStdout: true )
        println 'Output: '+Result
      }
      // Stack['peer']['params']['AwsAccountVpc4'] = ''
      Stack['peer']['params']['RoleInVpc4'    ] = ''
      // still not working with CloudFormation (have to use AWS console or API)
      println '*** WARNING! *** CloudFormation do NOT support cross-Region VPC-peering (only cross-Account) - do it with AWS Console'
      println 'Skip creating stacks "peer" and "routes".'
    }
    else
    {
      stackCfUpdate ( accountType: 'Management', stack: Stack, stackType: 'peer' )
      // routes
      //StackLoad = orcfLoad( varName: 'peer' )
      StackLoad = orcfLoad( varName: Stack['peer']['name'] )
      stackDef (
        stackType: 'routes',
        params: [
          PeerManagementVpc4:   StackLoad['outputs']['peerManagementVpc4'],
          CidrBlockManagement:  CidrBlockManagement,
        ],
      )
    }
  }
} // end of ActionType == 'create/update' ++++++++++++++++++++++++++++++

// --------------------------- delete ----------------------------------
if ( ActionType == 'delete' )
{
  stackDef ( actionType: 'delete', stackType: 'routes' )
  stackCfDelete ( accountType: 'Shared', stack: Stack, stackType: 'peer' )
  stackDef ( actionType: 'delete', stackType: 'vpc4-resources' )
  stackDef ( actionType: 'delete', stackType: 'vpc4' )
} // end of ActionType == 'delete' -------------------------------------

} // === end of script block =============================================

} // end of call

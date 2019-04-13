#!groovy

// stackVpc4

import aws.AwsAccountClass
import stack.StackClass

def call( Map Var = [:] ) {
  def AwsAccount            = Var.get('account'               , AwsAccountClass.instance.get() )
  def AwsAccountType        = Var.get('accountType'           , 'Target' )
  def ActionType            = Var.get('actionType'            , 'create/update' )
              
  def CidrPre               = Var.get('cidrPre'               , '' ) // 20
  def MainDomain            = Var.get('mainDomain'            , '' ) // some.domain
  def TagEnvironment        = Var.get('tagEnvironment'        , '' ) // some-tag
  def VpcManagement         = Var.get('vpcManagement'         , '' ) // vpc-123123
  def RtbManagement         = Var.get('rtbManagement'         , '' ) // rtb-123123
  def CidrBlockManagement   = Var.get('cidrBlockManagement'   , '' ) // 10.10.0.0/16
    
  def CreateVpc             = Var.get('createVpc'             , 'yes' )
  def CreatePeer            = Var.get('createPeer'            , 'no'  )
  def CreateNat             = Var.get('createNat'             , 'yes' )
  
  def Vpc4                  = Var.get('vpc4'                  , '' )
  def CidrBlockVpc4         = Var.get('cidrBlockVpc4'         , '' )
  def SubnetVpc4DmzA        = Var.get('subnetVpc4DmzA'        , '' )
  def SubnetVpc4DmzB        = Var.get('subnetVpc4DmzB'        , '' )
  def SubnetVpc4PrivateAppA = Var.get('subnetVpc4PrivateAppA' , '' )
  def SubnetVpc4PrivateAppB = Var.get('subnetVpc4PrivateAppB' , '' )
  def SubnetVpc4PrivateDbA  = Var.get('subnetVpc4PrivateDbA'  , '' )
  def SubnetVpc4PrivateDbB  = Var.get('subnetVpc4PrivateDbB'  , '' )
  def RtbVpc4Dmz            = Var.get('rtbVpc4Dmz'            , '' )
  def RtbVpc4Private        = Var.get('rtbVpc4Private'        , '' )

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

  def Vpc4defA
  def Vpc4defB
  def Vpc4rtb

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
    stackDef (
      stackType: 'vpc4',
      params: [
        MainDomain:               MainDomain,
        TagEnvironment:           TagEnvironment,
        CidrBlockVpc4:            '10.'+CidrPre+'.0.0/16',
        CidrBlockVpc4DmzA:        '10.'+CidrPre+'.11.0/24',
        CidrBlockVpc4DmzB:        '10.'+CidrPre+'.12.0/24',
        CidrBlockVpc4PrivateAppA: '10.'+CidrPre+'.21.0/24',
        CidrBlockVpc4PrivateAppB: '10.'+CidrPre+'.22.0/24',
        CidrBlockVpc4PrivateDbA:  '10.'+CidrPre+'.201.0/24',
        CidrBlockVpc4PrivateDbB:  '10.'+CidrPre+'.202.0/24',
        CreateVpc:                CreateVpc,
        CreatePeer:               CreatePeer,
        CreateNat:                CreateNat,
      ],
    )    
  }
  else if ( CreateVpc == 'shared' )
  {
    withAWS(  roleAccount:  AwsAccount['Management']['id'],
              region:       AwsAccount['Management']['region'],
              role:         AwsAccount['Management']['role'],
              externalId:   AwsAccount['Management'].get('externalId','') )
    {
      println 'VpcManagement: '+VpcManagement
      Result = sh( script: 'aws ec2 describe-vpcs --vpc-ids='+VpcManagement, returnStdout: true )
      println 'Result: '+Result
      ResultJson = readJSON( text: Result )
      Vpc4 = VpcManagement
      CidrBlockVpc4 = ResultJson['Vpcs'][0]['CidrBlock']

      Result = sh( script: 'aws ec2 describe-availability-zones', returnStdout: true )
      ResultJson = readJSON( text: Result )
      def AvailabilityZones = ResultJson['AvailabilityZones'].collect { it['ZoneName'] }
      println 'AvailabilityZones: ' + AvailabilityZones.toString()

      Result = sh( script: 'aws ec2 describe-subnets', returnStdout: true )
      println 'Result: '+Result
      ResultJson = readJSON( text: Result )

      Vpc4A = ResultJson['Subnets'].find { it['VpcId'] == Vpc4 && it['AvailabilityZone'] == AvailabilityZones[0] }
      Vpc4B = ResultJson['Subnets'].find { it['VpcId'] == Vpc4 && it['AvailabilityZone'] == AvailabilityZones[1] }
      Vpc4C = ResultJson['Subnets'].find { it['VpcId'] == Vpc4 && it['AvailabilityZone'] == AvailabilityZones[2] }
      Vpc4D = ResultJson['Subnets'].find { it['VpcId'] == Vpc4 && it['AvailabilityZone'] == AvailabilityZones[3] }
      Vpc4E = ResultJson['Subnets'].find { it['VpcId'] == Vpc4 && it['AvailabilityZone'] == AvailabilityZones[4] }
      Vpc4F = ResultJson['Subnets'].find { it['VpcId'] == Vpc4 && it['AvailabilityZone'] == AvailabilityZones[5] }

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
        CidrBlockVpc4:            CidrBlockVpc4,
        CidrBlockVpc4DmzA:        Vpc4A['CidrBlock'],
        CidrBlockVpc4DmzB:        Vpc4B['CidrBlock'],
        CidrBlockVpc4DmzC:        Vpc4C['CidrBlock'],
        CidrBlockVpc4DmzD:        Vpc4D['CidrBlock'],
        CidrBlockVpc4DmzE:        Vpc4E['CidrBlock'],
        CidrBlockVpc4DmzF:        Vpc4F['CidrBlock'],
        CidrBlockVpc4PrivateAppA: Vpc4A['CidrBlock'],
        CidrBlockVpc4PrivateAppB: Vpc4B['CidrBlock'],
        CidrBlockVpc4PrivateAppC: Vpc4C['CidrBlock'],
        CidrBlockVpc4PrivateAppD: Vpc4D['CidrBlock'],
        CidrBlockVpc4PrivateAppE: Vpc4E['CidrBlock'],
        CidrBlockVpc4PrivateAppF: Vpc4F['CidrBlock'],
        CidrBlockVpc4PrivateDbA:  Vpc4A['CidrBlock'],
        CidrBlockVpc4PrivateDbB:  Vpc4B['CidrBlock'],
        CidrBlockVpc4PrivateDbC:  Vpc4C['CidrBlock'],
        CidrBlockVpc4PrivateDbD:  Vpc4D['CidrBlock'],
        CidrBlockVpc4PrivateDbE:  Vpc4E['CidrBlock'],
        CidrBlockVpc4PrivateDbF:  Vpc4F['CidrBlock'],
        SubnetVpc4DmzA:           Vpc4A['SubnetId'],
        SubnetVpc4DmzB:           Vpc4B['SubnetId'],
        SubnetVpc4DmzC:           Vpc4C['SubnetId'],
        SubnetVpc4DmzD:           Vpc4D['SubnetId'],
        SubnetVpc4DmzE:           Vpc4E['SubnetId'],
        SubnetVpc4DmzF:           Vpc4F['SubnetId'],
        SubnetVpc4PrivateAppA:    Vpc4A['SubnetId'],
        SubnetVpc4PrivateAppB:    Vpc4B['SubnetId'],
        SubnetVpc4PrivateAppC:    Vpc4C['SubnetId'],
        SubnetVpc4PrivateAppD:    Vpc4D['SubnetId'],
        SubnetVpc4PrivateAppE:    Vpc4E['SubnetId'],
        SubnetVpc4PrivateAppF:    Vpc4F['SubnetId'],
        SubnetVpc4PrivateDbA:     Vpc4A['SubnetId'],
        SubnetVpc4PrivateDbB:     Vpc4B['SubnetId'],
        SubnetVpc4PrivateDbC:     Vpc4C['SubnetId'],
        SubnetVpc4PrivateDbD:     Vpc4D['SubnetId'],
        SubnetVpc4PrivateDbE:     Vpc4E['SubnetId'],
        SubnetVpc4PrivateDbF:     Vpc4F['SubnetId'],
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
        def AvailabilityZones = ResultJson['AvailabilityZones'].collect { it['ZoneName'] }
        //println 'AvailabilityZones: ' + AvailabilityZones.toString()

        Result = sh( script: 'aws ec2 describe-subnets', returnStdout: true )
        //println 'Result: '+Result
        ResultJson = readJSON( text: Result )

        Vpc4defA = ResultJson['Subnets'].find { it['VpcId'] == Vpc4 && it['AvailabilityZone'] == AvailabilityZones[0] }
        Vpc4defB = ResultJson['Subnets'].find { it['VpcId'] == Vpc4 && it['AvailabilityZone'] == AvailabilityZones[1] }

        Result = sh( script: 'aws ec2 describe-route-tables', returnStdout: true )
        //println 'Result: '+Result
        ResultJson = readJSON( text: Result )
        Vpc4rtb = ResultJson['RouteTables'].find { it['VpcId'] == Vpc4 }
      }
    }
    stackDef (
      stackType: 'vpc4e',
      stackName: 'vpc4',
      params: [
        MainDomain:               MainDomain,
        TagEnvironment:           TagEnvironment,
        Vpc4:                     Vpc4,
        CidrBlockVpc4:            CidrBlockVpc4,
        CidrBlockVpc4DmzA:        Vpc4defA['CidrBlock'],
        CidrBlockVpc4DmzB:        Vpc4defB['CidrBlock'],
        CidrBlockVpc4PrivateAppA: Vpc4defA['CidrBlock'],
        CidrBlockVpc4PrivateAppB: Vpc4defB['CidrBlock'],
        CidrBlockVpc4PrivateDbA:  Vpc4defA['CidrBlock'],
        CidrBlockVpc4PrivateDbB:  Vpc4defB['CidrBlock'],
        SubnetVpc4DmzA:           Vpc4defA['SubnetId'],
        SubnetVpc4DmzB:           Vpc4defB['SubnetId'],
        SubnetVpc4PrivateAppA:    Vpc4defA['SubnetId'],
        SubnetVpc4PrivateAppB:    Vpc4defB['SubnetId'],
        SubnetVpc4PrivateDbA:     Vpc4defA['SubnetId'],
        SubnetVpc4PrivateDbB:     Vpc4defB['SubnetId'],
        RtbVpc4Dmz:               Vpc4rtb['RouteTableId'],
        RtbVpc4Private:           Vpc4rtb['RouteTableId'],
        CreateVpc:                CreateVpc,
        CreatePeer:               CreatePeer,
        CreateNat:                CreateNat,
      ],
    )    
  }
  else
  {
    error 'Set CreateVpc to "yes", "no" or "get default" (current is "' +CreateVpc+'")!'
    continuePipeline = false
    currentBuild.result = 'SUCCESS'
  }
  // vpc4-resources
  stackDef ( stackType: 'vpc4-resources', params: [ CidrBlockManagement:  CidrBlockManagement ] )
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

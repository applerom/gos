#!groovy

// stackDef

import aws.AwsAccountClass
import stack.StackClass

def call( Map Var = [:] ) {
  def AwsAccount      = Var.get('account'       , AwsAccountClass.instance.get() )
  def AwsAccountType  = Var.get('accountType'   , 'Target' )
  def ActionType      = Var.get('actionType'    , 'create/update' )
  
  def StackType       = Var.get('stackType'     , 'must_be_defined' ) // must be defined
  def StackName       = Var.get('stackName'     , StackType )
  def StackSource     = Var.get('stackSource'   , 'def' )
  def StackFile       = Var.get('stackFile'     , (StackSource=='def') ? StackType+'.yml' : StackName+'.yml' )
  def StackTimeout    = Var.get('stackTimeout'  , 15 )
  def StackRepeat     = Var.get('stackRepeat'   , 1 )
  def YamlRegExp      = Var.get('yamlRegExp'    , [] )
  def YamlReplace     = Var.get('yamlReplace'   , [] )
  def YamlAppend      = Var.get('yamlAppend'    , [] )

  def Params          = Var.get('params'      , [] )

  def ProjectName       = Var.get('projectName'       , StackClass.instance.getProjectName() )
  def ProjectConfigName = Var.get('projectConfigName' , StackClass.instance.getProjectConfigName() )

  println 'stackDef '+StackName+' '+ActionType

  def Stack = [
    (StackType):[
      name:     StackName,
      file:     StackFile,
      params:   Params,
      timeout:  StackTimeout
    ],
  ]
  def FileTmp  = StackName+'-'+System.currentTimeMillis()+'.yml'

// *********************************************************************
script {
if ( StackSource == 'stack' ) // copy from Stack
{ // care about structure copy - they will be always links! (so use copy by element value)
  StackClass.instance.getStack()[StackType]['params'].each{ key, value ->
    if ( ! Params.containsKey( key ) )
    {
      Stack[StackType]['params'][key] = value
    }
  }
}
// +++++++++++++++++++++++++++ create/update +++++++++++++++++++++++++++
if ( ActionType == 'create/update' )
{
  writeFile( file: FileTmp, text: ( StackSource == 'stack' ) ? readFile( file: StackFile ) : libraryResource(StackFile) )
  YamlRegExp.each {               sh ("sed -i '"+it+                +"|' "+FileTmp) }
  YamlReplace.each{ key, value -> sh ("sed -i 's|"+key+"|"+value    +"|' "+FileTmp) }
  YamlAppend.each { key, value -> sh ("sed -i 's|"+key+"|"+key+value+"|' "+FileTmp) }
  while ( StackRepeat )
  {
    try {
      StackRepeat--
      stackCfUpdate ( stackFile: FileTmp, accountType: AwsAccountType, stack: Stack, stackType: StackType,
                      projectName: ProjectName, projectConfigName: ProjectConfigName )
    }
    catch ( Exception e ) {
      println 'error during create/update '+StackName
      if ( StackRepeat == 0 )
      {
        throw e
      }
    }
  }

  sh( 'rm -rf '+FileTmp )
} // end of ActionType == 'create/update' ++++++++++++++++++++++++++++++

// --------------------------- delete ----------------------------------
if ( ActionType == 'delete' )
{
  stackCfDelete ( accountType: AwsAccountType, stack: Stack, stackType: StackType,
                  projectName: ProjectName, projectConfigName: ProjectConfigName )
} // end of ActionType == 'delete' -------------------------------------

// === end of script block =============================================
}

} // end of call

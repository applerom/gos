#!groovy

// stackInit

import stack.StackClass

def call( Map Var = [:] ) {
  def Stack             = Var.get('stack'             , '' ) // some 
  def ProjectName       = Var.get('projectName'       , '' ) // some 
  def ProjectConfigName = Var.get('projectConfigName' , '' ) // some 
  println 'stackInit for '+ProjectName+' ('+ProjectConfigName+')'

  if ( ProjectName == '' || ProjectName == 'null' || ProjectName == null )
  {
    ProjectName = env.JOB_NAME
    println 'change ProjectName to '+ProjectName
  }

  if ( ProjectConfigName == '' || ProjectConfigName == 'null' || ProjectConfigName == null  )
  {
    ProjectConfigName = 'default'
    println 'change ProjectConfigName to '+ProjectConfigName
  }


  StackClass.instance.setStack( Stack )
  StackClass.instance.setProjectName( ProjectName )
  StackClass.instance.setProjectConfigName( ProjectConfigName )
}

#!groovy

// gosInit

import gos.GosClass

def call( Map Var = [:] ) {
  def GitUrl    = Var.get('gitUrl'    , ''         )
  def GitBranch = Var.get('gitBranch' , '*/master' )
  def TargetDir = Var.get('targetDir' , 'gos'      )
  def Files     = Var.get('files'     , 'stack.yml') // TODO: load array of files / search *.yml/*yaml and load

  println 'gosInit v.0.2 for git repo '+GitUrl+'/'+GitBranch+' to '+TargetDir+' ('+Files+')'

  def NewGos =[:]
  def NewWithEnv =[]
  def ResultText
  def ResultYaml

  script {
    if ( GitUrl == null )
    {
      GitUrl = scm.getUserRemoteConfigs()[0].getUrl()+'-config'
    }
    if ( GitBranch == null )
    {
      GitBranch = '*/master'
    }
    if ( TargetDir == null )
    {
      TargetDir = 'gos'
    }
    if ( Files == null )
    {
      Files = 'stack.yml'
    }

    println 'use parameters: '+GitUrl+'/'+GitBranch+' to '+TargetDir+' ('+Files+')'

    checkout([$class: 'GitSCM',
      branches:          [[name: GitBranch]],
      extensions:        [[$class: 'RelativeTargetDirectory', relativeTargetDir: TargetDir]],
      submoduleCfg:      [],
      userRemoteConfigs: [[url: GitUrl]],
      doGenerateSubmoduleConfigurations: false,
    ])
    ResultText = readFile( file: TargetDir+'/'+Files )
    ResultText = ResultText.replaceAll(/\$\{env.(.*?)\}/, { var -> env.(var[1]) } )
    ResultYaml = readYaml( text: ResultText )
    //println 'ResultText: '+ResultText
    //println 'ResultYaml: '+ResultYaml.toString()

    ResultYaml.each{ key, value ->
      if ( value instanceof String )
      {
        def NewValue = value.replaceAll(/\$\{(.*?)\}/, { var -> ResultYaml[var[1]] } )
        ResultYaml[key] = NewValue
        //println 'New env: '+key+'='+NewValue
        NewWithEnv.add(key+'='+NewValue)
      }
    }
    NewGos = readYaml( text: ResultText.replaceAll(/\$\{(.*?)\}/, { var -> ResultYaml[var[1]] } ) )
    NewGos['withenv'] = NewWithEnv
    NewGos['AwsAccount'] = awsAccountInit(  linkAwsAccount: NewGos.LinkAwsAccount,
                                            targetRegion:   NewGos.TargetRegion,
                                            mainDomain:     NewGos.MainDomain )
    stackInit ( stack:              NewGos.Stack,
                projectName:        NewGos.ProjectName,
                projectConfigName:  NewGos.ProjectConfigName )
  }
  GosClass.instance.setGos( NewGos )
  GosClass.instance.setBranch( GitBranch )
  GosClass.instance.setTargetDir( TargetDir )
  return NewGos
}


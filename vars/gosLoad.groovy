#!groovy

// gosLoad

import gos.GosClass

def call( Map Var = [:] ) {
  def GitUrl    = Var.get('gitUrl'    , ''         )
  def GitBranch = Var.get('gitBranch' , '*/master' )
  def TargetDir = Var.get('targetDir' , 'gosload-'+System.currentTimeMillis() )
  def Files     = Var.get('files'     , ''         ) // TODO: load array of files / search *.yml/*yaml and load

  println 'gosLoad v.0.1 for git repo '+GitUrl+'/'+GitBranch+' to '+TargetDir+' ('+Files+')'

  def NewGos =[:]
  def NewWithEnv =[]
  def ResultText
  def ResultYaml

  script {
    if ( GitUrl == '' )
    {
      error 'Set "gitUrl"!'
      continuePipeline = false
      currentBuild.result = 'SUCCESS'
    }
    if ( Files == '' )
    {
      error 'Set "files" for Gos!'
      continuePipeline = false
      currentBuild.result = 'SUCCESS'
    }
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
    ResultYaml.each{ key, value ->
      if ( value instanceof String )
      {
        def NewValue = value.replaceAll(/\$\{(.*?)\}/, { var -> ResultYaml[var[1]] } )
        ResultYaml[key] = NewValue
      }
    }
    NewGos = readYaml( text: ResultText.replaceAll(/\$\{(.*?)\}/, { var -> ResultYaml[var[1]] } ) )
  }
  return NewGos
}


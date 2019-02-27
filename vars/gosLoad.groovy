#!groovy

// gosLoad

import gos.GosClass

def call( Map Var = [:] ) {
  def GitUrl    = Var.get('gitUrl'    , scm.getUserRemoteConfigs()[0].getUrl()+'-config' )
  def GitBranch = Var.get('gitBranch' , '*/master' )
  def TargetDir = Var.get('targetDir' , 'gos'      )
  def Files     = Var.get('files'     , 'stack.yml') // TODO: load array of files / search *.yml/*yaml and load

  println 'gosLoad v.0.2 for git repo '+GitUrl+'/'+GitBranch+' to '+TargetDir+' ('+Files+')'

  def NewGos =[:]
  def NewWithEnv =[]
  def ResultText
  def ResultYaml

  script {
    checkout([$class: 'GitSCM',
      branches:          [[name: GitBranch]],
      extensions:        [[$class: 'RelativeTargetDirectory', relativeTargetDir: TargetDir]],
      submoduleCfg:      [],
      userRemoteConfigs: [[url: GitUrl]],
      doGenerateSubmoduleConfigurations: false,
    ])
    ResultText = readFile( file: TargetDir+'/'+Files )
    ResultText = ResultText.replaceAll(/\$\{params.(.*?)\}/, { var -> env.(var[1]) } )
    ResultText = ResultText.replaceAll(/\$\{env.(.*?)\}/, { var ->
      if( env.(var[1]) ) { env.(var[1]) } else { params.(var[1]) }
    } )
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


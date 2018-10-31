#!groovy

// stack.StackClass

package stack

@Singleton
class StackClass {

  private Map Stack = [:]
  private String ProjectName = ''
  private String ProjectConfigName = ''

  public get () {
    return this.Stack
  }

  public set ( Map Stack ) {
    this.Stack = Stack
  }

  public getStack () {
    return this.Stack
  }

  public setStack ( Map Stack ) {
    this.Stack = Stack
  }

  public getProjectName () {
    return this.ProjectName
  }

  public setProjectName ( String ProjectName ) {
    this.ProjectName = ProjectName
  }

  public getProjectConfigName () {
    return this.ProjectConfigName
  }

  public setProjectConfigName ( String ProjectConfigName ) {
    this.ProjectConfigName = ProjectConfigName
  }

}

#!groovy

// gos.GosClass

package gos

@Singleton
class GosClass {

  private Map Gos = [:]
  private String Branch = ''
  private String TargetDir = ''

  public get () {
    return this.Gos
  }

  public set ( Map Gos ) {
    this.Gos = Gos
  }

  public getGos () {
    return this.Gos
  }

  public setGos ( Map Gos ) {
    this.Gos = Gos
  }

  public getBranch () {
    return this.Branch
  }

  public setBranch ( String Branch ) {
    this.Branch = Branch
  }

  public getTargetDir () {
    return this.TargetDir
  }

  public setTargetDir ( String TargetDir ) {
    this.TargetDir = TargetDir
  }

}

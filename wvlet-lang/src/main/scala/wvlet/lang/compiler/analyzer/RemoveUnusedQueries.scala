package wvlet.lang.compiler.analyzer

import wvlet.lang.compiler.{CompilationUnit, Context, Phase}
import wvlet.lang.model.plan.{ModelDef, TypeDef}

/**
  * Check unused compilation units and exclude them
  */
class RemoveUnusedQueries extends Phase("check-unused"):

  private var contextUnit: Option[CompilationUnit] = None
  private var usedUnits                            = List.empty[CompilationUnit]

  override protected def init(units: List[CompilationUnit], context: Context): Unit =
    contextUnit = context
      .global
      .getContextUnit
      .flatMap { unit =>
        units.find(_ eq unit)
      }
    usedUnits = List.empty

  override def runAlways: Boolean =
    // Need to run this phase always to check unused queries depending on the context unit
    true

  override def run(unit: CompilationUnit, context: Context): CompilationUnit =
    if contextUnit.exists(_ eq unit) then
      usedUnits = unit :: usedUnits
    else
      var hasDef = false
      unit
        .unresolvedPlan
        .traverse {
          case p: TypeDef =>
            hasDef = true
          case m: ModelDef =>
            hasDef = true
        }
      if hasDef then
        usedUnits = unit :: usedUnits
    unit

  override protected def refineUnits(units: List[CompilationUnit]): List[CompilationUnit] =
    debug(s"Compiling ${usedUnits.size} files out of ${units.size} files")
    usedUnits.reverse

end RemoveUnusedQueries
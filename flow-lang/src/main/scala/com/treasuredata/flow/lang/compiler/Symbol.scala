package com.treasuredata.flow.lang.compiler

import com.treasuredata.flow.lang.model.{DataType, TreeNode, Type, PackageType}
import com.treasuredata.flow.lang.model.expr.NameExpr
import com.treasuredata.flow.lang.model.expr.NameExpr.EmptyName
import com.treasuredata.flow.lang.model.plan.LogicalPlan

object Symbol:
  val NoSymbol: Symbol =
    new Symbol(0):
      override def computeSymbolInfo(using Context): SymbolInfo = NoSymbolInfo

  private val importName = Name.termName("<import>")
//  lazy val EmptyPackage: Symbol = newPackageSymbol(rootPackageName)
//  lazy val RootType: Symbol = newTypeDefSymbol(NoSymbol, NoName, DataType.UnknownType)
//
  def newPackageSymbol(owner: Symbol, name: Name)(using context: Context): Symbol =
    val symbol = Symbol(context.global.newSymbolId)
    symbol.symbolInfo = PackageSymbolInfo(symbol, owner, name, PackageType(name), context.scope)
    symbol

  def newImportSymbol(owner: Symbol, tpe: Type)(using context: Context): Symbol =
    val symbol = Symbol(context.global.newSymbolId)
    symbol.symbolInfo = NamedSymbolInfo(symbol, NoSymbol, importName, tpe)
    symbol

  def newTypeDefSymbol(owner: Symbol, name: Name, dataType: DataType)(using
      context: Context
  ): Symbol =
    val symbol = Symbol(context.global.newSymbolId)
    symbol.symbolInfo = TypeSymbolInfo(symbol, owner, name, dataType)
    symbol

end Symbol

/**
  * Symbol is a permanent identifier for a TreeNode of LogicalPlan or Expression nodes. Symbol holds
  * a cache of the resolved SymbolInfo, which contains DataType for the TreeNode.
  *
  * @param name
  */
class Symbol(val id: Int):
  private var _symbolInfo: SymbolInfo | Null = null
  private var _dataType: DataType            = DataType.UnknownType
  private var _tree: TreeNode | Null         = null

  override def toString =
    if _symbolInfo == null then
      s"Symbol($id)"
    else
      _symbolInfo.toString

  def isNoSymbol: Boolean = this == Symbol.NoSymbol

  def dataType: DataType            = _dataType
  def dataType_=(d: DataType): Unit = _dataType = d

  private def isResolved: Boolean = dataType.isResolved

  def tree: TreeNode =
    if _tree == null then
      LogicalPlan.empty
    else
      _tree

  def symbolInfo(using Context): SymbolInfo =
    if _symbolInfo == null then
      _symbolInfo = computeSymbolInfo

    _symbolInfo

  def symbolInfo_=(info: SymbolInfo): Unit = _symbolInfo = info

  def computeSymbolInfo(using Context): SymbolInfo = ???

end Symbol
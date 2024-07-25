package com.treasuredata.flow.lang.compiler

import com.treasuredata.flow.lang.model.{DataType, Type}
import Type.{FunctionType, PackageType}
import com.treasuredata.flow.lang.compiler.Symbol.NoSymbol
import com.treasuredata.flow.lang.model.expr.Expression
import com.treasuredata.flow.lang.model.plan.{DefContext, EmptyRelation, LogicalPlan}

/**
  * SymbolInfo is the result of resolving a name (Symbol) during the compilation phase.
  *
  * @param symbol
  * @param owner
  * @param name
  * @param dataType
  */
class SymbolInfo(val symbol: Symbol, val name: Name, private var _tpe: Type):
  private var _declScope: Scope | Null = null
  def declScope: Scope                 = _declScope
  def declScope_=(s: Scope): Unit      = _declScope = s

  def tpe: Type            = _tpe
  def tpe_=(t: Type): Unit = _tpe = t
  def dataType =
    tpe match
      case t: DataType =>
        t
      case _ =>
        DataType.UnknownType

  def dataType_=(d: DataType): Unit = tpe = d

  def findMember(name: Name): Symbol = NoSymbol

class PackageSymbolInfo(
    symbol: Symbol,
    owner: Symbol,
    name: Name,
    tpe: PackageType,
    packageScope: Scope
) extends SymbolInfo(symbol, name, tpe):
  this.declScope = packageScope

  override def toString: String =
    if owner.isNoSymbol then
      s"${name}"
    else
      s"${owner}.${name}"

object NoSymbolInfo extends SymbolInfo(Symbol.NoSymbol, Name.NoName, Type.UnknownType):
  override def toString: String = "NoSymbol"

class NamedSymbolInfo(symbol: Symbol, owner: Symbol, name: Name, tpe: Type)
    extends SymbolInfo(symbol, name, tpe):
  override def toString: String = s"${owner}.${name}: ${dataType}"

class TypeSymbolInfo(symbol: Symbol, owner: Symbol, name: Name, tpe: DataType, typeScope: Scope)
    extends NamedSymbolInfo(symbol, owner, name, tpe):
  this.declScope = typeScope
  override def findMember(name: Name): Symbol = typeScope.lookupSymbol(name).getOrElse(NoSymbol)
  def members: List[Symbol]                   = typeScope.getLocalSymbols

class MethodSymbolInfo(
    symbol: Symbol,
    owner: Symbol,
    name: Name,
    val ft: FunctionType,
    val body: Option[Expression],
    defContexts: List[DefContext]
) extends NamedSymbolInfo(symbol, owner, name, ft)

class ModelSymbolInfo(symbol: Symbol, owner: Symbol, name: Name, tpe: DataType)
    extends NamedSymbolInfo(symbol, owner, name, tpe):
  override def toString: String = s"model ${owner}.${name}: ${dataType}"

class BoundedSymbolInfo(symbol: Symbol, name: Name, tpe: DataType, val expr: Expression)
    extends SymbolInfo(symbol, name, tpe):
  override def toString: String = s"bounded ${name}: ${dataType} = ${expr}"

case class MultipleSymbolInfo(s1: SymbolInfo, s2: SymbolInfo)
    extends SymbolInfo(s1.symbol, s1.name, s1.tpe)

//class TypeInfo(symbol: Symbol, tpe: Type) extends SymbolInfo(symbol, tpe)
//
//case class SingleTypeInfo(symbol: Symbol, tpe: Type) extends TypeInfo(symbol, tpe)
//
//// Multiple types are used for overloaded methods
//case class MultipleTypeInfo(i1: TypeInfo, i2: TypeInfo)
//    extends TypeInfo(Symbol.NoSymbol, Type.UnknownType)

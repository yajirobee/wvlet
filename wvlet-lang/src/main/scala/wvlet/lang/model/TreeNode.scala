/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package wvlet.lang.model

import wvlet.lang.compiler.{Context, CompilationUnit, SourceFile, SourceLocation, Symbol}

/**
  * A base class for LogicalPlan and Expression
  */
trait TreeNode:

  private var _symbol: Symbol = Symbol.NoSymbol

  def symbol: Symbol            = _symbol
  def symbol_=(s: Symbol): Unit = _symbol = s

  /**
    * @return
    *   the code location in the SQL text if available
    */
  def nodeLocation: Option[NodeLocation]
  def sourceLocation(using cu: CompilationUnit): SourceLocation = SourceLocation(cu, nodeLocation)
  def locationString(using ctx: Context): String =
    sourceLocation(using ctx.compilationUnit).locationString

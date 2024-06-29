package com.treasuredata.flow.lang.compiler

import com.treasuredata.flow.lang.compiler.parser.{FlowScanner, FlowToken, SourcePosition, Span}
import com.treasuredata.flow.lang.compiler.parser.FlowToken.*
import wvlet.airframe.ulid.ULID
import wvlet.log.io.IOUtil

import java.io.File
import scala.collection.mutable.ArrayBuffer

object SourceFile:
  object NoSourceFile extends SourceFile("<empty>", _ => "")
  def fromFile(file: String): SourceFile = SourceFile(file, IOUtil.readAsString)
  def fromString(content: String): SourceFile = SourceFile(
    s"${ULID.newULIDString}.flow",
    _ => content
  )

  def fromResource(path: String): SourceFile = SourceFile(path, IOUtil.readAsString)

class SourceFile(val file: String, readContent: (file: String) => String):
  override def toString: String      = s"SourceFile($file)"
  def fileName: String               = new File(file).getName
  def toCompileUnit: CompilationUnit = CompilationUnit(this)

  lazy val content: IArray[Char] = IArray.unsafeFromArray(readContent(file).toCharArray)
  def length: Int                = content.length

  private val lineIndexes: Array[Int] =
    val txt = content
    val buf = new ArrayBuffer[Int]
    buf += 0
    var i = 0
    while i < txt.length do
      val isLineBreak =
        val ch = txt(i)
        // CR LF
        if ch == CR then
          i + 1 == txt.length || content(i + 1) != LF
        else
          FlowToken.isLineBreakChar(ch)
      if isLineBreak then
        buf += i + 1
      i += 1
    buf += txt.length // sentinel, so that findLine below works smoother
    buf.toArray

  private def findLineIndex(offset: Int, hint: Int = -1): Int =
    val idx = java.util.Arrays.binarySearch(lineIndexes, offset)
    if idx >= 0 then
      idx
    else
      -idx - 2

  def sourcePositionAt(offset: Int): SourcePosition = SourcePosition(this, Span.at(offset))

  def offsetToLine(offset: Int): Int = findLineIndex(offset)

  inline def charAt(pos: Int): Char = content(pos)

  /**
    * Find the start of the line (offset) for the given offset
    */
  def startOfLine(offset: Int): Int =
    val lineStart = findLineIndex(offset)
    lineIndexes(lineStart)

  def nextLine(offset: Int): Int =
    val lineStart = findLineIndex(offset)
    lineIndexes(lineStart + 1)

  def offsetToColumn(offset: Int): Int =
    val lineStart = findLineIndex(offset)
    offset - lineIndexes(lineStart) + 1

end SourceFile

package com.treasuredata.flow.lang.runner

import wvlet.log.LogSupport

trait QueryResultFormat:
  def fitToWidth(s: String, colSize: Int): String =
    if s.length > colSize then
      if colSize > 1 then
        s"${s.substring(0, colSize - 1)}…"
      else
        s.substring(0, colSize)
    else
      s

  protected def center(s: String, colSize: Int): String =
    val padding      = (colSize - s.length).max(0)
    val leftPadding  = padding / 2
    val rightPadding = padding - leftPadding
    fitToWidth(" " * leftPadding + s + " " * rightPadding, colSize)

  protected def alignRight(s: String, colSize: Int): String =
    val padding = (colSize - s.length).max(0)
    fitToWidth(" " * padding + s, colSize)

  protected def alignLeft(s: String, colSize: Int): String =
    val padding = (colSize - s.length).max(0)
    fitToWidth(s + " " * padding, colSize)

  protected def printElem(elem: Any): String =
    elem match
      case null =>
        ""
      case s: String =>
        s
      case m: Map[?, ?] =>
        val elems = m
          .map { (k, v) =>
            s"${k} => ${printElem(v)}"
          }
          .mkString(", ")
        s"${elems}"
      case a: Array[?] =>
        s"[${a.map(v => printElem(v)).mkString(", ")}"
      case x =>
        x.toString

  def printTableRows(tableRows: TableRows): String

end QueryResultFormat

object TSVFormat extends QueryResultFormat:
  def printTableRows(tableRows: TableRows): String =
    val fieldNames = tableRows.schema.fields.map(_.name.name).toIndexedSeq
    val header     = fieldNames.mkString("\t")
    val data = tableRows
      .rows
      .map { row =>
        fieldNames
          .map { fieldName =>
            row.get(fieldName).map(x => printElem(x)).getOrElse("")
          }
          .mkString("\t")
      }
      .mkString("\n")
    s"${header}\n${data}"

end TSVFormat

object QueryResultPrinter extends LogSupport:
  def print(result: QueryResult, format: QueryResultFormat): String =
    result match
      case QueryResult.empty =>
        ""
      case QueryResultList(list) =>
        list.map(x => print(x, format)).mkString("\n\n")
      case PlanResult(plan, result) =>
        print(result, format)
      case t: TableRows =>
        format.printTableRows(t)

end QueryResultPrinter

class PrettyBoxFormat(maxWidth: Option[Int], maxColWidth: Int)
    extends QueryResultFormat
    with LogSupport:

  def printTableRows(tableRows: TableRows): String =
    val isNumeric = tableRows.schema.fields.map(_.isNumeric).toIndexedSeq

    val tbl: List[Seq[String]] =
      val rows = List.newBuilder[Seq[String]]
      // Column names
      rows += tableRows.schema.fields.map(_.name.name)
      // Column types
      rows +=
        tableRows
          .schema
          .fields
          .map {
            _.dataType.typeDescription
          }

      var rowCount = 0
      tableRows
        .rows
        .foreach { row =>
          val sanitizedRow = row.map { (k, v) =>
            Option(v).map(v => printElem(v)).getOrElse("")
          }
          rowCount += 1
          rows += sanitizedRow.toSeq
        }
      rows.result()

    val maxColSize: IndexedSeq[Int] =
      tbl
        .map { row =>
          row.map(_.size)
        }
        .reduce { (r1, r2) =>
          r1.zip(r2)
            .map { case (l1, l2) =>
              l1.max(l2)
            }
        }
        .map(_.min(maxColWidth))
        .toIndexedSeq

    assert(tbl.size >= 2)
    val columnLabels = tbl.head
    val columnTypes  = tbl.tail.head

    val data = tbl.tail.tail

    val rows  = Seq.newBuilder[String]
    val width = maxColSize.sum + (maxColSize.size - 1) * 3

    rows +=
      maxColSize
        .map { maxSize =>
          "─".padTo(maxSize, "─").mkString
        }
        .mkString("┌─", "─┬─", "─┐")

    // header
    rows +=
      columnLabels
        .zip(maxColSize)
        .map { case (h, maxSize) =>
          center(h, maxSize)
        }
        .mkString("│ ", " │ ", " │")
    // column types
    rows +=
      columnTypes
        .zip(maxColSize)
        .map { case (h, maxSize) =>
          center(h, maxSize)
        }
        .mkString("│ ", " │ ", " │")

    // header separator
    rows +=
      maxColSize
        .map { s =>
          "─" * s
        }
        .mkString("├─", "─┼─", "─┤")

    // rows
    rows ++=
      data.map { row =>
        row
          .zip(maxColSize)
          .zipWithIndex
          .map { case ((x, maxSize), colIndex) =>
            if isNumeric(colIndex) then
              alignRight(x, maxSize)
            else
              alignLeft(x, maxSize)
          }
          .mkString("│ ", " │ ", " │")
      }

    // result footer
    rows +=
      maxColSize
        .map { s =>
          "─" * s
        }
        .mkString("├─", "─┴─", "─┤")
    if tableRows.isTruncated then
      rows +=
        alignLeft(f"${tableRows.totalRows}%,d rows (${tableRows.rows.size}%,d shown)", width)
          .mkString("│ ", "", " │")
    else
      rows += alignLeft(f"${tableRows.totalRows}%,d rows", width).mkString("│ ", "", " │")

    rows +=
      maxColSize
        .map { maxSize =>
          "─".padTo(maxSize, "─").mkString
        }
        .mkString("└─", "───", "─┘")

    val formattedRows = rows
      .result()
      .map { row =>
        fitToWidth(row, maxWidth.getOrElse(row.size))
      }
    formattedRows.mkString("\n")

  end printTableRows

  private def print(row: Any): String =
    row match
      case m: Map[?, ?] =>
        m.map: (k, v) =>
            s"${k}: ${print(v)}"
          .mkString(", ")
      case a: Array[?] =>
        a.map(print).mkString(", ")
      case null =>
        "null"
      case s: String =>
        s""""${s}""""
      case x =>
        x.toString

end PrettyBoxFormat
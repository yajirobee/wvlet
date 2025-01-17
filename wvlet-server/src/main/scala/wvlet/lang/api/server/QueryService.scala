package wvlet.lang.api.server

import wvlet.airframe.control.ThreadUtil
import wvlet.airframe.ulid.ULID
import wvlet.lang.{StatusCode, WvletLangException}
import wvlet.lang.api.v1.frontend.FrontendApi.{QueryInfoRequest, QueryRequest, QueryResponse}
import wvlet.lang.api.v1.query.{QueryError, QueryInfo, QueryResult, QueryStatus}
import wvlet.lang.api.v1.query.QueryStatus.{QUEUED, RUNNING}
import wvlet.lang.runner.QueryExecutor
import wvlet.lang.runner.cli.WvletScriptRunner
import wvlet.lang.runner.connector.DBConnector
import wvlet.log.LogSupport

import java.time.Instant
import java.util.concurrent.{ConcurrentHashMap, Executors}
import scala.jdk.CollectionConverters.*

class QueryService(scriptRunner: WvletScriptRunner) extends LogSupport with AutoCloseable:

  private val threadManager = Executors
    .newCachedThreadPool(ThreadUtil.newDaemonThreadFactory("wvlet-query-service"))

  private val queryMap = ConcurrentHashMap[ULID, QueryInfo]().asScala

  override def close(): Unit =
    // Close the query service
    threadManager.shutdownNow()

  def enqueue(request: QueryRequest): QueryResponse =
    // Enqueue the query request
    val queryId        = ULID.newULID
    val firstQueryInfo = QueryInfo(queryId = queryId, pageToken = "0", status = QUEUED)
    queryMap += queryId -> firstQueryInfo
    threadManager.submit(
      new Runnable:
        override def run: Unit = runQuery(queryId, request.query)
    )
    QueryResponse(queryId = queryId, requestId = request.requestId)

  def fetchNext(request: QueryInfoRequest): QueryInfo =
    info(s"Fetching query info: ${request}")
    // Fetch the query info
    queryMap
      .get(request.queryId)
      .getOrElse {
        throw StatusCode.INVALID_ARGUMENT.newException(s"Query not found: ${request.queryId}")
      }

  private def runQuery(queryId: ULID, query: String): Unit =
    info(s"[${queryId}] Running query:\n${query}")
    var lastInfo = queryMap(queryId)
    lastInfo = lastInfo.copy(pageToken = "1", QueryStatus.RUNNING, startedAt = Some(Instant.now()))
    queryMap += queryId -> lastInfo

    val queryResult = scriptRunner.runStatement(query)
    if queryResult.isSuccess then
      // TODO Support pagination
      // TODO Return the query result
      val preview = queryResult.toPrettyBox()
      queryMap += queryId ->
        lastInfo.copy(
          pageToken = "2",
          status = QueryStatus.FINISHED,
          completedAt = Some(Instant.now()),
          preview = Some(preview)
        )
    else
      val error = queryResult.getError.get
      val errorCode =
        error match
          case e: WvletLangException =>
            e.statusCode
          case _ =>
            StatusCode.NON_RETRYABLE_INTERNAL_ERROR

      queryMap += queryId ->
        lastInfo.copy(
          pageToken = "2",
          status = QueryStatus.FAILED,
          completedAt = Some(Instant.now()),
          error = Some(QueryError(errorCode.name, message = error.getMessage, error = Some(error)))
        )

  end runQuery

end QueryService

package wvlet.lang.runner

import wvlet.airframe.ulid.ULID

object ThreadUtil:
  def runBackgroundTask(f: () => Unit): Thread =
    val t =
      new Thread:
        override def run(): Unit = f()

    t.setName(s"wvlet-background-task-${ULID.newULID}")
    t.setDaemon(true)
    t.start()
    t
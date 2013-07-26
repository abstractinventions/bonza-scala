package com.abstractinventions.bonza

/** embedded server */
object Server {
  val logger = org.clapper.avsl.Logger(Server.getClass)

  def main(args: Array[String]) {
    val port = args(0).toInt
    unfiltered.netty.Http(port)
      .handler(new ReverseProxy("/news","http://www.news.com.au"))
      .handler(new ReverseProxy("/g","http://www.google.com.au"))
      .run { s =>
        logger.info("starting at localhost on port %s"
                    .format(s.port))
      }
  }
}

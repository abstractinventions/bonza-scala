package com.abstractinventions.bonza

import unfiltered.request.HttpRequest
import com.ning.http.client.Response
import java.text.SimpleDateFormat
import java.io.File

/** embedded server */
object Server {
  
  val usage = """Usage: bonza [filename |  port [-quiet] prefix1=resource1 [prefix2=resource2]...]

      If invoked without arguments, reads configuration from a .bonza file in the same directory.
      If invoked with a single argument that is a filename, reads configuration from the named file.
      Otherwise reads arguments from the command line.

      File Format:
      Delimited lines, the first line is the port to bind on with the remaining lines consist of proxy mapping 
      expressions.  The # character may be used as a comment character and will result in everything after it being ignored.

      e.g :

        #port
        8080
        #map /g to google
        /g=http://wwww.google.com

      Proxy Mapping Expressions: uri_prefix=proxied_resource
      Route requests with the given uri prefix to the proxied resource.
      The uri prefix of the request is replaced with the uri component of the proxied resource.
      e.g. given a mapping expresssion /google=http://google.net/ then a request to /google/blah?q=foo will result in a
      request to http://google.net/blah?q=foo

      Logging:
      The system logs requests in the following format : {timestamp} {source_ip} {method} {uri} -> {proxied_url} > {response_code} in {request_time} ms
      Logging may be disabled by passing -quiet on the command line or in the file.    """

  def main(args: Array[String]) {

    try {
      val config: (Int, Iterable[ReverseProxy]) = parseConfig(args)

      var server = unfiltered.netty.Http(config._1)

      server = config._2.foldLeft(server)((svr,rp) => svr.handler(rp))

      server.run {
                   s =>  println("starting at localhost on port %s"
                                 .format(s.port))
                 }

    } catch {
      case _:Throwable =>
        Console.err.println(usage)
    }
  }

  def canRead(f: String) = {
    val file = new File(f)
    file.canRead
  }

  def logToStdout(startTime: Long, request: HttpRequest[Any], destination: String, response: Response) {
    val sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
    val startTimeString = sdf.format(startTime);
    val elapsed = System.currentTimeMillis() - startTime
    val msg = s"${startTimeString} ${request.remoteAddr} ${request.method} ${request.uri} => ${destination} ( ${response.getStatusCode} in ${elapsed} ms )"
    println(msg)
  }

  def quietLogger(startTime: Long, request: HttpRequest[Any], destination: String, response: Response) {

  }

  /**
   * @param args
   * @return
   */
  def parseConfig(args: Array[String]): (Int, Iterable[ReverseProxy]) = {

    val configList: List[String] = argListToConfigurationList(args)

    val port = Integer.parseInt(configList(0))
    val log = configList.contains("-quiet")
    val logFunction = log match {
      case true  => quietLogger _
      case false => logToStdout _
    }
    val reverseProxies: List[ReverseProxy] = configList.tail
                                             .filterNot(_ == "-quiet")
                                             .map { configItem =>
                                                                     val parts: Array[String] = configItem.split("=")
                                                                     new ReverseProxy(parts(0), parts(1), logFunction)
                                                                 }
    (port, reverseProxies)
  }


  def argListToConfigurationList(args: Array[String]): List[String] = {
    val args2: Array[String] = args.length match {
      case 0 => Array(".bonza")
      case _ => args
    }

    val parts = args2(0) match {
      case file: String if (canRead(file)) => {
        io.Source.fromFile(file).getLines().toList
      }
      case _                               => {
        args2.toList
      }
    }
    parts.map(p=>p.substring(p.indexOf("#")))
         .map(_.trim())
         .filter(_.length > 0)
  }




}

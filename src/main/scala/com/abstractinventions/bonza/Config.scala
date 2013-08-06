package com.abstractinventions.bonza

import akka.actor.{ActorRef, Props, Actor}
import java.io.File
import unfiltered.request.HttpRequest
import com.ning.http.client.Response
import java.text.SimpleDateFormat
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import java.security.MessageDigest

case object LoadConfig

case class WatchFile(file: File)

case class Config(port: Int, handlers: Iterable[ReverseProxy]) {

  override def toString(): String = {
    "port " + port + "" + handlers.foldLeft("") {(s: String, h: ReverseProxy) => s + "\n" + h.toString}
  }
}

class ConfigActor(args: Array[String]) extends Actor {
  var server: ActorRef = context.actorOf(Props[ServerActor]);
  var configHash: String = null;

  def receive: Actor.Receive = {
    case LoadConfig            => {
      val config: Config = parseConfig(args)
      server ! Start(config)
    }
    case WatchFile(file: File) => {
      val newHash = md5(file)
      if (configHash != newHash) {
        println("Config change detected.")
        server ! Stop
        self ! LoadConfig
      }
      configHash = newHash
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
  def parseConfig(args: Array[String]): Config = {

    val configList: List[String] = argListToConfigurationList(args)

    val port = Integer.parseInt(configList(0))
    val log = configList.contains("-quiet")
    val logFunction = log match {
      case true  => quietLogger _
      case false => logToStdout _
    }
    val reverseProxies: List[ReverseProxy] = configList.tail
                                             .filterNot(_ == "-quiet")
                                             .map {
                                                    configItem =>
                                                      val parts: Array[String] = configItem.split("=")
                                                      new ReverseProxy(parts(0), parts(1), logFunction)
                                                  }
    Config(port, reverseProxies)
  }

  def md5(f: File) = {
    val lines: Iterator[String] = io.Source.fromFile(f).getLines()
    val contents = lines.addString(new StringBuilder(), "").toString()
    new String(MessageDigest.getInstance("MD5").digest(contents.getBytes()))
  }


  def watchFile(file: File) {
    configHash = md5(file)
    context.system.scheduler.schedule(10 seconds,
                                      5 seconds,
                                      self,
                                      WatchFile(file))

  }

  def argListToConfigurationList(args: Array[String]): List[String] = {
    val args2: Array[String] = args.length match {
      case 0 => Array(".bonza")
      case _ => args
    }

    val parts = args2(0) match {
      case file: String if (canRead(file)) => {
        watchFile(new File(file))
        io.Source.fromFile(file).getLines().toList
      }
      case _                               => {
        args2.toList
      }
    }
    //strip comments,trim whitespace and remove blank lines
    parts.map(p => stripComments(p))
    .map(_.trim())
    .filter(_.length > 0)
  }


  def stripComments(p: String): String = {
    if (p.indexOf("#") >= 0) {
      p.substring(0, p.indexOf("#"))
    } else {p}
  }

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

}

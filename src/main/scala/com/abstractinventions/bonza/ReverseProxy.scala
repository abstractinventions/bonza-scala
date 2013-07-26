package com.abstractinventions.bonza

import unfiltered.netty.{ReceivedMessage, ServerErrorResponse, async}
import unfiltered.request.{HttpRequest, Path}
import java.net.URI
import dispatch.{Req, Http, url}
import unfiltered.response._
import scala.collection.JavaConverters._
import org.apache.commons.io.IOUtils
import unfiltered.response.ResponseHeader
import unfiltered.response.ResponseBytes
import unfiltered.response.Status
import scala.concurrent.ExecutionContext.Implicits.global
import com.ning.http.client.Response


class ReverseProxy(prefix: String, destination: String, log:(Long,HttpRequest[Any], String, Response) => Unit) extends async.Plan
with ServerErrorResponse {



  def intent = {
    case req@Path(pathString) if pathString.startsWith(prefix) => {
      val startTime = System.currentTimeMillis()
      val proxiedRequest: Req = createProxiedRequest(pathString, req)
      for {
        resp <- Http(proxiedRequest)
      } {
        log(startTime, req, proxiedRequest.url, resp)
        req.respond(proxyResponse(resp, req))
      }
    }
  }

  /**
   * Attempt to adjust a location header so that it passes back through the reverse proxy.
   *
   * @param request
   * @param locationHeader
   * @return
   */
  def reverseMapLocationHeader(request: HttpRequest[Any], locationHeader: String): String = {

    if (locationHeader.startsWith(destination)) {
      val originalUri = new URI(request.uri)
      val remappedSuffix = locationHeader.substring(prefix.length)
      val newURI = new URI(originalUri.getScheme,
                           originalUri.getHost,
                           prefix + remappedSuffix,
                           originalUri.getQuery(),
                           originalUri.getFragment);
      newURI.toString
    } else {
      locationHeader;
    }
  }

  /**
   * Manipulate cookie header to remove the Secure property.
   *
   * @param cookieHeader
   * @return
   */
  def crunchCookie(request: HttpRequest[Any], cookieHeader: String): String = {
    if (!request.isSecure) {
      cookieHeader.split("; ").filter(_ != "Secure").mkString("; ")
    } else {
      cookieHeader
    }
  }

  def createProxiedRequest(pathString: String, req: HttpRequest[ReceivedMessage]) = {

    val requestUri = new URI(pathString)
    var dest = destination + requestUri.getPath.substring(prefix.length)
    if (requestUri.getQuery != null) {
      dest = dest + requestUri.getQuery
    }

    val proxiedRequest = url(dest).setMethod(req.method)
    req.headerNames.foreach(name => req.headers(name).foreach(value => proxiedRequest.addHeader(name, value)))
    proxiedRequest.setBody(IOUtils.toByteArray(req.inputStream))

  }

  def proxyResponse(resp: Response, req: HttpRequest[ReceivedMessage]): ResponseFunction[Any] = {
    val headerFunctions: Iterable[ResponseFunction[Any]] =
      mapAsScalaMapConverter(resp.getHeaders)
      .asScala
      .mapValues(_.asScala)
      .filter(p => p._1 != "Transfer-Encoding")
      .map {
             case ("Location", headerValues) => ResponseHeader("Location",
                                                               headerValues.map {loc => reverseMapLocationHeader(req, loc)})


             case ("Set-Cookie", headerValues) => ResponseHeader("Set-Cookie",
                                                                 headerValues.map {cookie => crunchCookie(req, cookie)})


             case (k, v) => ResponseHeader(k, v)

           }


    val responseBytes = ResponseBytes(resp.getResponseBodyAsBytes)

    // YUCK...
    // TODO : remove the mutable variable
    var rf = headerFunctions.head
    headerFunctions.tail.foreach(f => rf = rf ~> f)
    rf = rf ~> responseBytes
    rf = Status(resp.getStatusCode) ~> rf

    rf
  }

}

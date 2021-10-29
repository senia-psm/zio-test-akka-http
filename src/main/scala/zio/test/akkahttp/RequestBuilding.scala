package zio.test.akkahttp

import akka.http.scaladsl.marshalling.{Marshal, ToEntityMarshaller}
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.HttpCredentials
import zio.clock.Clock
import zio.{Has, RIO, ZIO}

import scala.collection.immutable
import scala.reflect.ClassTag

trait RequestBuilding {
  type RequestTransformer = HttpRequest => HttpRequest

  type EIO[+T] = RIO[Clock with Has[RouteTest.Config], T]

  class RequestBuilder(val method: HttpMethod) {
    def apply(): HttpRequest = apply("/")

    def apply(uri: String): HttpRequest = apply(uri, HttpEntity.Empty)

    def apply(uri: String, entity: RequestEntity): HttpRequest = apply(Uri(uri), entity)

    def apply(uri: Uri): HttpRequest = apply(uri, HttpEntity.Empty)

    def apply(uri: Uri, entity: RequestEntity): HttpRequest = HttpRequest(method, uri, Nil, entity)

    def apply[T](uri: String, content: T)(implicit m: ToEntityMarshaller[T]): EIO[HttpRequest] =
      apply(Uri(uri), content)

    def apply[T](uri: Uri, content: T)(implicit m: ToEntityMarshaller[T]): EIO[HttpRequest] =
      ZIO.access[Has[RouteTest.Config]](_.get.marshallingTimeout).flatMap { timeout =>
        ZIO
          .fromFuture(implicit ec => Marshal(content).to[RequestEntity])
          .timeoutFail(new RuntimeException(s"Failed to marshal request entity within $timeout"))(timeout)
          .map(apply(uri, _))
      }
  }

  val Get     = new RequestBuilder(GET)
  val Post    = new RequestBuilder(POST)
  val Put     = new RequestBuilder(PUT)
  val Patch   = new RequestBuilder(PATCH)
  val Delete  = new RequestBuilder(DELETE)
  val Options = new RequestBuilder(OPTIONS)
  val Head    = new RequestBuilder(HEAD)

  // TODO: reactivate after HTTP message encoding has been ported
  // def encode(encoder: Encoder): RequestTransformer = encoder.encode(_, flow)

  def addHeader(header: HttpHeader): RequestTransformer = _.mapHeaders(header +: _)

  def addHeader(headerName: String, headerValue: String): RequestTransformer =
    HttpHeader.parse(headerName, headerValue) match {
      case HttpHeader.ParsingResult.Ok(h, Nil) => addHeader(h)
      case result                              => throw new IllegalArgumentException(result.errors.head.formatPretty)
    }

  def addHeaders(first: HttpHeader, more: HttpHeader*): RequestTransformer = _.mapHeaders(_ ++ (first +: more))

  def mapHeaders(f: immutable.Seq[HttpHeader] => immutable.Seq[HttpHeader]): RequestTransformer = _.mapHeaders(f)

  def removeHeader(headerName: String): RequestTransformer =
    _ mapHeaders (_ filterNot (_.name equalsIgnoreCase headerName))

  def removeHeader[T <: HttpHeader : ClassTag]: RequestTransformer = removeHeader(implicitly[ClassTag[T]].runtimeClass)

  def removeHeader(clazz: Class[_]): RequestTransformer = _ mapHeaders (_ filterNot clazz.isInstance)

  def removeHeaders(names: String*): RequestTransformer =
    _ mapHeaders (_ filterNot (header => names exists (_ equalsIgnoreCase header.name)))

  def addCredentials(credentials: HttpCredentials) = addHeader(headers.Authorization(credentials))
}

object RequestBuilding extends RequestBuilding

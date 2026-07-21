# zio-test-akka-http

|  CI | Release |
| --- | --- |
| [![Build Status][Badge-Actions]][Link-Actions] | [![Release Artifacts][Badge-SonatypeReleases]][Link-SonatypeReleases] |


`Akka-HTTP` route TestKit for `zio-test`

```sbt
libraryDependencies += "info.senia" %% "zio-test-akka-http" % "x.x.x"
```

## Usage

The basic structure of a test built with the testkit is this (expression placeholder in all-caps):
```
(REQUEST ~> ROUTE).map { res =>
  assertTrue(res.some.path == value)
}
```

See [RouteZIOSpecDefaultSpec.scala](src/test/scala/zio/test/akkahttp/RouteZIOSpecDefaultSpec.scala) for usage examples.

## Imports

Import `zio.test.akkahttp.assertions._` and provide `RouteTestEnvironment` to use this testkit:

```scala
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.model.HttpResponse
import zio.test.Assertion._
import zio.test._
import zio.test.akkahttp.RouteTestEnvironment
import zio.test.akkahttp.assertions._

object MySpec extends ZIOSpecDefault {
  def spec =
    suite("MySpec")(
      test("my test") {
        (Get() ~> complete(HttpResponse())).map { res =>
          assertTrue(res.handled.get.response == HttpResponse())
        }
      }
    ).provideShared(RouteTestEnvironment.environment)
}
```

Alternatively you can use `DefaultAkkaRunnableSpec` without importing `assertions._` and providing additional environment:

```scala
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.model.HttpResponse
import zio.test.Assertion._
import zio.test._
import zio.test.akkahttp.AkkaZIOSpecDefault

object MySpec extends AkkaZIOSpecDefault {
  def spec =
    suite("MySpec")(
      test("my test") {
        (Get() ~> complete(HttpResponse())).map { res =>
          assertTrue(res.handled.get.response == HttpResponse())
        }
      }
    )
}
```


## Request creation

You can use `HttpRequest` or `ZIO[R, E, HttpRequest]` as request:

```scala
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.server.Directives.complete
import zio.test.Assertion._
import zio.test._
import zio.test.akkahttp.assertions._

(Get() ~> complete(HttpResponse())).map { res =>
  assertTrue(res.handled.get.response == HttpResponse())
}
```

Available request builders: `Get`,`Post`,`Put`,`Patch`,`Delete`,`Options`,`Head`.

## Request modification

You can use any function `HttpRequest => HttpRequest` to modify request.

There are several common request modifications provided: `addHeader`,`mapHeaders`,`removeHeader`,`addCredentials`:

```scala
Get() ~> addHeader("MyHeader", "value") ~> route
```

You can also add header with `~>` method:
```scala
Get() ~> RawHeader("MyHeader", "value") ~> route
```

## Assertions

There are several methods on response available:

- `handled`
- `response`
- `responseEntity`
- `chunks`
- `entityAs`
- `responseAs`
- `contentType`
- `mediaType`
- `charset`
- `headers`
- `header`
- `status`
- `closingExtension`
- `trailer`
- `rejected`
- `isWebSocketUpgrade`
- `webSocketUpgradeProtocol`

There should be a method for every [inspector from original Akka-HTTP Route TestKit](https://doc.akka.io/docs/akka-http/current/routing-dsl/testkit.html#table-of-inspectors). If you can't find a method for existing inspector please open an issue.

Note that some methods are available only on eager version of response, but not on lazy one. You can always convert lazy response to eager with `toEager` method.

It's sage to call `get` on `Option` inside of `asertTrue` macros.

Note that response is eager on response fetching by default - you can't use `~>` for status code and headers if route returns an infinite body.

To avoid eager response body fetching use lazy `?~>` method instead of the last `~>`:

```scala
import akka.http.scaladsl.model.StatusCodes.OK
import akka.http.scaladsl.server.Directives.{complete, get, respondWithHeader}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Directives
import akka.stream.scaladsl.Source
import akka.util.ByteString
import zio.test.Assertion._
import zio.test._
import zio.test.akkahttp.RouteTestEnvironment
import zio.test.akkahttp.assertions._

val pinkHeader = RawHeader("Fancy", "pink")

val route = get {
  respondWithHeader(pinkHeader) {
    complete(HttpEntity(ContentTypes.`application/octet-stream`, Source.repeat(ByteString("abc"))))
  }
}

(Get() ?~> route).map { res =>
  assertTrue(
    res.handled.get.status == OK,
    res.handled.get.header("Fancy").get == pinkHeader,
  )
}
```



[Badge-Actions]: https://github.com/senia-psm/zio-test-akka-http/actions/workflows/scala.yml/badge.svg?branch=master
[Badge-SonatypeReleases]: https://img.shields.io/maven-central/v/info.senia/zio-test-akka-http_2.13.svg "Maven Central Releases"

[Link-Actions]: https://github.com/senia-psm/zio-test-akka-http/actions
[Link-SonatypeReleases]: https://central.sonatype.com/artifact/info.senia/zio-test-akka-http_2.13 "Maven Central Releases"

# zio-test-akka-http

|  CI | Release | Issues | Snapshot |
| --- | --- | --- | --- |
| [![Build Status][Badge-Actions]][Link-Actions] | [![Release Artifacts][Badge-SonatypeReleases]][Link-SonatypeReleases] | [![Average time to resolve an issue][Badge-IsItMaintained]][Link-IsItMaintained] | [![Snapshot Artifacts][Badge-SonatypeSnapshots]][Link-SonatypeSnapshots] |


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

See [RouteDefaultRunnableSpec.scala](https://github.com/senia-psm/zio-test-akka-http/blob/master/src/test/scala/zio/test/akkahttp/RouteDefaultRunnableSpec.scala) for usage examples.

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

There are several assertions available:

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
- `rejection`
- `isWebSocketUpgrade`

There should be an assertion for every [inspector from original Akka-HTTP Route TestKit](https://doc.akka.io/docs/akka-http/current/routing-dsl/testkit.html#table-of-inspectors). If you can't find an assertion for existing inspector please open an issue.

Note that assertions are eager on response fetching by default - you can't use assertions for status code and headers if route returns an infinite body.

To avoid eager response body fetching use lazy `?~>` method instead of last `~>`:

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



[Badge-Actions]: https://github.com/senia-psm/zio-test-akka-http/workflows/Scala%20CI/badge.svg?branch=master
[Badge-SonatypeReleases]: https://img.shields.io/nexus/r/https/oss.sonatype.org/info.senia/zio-test-akka-http_2.13.svg "Sonatype Releases"
[Badge-SonatypeSnapshots]: https://img.shields.io/nexus/s/https/oss.sonatype.org/info.senia/zio-test-akka-http_2.13.svg "Sonatype Snapshots"
[Badge-IsItMaintained]: http://isitmaintained.com/badge/resolution/senia-psm/zio-test-akka-http.svg "Average time to resolve an issue"

[Link-Actions]: https://github.com/senia-psm/zio-test-akka-http/actions?query=workflow%3A%22Scala+CI%22+branch%3Amaster
[Link-SonatypeReleases]: https://oss.sonatype.org/content/repositories/releases/info/senia/zio-test-akka-http_2.13/ "Sonatype Releases"
[Link-SonatypeSnapshots]: https://oss.sonatype.org/content/repositories/snapshots/info/senia/zio-test-akka-http_2.13/ "Sonatype Snapshots"
[Link-IsItMaintained]: http://isitmaintained.com/project/senia-psm/zio-test-akka-http "Average time to resolve an issue"

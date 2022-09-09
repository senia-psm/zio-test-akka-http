package zio.test.akkahttp

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.HttpMethods.{GET, PUT}
import akka.http.scaladsl.model.StatusCodes.{InternalServerError, OK}
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import akka.http.scaladsl.server.Directives.{complete, get, put, respondWithHeader}
import akka.http.scaladsl.server.MethodRejection
import akka.pattern.ask
import akka.stream.scaladsl.Source
import akka.testkit.TestProbe
import akka.util.{ByteString, Timeout}
import zio.ZIO
import zio.test._

import scala.concurrent.duration.DurationInt

object AkkaZIOSpecDefaultSpec extends AkkaZIOSpecDefault {
  def spec =
    suite("ZioRouteTestSpec")(
      test("the most simple and direct route test") {
        (Get() ~> complete(HttpResponse())).map { res =>
          assertTrue(res.handled.get.response == HttpResponse())
        }
      },
      test("a test using a directive and some checks") {
        val pinkHeader = RawHeader("Fancy", "pink")

        val result = Get() ~> addHeader(pinkHeader) ~> {
          respondWithHeader(pinkHeader) {
            complete("abc")
          }
        }

        result.map { res =>
          assertTrue(
            res.handled.get.status == OK,
            res.handled.get.entity == HttpEntity(ContentTypes.`text/plain(UTF-8)`, "abc"),
            res.handled.get.header("Fancy").get == pinkHeader,
          )
        }
      },
      test("proper rejection collection") {
        val result = Post("/abc", "content") ~> {
          (get | put) {
            complete("naah")
          }
        }

        result.map { res =>
          assertTrue(res.rejected.get == List(MethodRejection(GET), MethodRejection(PUT)))
        }
      },
      test("separation of route execution from checking") {
        val pinkHeader = RawHeader("Fancy", "pink")

        case object Command

        for {
          system <- ZIO.service[ActorSystem]
          service = TestProbe()(system)
          handler = TestProbe()(system)
          resultFiber <- {
            implicit def serviceRef: ActorRef = service.ref

            implicit val askTimeout: Timeout = 1.second

            Get() ~> pinkHeader ~> {
              respondWithHeader(pinkHeader) {
                complete(handler.ref.ask(Command).mapTo[String])
              }
            }
          }.fork
          _ <- ZIO.attemptBlocking {
                 handler.expectMsg(Command)
                 handler.reply("abc")
               }
          res <- resultFiber.join
        } yield assertTrue(
          res.handled.get.status == OK,
          res.handled.get.entity == HttpEntity(ContentTypes.`text/plain(UTF-8)`, "abc"),
          res.handled.get.header("Fancy").get == pinkHeader,
        )
      },
      test("internal server error") {
        val route = get {
          throw new RuntimeException("BOOM")
        }

        (Get() ~> route).map { res =>
          assertTrue(res.handled.get.status == InternalServerError)
        }
      },
      test("infinite response") {
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
      },
    )
}

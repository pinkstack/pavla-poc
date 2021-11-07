package com.pinkstack

import cats._
import cats.data.EitherT
import cats.implicits._
import cats.effect._
import com.monovore.decline._
import com.monovore.decline.effect._
import io.circe.Json
import org.typelevel.log4cats.{SelfAwareLogger, SelfAwareStructuredLogger}
import org.typelevel.log4cats.slf4j.Slf4jLogger
import sttp.client3.{SttpBackend, basicRequest}
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend
import sttp.client3.circe.asJson

import java.text.SimpleDateFormat
import java.util.Date
import scala.reflect.io.Path
import scala.util.Try
import sttp.client3.{SttpBackend, _}
import sttp.model.Uri
import scala.jdk.CollectionConverters._

object Spatial {

  import org.apache.lucene.util.SloppyMath.haversinMeters

  def haversinDistance(a: Position, b: Position): Double =
    haversinMeters(a.latitude, a.longitude, b.latitude, b.longitude)
}


final case class ArcGIS[F[_] : Sync](arcGISApiKey: String,
                                     logger: SelfAwareStructuredLogger[F])
                                    (implicit backend: SttpBackend[F, _]) {
  case class NoAddressFound(message: String) extends Exception(message)

  private val root: Uri = uri"https://geocode-api.arcgis.com"
  private val rootWith: (String, Map[String, String]) => Uri = (path, params) =>
    root.withWholePath(path).withParams(params ++ Map("token" -> arcGISApiKey, "f" -> "pjson"))

  private val geocodeServer: (String, Map[String, String]) => Uri = (path, params) =>
    rootWith("/arcgis/rest/services/World/GeocodeServer/" + path, params)

  def findAddressCandidates(address: String): F[Either[NoAddressFound, Address]] = {
    val toAddress: Json => Option[Address] =
      _.\\("candidates").flatMap { candidate =>
        for {
          address <- candidate.\\("address").headOption.flatMap(_.asString)
          location <- candidate.\\("location").headOption
          longitude <- location.\\("x").headOption.flatMap(_.as[Double].get(0))
          latitude <- location.\\("y").headOption.flatMap(_.as[Double].get(0))
        } yield Address(address, Position(latitude, longitude))
      }.headOption

    basicRequest.get(
      geocodeServer("findAddressCandidates", Map("address" -> address))
    ).response(asJson[Json].getRight)
      .send(backend)
      .map(response =>
        toAddress(response.body).toRight(NoAddressFound(s"No address ${address} was found.")))
  }
}

final case class OnTime[F[_] : Sync]()(implicit backend: SttpBackend[F, _]) {

}

final case class PromInfo[F[_] : Sync]()
                                      (implicit backend: SttpBackend[F, _]) {
  case class NoStationsFound(message: String) extends Exception(message)

  val bicikeljRootLayer: String => Uri = { layer =>
    val url =
      s"""https://prominfo.projekti.si/web/api/MapService/Query/${layer}/
         |query?returnGeometry=true&where=1%3D1&outSr=4326&inSr=4326
         |&geometry=%7B%22xmin%22%3A14.0321%2C%22ymin%22%3A45.7881%2C%22xmax%22%3A14
         |.8499%2C%22ymax%22%3A46.218%2C%22spatialReference%22%3A%7B%22wkid%22%3A4326
         |%7D%7D&geometryType=esriGeometryEnvelope&spatialRel=esriSpatialRelContains&f=json&outFields=*""".stripMargin
    val urlx = url.split('\n').map(_.trim.filter(_ >= ' ')).mkString
    uri"${urlx}"
  }

  def bicikeljStations(): F[Either[NoStationsFound, List[BikeStation]]] = {
    val toBikeStands: Json => List[BikeStation] = { json =>
      json.\\("features").flatMap { feature =>
        for {
          position <- feature.\\("geometry").flatMap { geometry =>
            for {
              longitude <- geometry.\\("x").flatMap(_.as[Double].get(0))
              latitude <- geometry.\\("y").flatMap(_.as[Double].get(0))
            } yield Position(latitude, longitude)
          }

          a <- feature.\\("attributes").flatMap { attributes =>
            for {
              address <- attributes.\\("address").flatMap(_.as[String].get(0)).headOption
              name <- attributes.\\("name").flatMap(_.as[String].get(0)).headOption
              state <- attributes.\\("bike_station_state").flatMap(_.as[String].get(0)).headOption
              connectionState <- attributes.\\("bike_station_connection_state").flatMap(_.as[String].get(0)).headOption
              count <- attributes.\\("bike_stand_count").flatMap(_.as[Int].get(0)).headOption
              free <- attributes.\\("bike_stand_free").flatMap(_.as[Int].get(0)).headOption
              busy <- attributes.\\("bike_stand_busy").flatMap(_.as[Int].get(0)).headOption
            } yield (name, address, state, connectionState, count, free, busy)
          }
        } yield BikeStation(a._1, Address(a._2, position), a._3, a._4, a._5, a._6, a._7)
      }
    }

    basicRequest.get(
      bicikeljRootLayer("lay_bicikelj")
    ).response(asJson[Json].getRight)
      .send(backend)
      .map(response => {
        val stands: List[BikeStation] = toBikeStands(response.body).distinctBy(_.name)
        Either.cond(stands.nonEmpty, stands, NoStationsFound("No Bicike(LJ) stations were found"))
      })
  }
}

object PathFinderApp extends CommandIOApp(
  name = "path-finder",
  header = "Find sustainable way from A to B"
) {

  import Filters._

  def mkAsyncBackend[F[_] : Async](logger: SelfAwareStructuredLogger[F]): Resource[F, SttpBackend[F, _]] =
    AsyncHttpClientCatsBackend.resource[F]()
      .evalTap(_ => logger.info("Booting Async HTTP backend."))
      .onFinalize(logger.info("Async HTTP backend done."))

  def main: Opts[IO[ExitCode]] = {
    val arcGISApiKeyOpt = Opts.option[String]("arcgis-api-key", "ArcGI API Key")
    val sourceLocationOpt = Opts.option[String]("source-location", "Source location")
    val targetLocationOpt = Opts.option[String]("target-location", "Target location")

    (arcGISApiKeyOpt, sourceLocationOpt, targetLocationOpt).mapN { (arcGISApiKey, source, target) =>
      for {
        _ <- {
          for {
            logger <- Resource.liftK(Slf4jLogger.fromName[IO]("path-finder"))
            backend <- mkAsyncBackend[IO](logger)
          } yield (logger, backend)
        }.use { case (logger, backend) =>
          implicit val be: SttpBackend[IO, _] = backend
          val arc = ArcGIS[IO](arcGISApiKey, logger)
          val promInfo = PromInfo[IO]()

          for {
            _ <- logger.info(s"Looking for ${source} -> ${target}")
            a <- arc.findAddressCandidates(source).rethrow
            b <- arc.findAddressCandidates(target).rethrow
            bikeStations <- promInfo.bicikeljStations().rethrow
            _ <- IO {
              println(s"📍 ${a}")
              println(s"📍 ${b}")

              bikeStations.filterAvailable.orderNearest(a.position).foreach { case (stand, distance) =>
                println(s"🚲 => ${stand.name} ${stand.address} - ${stand.free} / ${distance}")
              }

            }
          } yield ()
        }
      } yield ExitCode.Success
    }
  }
}

object Filters {
  implicit class BikeStationFilters(val stations: List[BikeStation]) {
    val filterAvailable: List[BikeStation] = stations.filter(_.free > 0)

    def orderNearest(position: Position): List[(BikeStation, Double)] =
      stations
        .map { station =>
          println(s"${position} -> ${station.address.position}")
          (station, Spatial.haversinDistance(position, station.address.position))
        }
        .sortBy(_._2)
  }

}

import javax.jms._
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory
import org.slf4j.LoggerFactory
import cats.effect._
import cats.implicits._
import scala.concurrent.duration._

class PublisherLib(config: AppConfig) {
  private val logger = LoggerFactory.getLogger(getClass)
  private val connectionFactory = new ActiveMQConnectionFactory(
    config.brokerUrl
  )

  // Resources for Connection, Session, and Producer
  private def makeConnection: Resource[IO, Connection] =
    Resource.make {
      IO.blocking {
        val conn = connectionFactory.createConnection(
          config.credentials.user,
          config.credentials.pass
        )
        conn.start()
        logger.info(
          "Successfully connected to the broker at ${config.brokerUrl}."
        )
        conn
      }
    } { conn => IO.blocking(conn.close()) }

  private def makeSession(connection: Connection): Resource[IO, Session] =
    Resource.make(
      IO.blocking(connection.createSession(false, Session.AUTO_ACKNOWLEDGE))
    ) { session =>
      IO.blocking(session.close())
    }

  private def makeProducer(
      session: Session,
      destination: Destination
  ): Resource[IO, MessageProducer] =
    Resource.make(IO.blocking(session.createProducer(destination))) {
      producer =>
        IO.blocking(producer.close())
    }

  private def createDestination(
      session: Session,
      pubConfig: PublisherConfig
  ): IO[Destination] =
    IO.blocking {
      if (pubConfig.isTopic) session.createTopic(pubConfig.destinationName)
      else session.createQueue(pubConfig.destinationName)
    }

  private def sendMessages(
      session: Session,
      producer: MessageProducer,
      pubConfig: PublisherConfig
  ): IO[Unit] = {
    (1 to pubConfig.numMessages).toList.traverse_ { i =>
      val messageText = pubConfig.payload.getOrElse(
        s"Message #${i} to ${pubConfig.destinationName}"
      )
      for {
        message <- IO.blocking(session.createTextMessage(messageText))
        _ <- IO.blocking(producer.send(message))
        _ <- IO(logger.info(s"Sent message: '$messageText'"))
        _ <- IO.sleep(500.millis)
      } yield ()
    }
  }

  def publish(pubConfig: PublisherConfig): IO[Unit] = {
    val resources = for {
      connection <- makeConnection
      session <- makeSession(connection)
      destination <- Resource.eval(createDestination(session, pubConfig))
      producer <- makeProducer(session, destination)
    } yield (session, producer)

    resources
      .use { case (session, producer) =>
        for {
          _ <- IO.blocking(producer.setDeliveryMode(DeliveryMode.PERSISTENT))
          _ <- IO(
            logger.info(
              s"Publisher ready. Sending ${pubConfig.numMessages} messages..."
            )
          )
          _ <- sendMessages(session, producer, pubConfig)
          _ <- IO(logger.info("Finished sending all messages."))
        } yield ()
      }
      .handleErrorWith {
        case e: JMSException =>
          IO.raiseError(
            new RuntimeException(s"A JMS error occurred: ${e.getMessage}", e)
          )
        case e: Exception =>
          IO.raiseError(
            new RuntimeException(
              s"An unexpected error occurred: ${e.getMessage}",
              e
            )
          )
      }
  }
}

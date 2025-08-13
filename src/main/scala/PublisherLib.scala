import javax.jms._
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory
import org.slf4j.LoggerFactory
import cats.effect._
import cats.implicits._
import scala.concurrent.duration._

class PublisherLib(config: AppConfig) { // <-- NEW: Receive config via constructor
  private val logger = LoggerFactory.getLogger(getClass)

  private val connectionFactory = new ActiveMQConnectionFactory(
    config.brokerUrl
  )

  // Manages the lifecycle of a JMS Connection using Cats Effect's Resource
  private def makeConnection: Resource[IO, Connection] =
    Resource.make(
      IO.blocking {
        val username = config.credentials.user
        val password = config.credentials.pass
        logger.info(s"Attempting to connect to the broker as user: '$username'")
        val conn = connectionFactory.createConnection(username, password)
        conn.start()
        logger.info("Successfully connected to the broker.")
        conn
      }
    )(conn =>
      IO.blocking(conn.close()).evalOn(scala.concurrent.ExecutionContext.global)
    )

  // Manages the lifecycle of a JMS Session
  private def makeSession(connection: Connection): Resource[IO, Session] =
    Resource.make(
      IO.blocking(connection.createSession(false, Session.AUTO_ACKNOWLEDGE))
    )(session =>
      IO.blocking(session.close())
        .evalOn(scala.concurrent.ExecutionContext.global)
    )

  def publish(pubConfig: PublisherConfig): IO[Unit] = {
    (for {
      connection <- makeConnection
      session <- makeSession(connection)
    } yield (session))
      .use { session =>
        for {
          destination <- IO.blocking {
            if (pubConfig.isTopic) {
              logger.info(
                s"Target destination type: Topic ('${pubConfig.destinationName}')"
              )
              session.createTopic(pubConfig.destinationName)
            } else {
              logger.info(
                s"Target destination type: Queue ('${pubConfig.destinationName}')"
              )
              session.createQueue(pubConfig.destinationName)
            }
          }
          publisher <- IO.blocking(session.createProducer(destination))
          _ <- IO.blocking(publisher.setDeliveryMode(DeliveryMode.PERSISTENT))
          _ <- IO(
            logger.info(
              s"Publisher ready. Sending ${pubConfig.numMessages} messages..."
            )
          )
          _ <- (1 to pubConfig.numMessages).toList.traverse_ { i =>
            val messageText = s"Message #${i} to ${pubConfig.destinationName}"
            for {
              message <- IO.blocking(session.createTextMessage(messageText))
              _ <- IO.blocking(publisher.send(message))
              _ <- IO(logger.info(s"Sent message: '$messageText'"))
              _ <- IO.sleep(500.millis)
            } yield ()
          }
          _ <- IO(
            logger.info("Finished sending all messages for this configuration.")
          )
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

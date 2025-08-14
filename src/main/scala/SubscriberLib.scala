import javax.jms._
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory
import org.slf4j.LoggerFactory
import cats.effect._
import cats.implicits._
import scala.concurrent.duration.FiniteDuration

class SubscriberLib(config: AppConfig) {
  private val logger = LoggerFactory.getLogger(getClass)
  private val connectionFactory = new ActiveMQConnectionFactory(
    config.brokerUrl
  )

  private def makeConnection(clientId: String): Resource[IO, Connection] =
    Resource.make(
      IO.blocking {
        val conn = connectionFactory
          .createConnection(config.credentials.user, config.credentials.pass)
        conn.setClientID(clientId)
        conn.start()
        logger.info(
          s"Successfully connected to broker at ${config.brokerUrl} with Client ID: $clientId"
        )
        conn
      }
    )(conn => IO.blocking(conn.close()))

  private def makeSession(connection: Connection): Resource[IO, Session] =
    Resource.make(
      IO.blocking(connection.createSession(false, Session.AUTO_ACKNOWLEDGE))
    )(session => IO.blocking(session.close()))

  private def makeConsumer(
      session: Session,
      topic: Topic,
      subName: String
  ): Resource[IO, MessageConsumer] = Resource.make(
    IO.blocking(session.createSharedDurableConsumer(topic, subName))
  )(consumer => IO.blocking(consumer.close()))

  private def receiveAllLoop(consumer: MessageConsumer): IO[Unit] = {
    IO.blocking(Option(consumer.receive(1000L))).flatMap {
      case Some(message: TextMessage) =>
        IO(logger.info(s"Received: '${message.getText}'")) >> receiveAllLoop(
          consumer
        )
      case Some(_) =>
        IO(logger.warn("Received a non-text message.")) >> receiveAllLoop(
          consumer
        )
      case None =>
        IO(logger.info("No more messages in the queue. Exiting."))
    }
  }

  private def receiveLastValue(
      consumer: MessageConsumer,
      timeout: FiniteDuration
  ): IO[Unit] = {
    IO(
      logger.info(
        s"Waiting for last-value message for up to ${timeout.toSeconds} seconds..."
      )
    ) >>
      IO.blocking(Option(consumer.receive(timeout.toMillis))).flatMap {
        case Some(message: TextMessage) =>
          IO(logger.info(s"Received last value: '${message.getText}'"))
        case Some(_) =>
          IO(logger.warn("Received a non-text last-value message."))
        case None => IO(logger.warn("No message received within the timeout."))
      }
  }

  def subscribe(
      name: String,
      topicName: String,
      lastValue: Boolean,
      timeout: FiniteDuration
  ): IO[Unit] = {
    val resources = for {
      connection <- makeConnection(name)
      session <- makeSession(connection)
      topic <- Resource.eval(IO.blocking(session.createTopic(topicName)))
      consumer <- makeConsumer(session, topic, name)
    } yield consumer

    resources
      .use { consumer =>
        if (lastValue) {
          receiveLastValue(consumer, timeout)
        } else {
          receiveAllLoop(consumer)
        }
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

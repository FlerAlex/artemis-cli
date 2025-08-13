import javax.jms._
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory
import org.slf4j.LoggerFactory
import cats.effect._
import cats.effect.std.Queue
import cats.implicits._
import fs2.Stream
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeoutException
import cats.effect.unsafe.implicits.global

class SubscriberLib(config: AppConfig) {
  private val logger = LoggerFactory.getLogger(getClass)

  private val connectionFactory = new ActiveMQConnectionFactory(
    config.brokerUrl
  )

  // Safely manage the JMS Connection resource
  private def makeConnection(clientId: String): Resource[IO, Connection] =
    Resource.make(
      IO.blocking {
        val username = config.credentials.user
        val password = config.credentials.pass
        logger.info(
          s"Attempting to connect to the broker as user: '$username' with Client ID: '$clientId'"
        )
        val conn = connectionFactory.createConnection(username, password)
        conn.setClientID(clientId)
        conn.start()
        logger.info("Successfully connected to the broker.")
        conn
      }
    )(conn =>
      IO.blocking(conn.close()).evalOn(scala.concurrent.ExecutionContext.global)
    )

  private def createSubscriber(
      session: Session,
      subscriberName: String,
      topicName: String,
      lastValue: Boolean
  ): IO[MessageConsumer] = IO.blocking {
    val destinationString = if (lastValue) {
      logger.info(
        s"Creating durable, non-destructive, last-value subscription for topic '$topicName'"
      )
      s"$topicName?default-last-value-queue=true&default-non-destructive=true"
    } else {
      logger.info(
        s"Creating standard durable subscription for topic '$topicName'"
      )
      topicName
    }
    val topic = session.createTopic(destinationString)
    session.createDurableSubscriber(topic, subscriberName)
  }

  // This method now terminates after one message or a timeout.
  def subscribe(
      subscriberName: String,
      topicName: String,
      lastValue: Boolean,
      timeout: FiniteDuration
  ): IO[Unit] = {
    val program = for {
      queue <- Resource.eval(Queue.bounded[IO, Message](100))
      consumer <- makeConnection(subscriberName).flatMap { conn =>
        Resource
          .make(
            IO.blocking(conn.createSession(false, Session.AUTO_ACKNOWLEDGE))
          )(session => IO.blocking(session.close()))
          .flatMap { session =>
            Resource.make(
              createSubscriber(session, subscriberName, topicName, lastValue)
            )(subscriber => IO.blocking(subscriber.close()))
          }
      }
    } yield (queue, consumer)

    program
      .use { case (queue, consumer) =>
        val listener = IO.blocking {
          consumer.setMessageListener { msg =>
            queue.offer(msg).unsafeRunAndForget()
          }
        }

        for {
          _ <- listener
          _ <- IO(
            logger.info(
              s"Subscriber '$subscriberName' started. Waiting for a message for up to $timeout..."
            )
          )
          maybeMsg <- queue.take.timeout(timeout).attempt
          _ <- maybeMsg match {
            case Right(msg) =>
              msg match {
                case textMessage: TextMessage =>
                  IO(
                    logger.info(
                      s"Successfully received and processed message: '${textMessage.getText}'"
                    )
                  )
                case _ =>
                  IO(logger.warn(s"Received a non-text message: $msg"))
              }
            case Left(_: TimeoutException) =>
              IO(
                logger.warn(
                  s"No message received within the $timeout timeout. Exiting."
                )
              )
            case Left(err) =>
              IO.raiseError(err)
          }
        } yield ()
      }
      .handleErrorWith { e =>
        IO.raiseError(
          new RuntimeException(s"Subscriber stopped due to a fatal error.", e)
        )
      }
  }
}

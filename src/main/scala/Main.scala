import cats.effect.{ExitCode, IO}
import cats.implicits._
import com.monovore.decline._
import com.monovore.decline.effect._
import org.slf4j.LoggerFactory
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import java.nio.file.Path
import pureconfig.ConfigSource
import pureconfig.module.catseffect._

enum Command {
  case Publish(config: PublisherConfig)
  case Subscribe(name: String, topic: String, lastValue: Boolean, timeout: FiniteDuration)
}

object Main extends CommandIOApp(
      name = "artemis-cli",
      header = "A CLI for interacting with ActiveMQ Artemis."
    ) {
  private val logger = LoggerFactory.getLogger(getClass)

  def run(command: Command, configFile: Path): IO[ExitCode] = {
    for {
      appConfig <- IO.fromEither(
        ConfigSource.file(configFile).at("app").load[AppConfig]
          .leftMap(failures => new RuntimeException(s"Failed to load config: ${failures.prettyPrint()}"))
      )
      exitCode <- command match {
        case Command.Publish(config) =>
          val publisher = new PublisherLib(appConfig)
          publisher.publish(config).as(ExitCode.Success)

        case Command.Subscribe(name, topic, lastValue, timeout) =>
          val subscriber = new SubscriberLib(appConfig)
          subscriber.subscribe(name, topic, lastValue, timeout).as(ExitCode.Success)
      }
    } yield exitCode
  }

  private val configFileOpt: Opts[Path] = Opts.option[Path]("config", short = "c", help = "Path to the application.conf file.")

  private val publishOpts: Opts[Command] =
    Opts.subcommand("publish", "Publish messages to a destination.") {
      val destinationOpt = Opts.option[String]("destination", short = "d", help = "Name of the destination (topic or queue).")
      val typeOpt = Opts.option[String]("type", short = "t", help = "Destination type: 'topic' or 'queue'.").mapValidated {
        case "topic" => true.validNel; case "queue" => false.validNel
        case other   => s"Invalid type: '$other'".invalidNel
      }
      val messagesOpt = Opts.option[Int]("messages", short = "m", help = "Number of messages to send.").withDefault(1)
      val payloadOpt = Opts.option[String]("payload", help = "JSON payload for the message body.").orNone

      (destinationOpt, typeOpt, messagesOpt, payloadOpt).mapN(PublisherConfig.apply).map(Command.Publish.apply)
    }

  private val subscribeOpts: Opts[Command] =
    Opts.subcommand("subscribe", "Subscribe to a durable topic.") {
      val nameOpt = Opts.option[String]("name", short = "n", help = "A unique name for the client ID and durable subscription.")
      val topicOpt = Opts.option[String]("topic", help = "The name of the topic to subscribe to.")
      val lastValueOpt = Opts.flag("last-value", "Connect to a last-value queue instead of a standard topic.").orFalse
      val timeoutOpt = Opts.option[Long]("timeout", help = "Timeout in seconds to wait for a message.").withDefault(5L).map(FiniteDuration(_, TimeUnit.SECONDS))

      (nameOpt, topicOpt, lastValueOpt, timeoutOpt).mapN(Command.Subscribe.apply)
    }

  override def main: Opts[IO[ExitCode]] = {
    val commandOpts = publishOpts orElse subscribeOpts
    (commandOpts, configFileOpt).tupled.map { case (command, configFile) =>
      run(command, configFile).handleErrorWith { err =>
        IO(logger.error(s"Command failed: ${err.getMessage}", err)).as(ExitCode.Error)
      }
    }
  }
}

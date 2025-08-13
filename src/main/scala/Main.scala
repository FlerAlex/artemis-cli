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
  case Subscribe(
      name: String,
      topic: String,
      lastValue: Boolean,
      timeout: FiniteDuration
  )
}

case class PublisherConfig(
    numMessages: Int,
    destinationName: String,
    isTopic: Boolean
)

object Main
    extends CommandIOApp(
      name = "artemis-cli",
      header = "A CLI for interacting with ActiveMQ Artemis."
    ) {
  private val logger = LoggerFactory.getLogger(getClass)

  // Centralized run method to handle command logic
  def run(appConfig: AppConfig, command: Command): IO[ExitCode] =
    command match {
      case Command.Publish(config) =>
        val publisher = new PublisherLib(appConfig)
        for {
          _ <- IO(
            logger.info(s"Executing publish command with config: $config")
          )
          _ <- publisher.publish(config)
          _ <- IO(logger.info("Publisher finished successfully."))
        } yield ExitCode.Success

      case Command.Subscribe(name, topic, lastValue, timeout) =>
        val subscriber = new SubscriberLib(appConfig)
        for {
          _ <- IO(
            logger.info(
              s"Executing subscribe command for '$name' on topic '$topic' (Last Value: $lastValue, Timeout: $timeout)"
            )
          )
          _ <- subscriber.subscribe(name, topic, lastValue, timeout)
        } yield ExitCode.Success
    }

  private val configFileOpt: Opts[Path] = Opts.option[Path](
    "config",
    short = "c",
    help = "Path to the application.conf file."
  )

  // Options for the 'publish' subcommand
  private val publishOpts: Opts[Command.Publish] =
    Opts.subcommand("publish", "Publish messages to a destination.") {
      val messagesOpt = Opts
        .option[Int](
          "messages",
          short = "m",
          help = "Number of messages to send."
        )
        .withDefault(1)
      val destinationOpt = Opts.option[String](
        "destination",
        short = "d",
        help = "Name of the destination (topic or queue)."
      )
      val typeOpt = Opts
        .option[String](
          "type",
          short = "t",
          help = "Destination type: 'topic' or 'queue'."
        )
        .mapValidated {
          case "topic" => true.validNel
          case "queue" => false.validNel
          case other =>
            s"Invalid destination type: '$other'. Must be 'topic' or 'queue'.".invalidNel
        }
      (messagesOpt, destinationOpt, typeOpt)
        .mapN(PublisherConfig.apply)
        .map(Command.Publish.apply)
    }

  // Options for the 'subscribe' subcommand
  private val subscribeOpts: Opts[Command.Subscribe] =
    Opts.subcommand("subscribe", "Subscribe to a durable topic.") {
      val nameOpt = Opts.option[String](
        "name",
        short = "n",
        help = "A unique name for the client ID and durable subscription."
      )
      val topicOpt = Opts
        .option[String](
          "topic",
          help = "The name of the topic to subscribe to."
        )
        .withDefault("DurableNewsTopic")
      val lastValueOpt = Opts
        .flag("last-value", "Enable last-value semantics for the topic.")
        .orFalse
      val timeoutOpt = Opts
        .option[Long](
          "timeout",
          help = "Timeout in seconds to wait for a message."
        )
        .withDefault(5L)
        .map(FiniteDuration(_, TimeUnit.SECONDS))
      (nameOpt, topicOpt, lastValueOpt, timeoutOpt).mapN(
        Command.Subscribe.apply
      )
    }

  // The main entry point now parses the global config option first, then the subcommand.
  override def main: Opts[IO[ExitCode]] = {
    val commandOpts: Opts[Command] = publishOpts orElse subscribeOpts

    (configFileOpt, commandOpts).mapN { (configPath, command) =>
      val loadConfig = IO.fromEither(
        ConfigSource
          .file(configPath)
          .at("app")
          .load[AppConfig]
          .leftMap(failures => new RuntimeException(failures.prettyPrint()))
      )

      loadConfig
        .flatMap(appConfig => run(appConfig, command))
        .handleErrorWith { err =>
          IO(logger.error(s"Command failed: ${err.getMessage}", err))
            .as(ExitCode.Error)
        }
    }
  }
}

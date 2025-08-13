import pureconfig.ConfigReader

// The 'derives' keyword handles the derivation automatically, so the old import is no longer needed.
case class Credentials(user: String, pass: String) derives ConfigReader
case class AppConfig(brokerUrl: String, credentials: Credentials)
    derives ConfigReader

// Configuration for the publish command
case class PublisherConfig(
    destinationName: String,
    isTopic: Boolean,
    numMessages: Int,
    payload: Option[String] // Add for json payload
)

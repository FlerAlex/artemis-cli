import pureconfig.ConfigReader

case class Credentials(user: String, pass: String) derives ConfigReader
case class AppConfig(brokerUrl: String, credentials: Credentials)
    derives ConfigReader

case class PublisherConfig(
    destinationName: String,
    isTopic: Boolean,
    numMessages: Int,
    payload: Option[String] 
)

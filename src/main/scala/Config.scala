import pureconfig.ConfigReader

// Define the case classes that model the structure of application.conf
case class Credentials(user: String, pass: String) derives ConfigReader
case class AppConfig(brokerUrl: String, credentials: Credentials)
    derives ConfigReader

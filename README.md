ToHttp
=======

ToHttp is a vey simple and rudimentary TCP/IP to HTTP forwarder.

You can use this tool to relay information sent out over a TCP/IP connection
to a receiving party over http/s protocol. The tool opens a TCP/IP listener
socket to which information can be sent to using standard TCP/IP protocol.
Information received by the tool on the TCP/IP listener socket will then be
relayed to the URL specified in the command-line.

## Usage
This tool needs to be run as follows,

`java -jar ToHttp.jar <listner socker> <forwarding URL>`

The URL can be specified to use eitheth http:// or https:// schemes.
If for https:// scheme a custom trustsore is needed, ensure that the
trust certificate is in the custom truststore and then run the tool as,

`java -Djavax.net.ssl.trustStore=<trust-store-name> -jar <listener socket> <forwarding URL>`


## License
ToHttp is released as a open source software under Apache License Version 2.0. Please
browse to https://www.apache.org/licenses/LICENSE-2.0 for details of the provisions of this
license.



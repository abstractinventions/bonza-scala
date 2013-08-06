package com.abstractinventions.bonza

import akka.actor.Actor
import unfiltered.netty.Http

case class Stop;

case class Start(config: Config);

/** embedded server */
class ServerActor extends Actor {

  var server: Http = null;

  def receive: Actor.Receive = {
    case Start(config) => {
      server = unfiltered.netty.Http(config.port)
      server = config.handlers.foldLeft(server)((svr, rp) => svr.handler(rp))
      runAsync(server, config)
    }
    case Stop          => {
      if (server != null) {
        println("stoping")
        server.stop();
      }
    }

  }


  /**
   * Run the given server on another thread so that akka can process messages.
   *
   */
  def runAsync(server: Http, config: Config) {
    val thread = new Thread(new Runnable {
      def run() {
        server.run {
                     s =>
                       println("Starting with configuration : \n\t" + config.toString().replaceAll("\n","\n\t"))
                   }
      }
    }, "main")
    thread.start()
  }
}

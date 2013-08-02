package com.abstractinventions.bonza

import akka.actor.{Props, ActorSystem}
import akka.event.Logging

object Bonza {

  def main(args: Array[String]) {
    val system = ActorSystem("BonzaSystem")
    val configActor = system.actorOf(Props(classOf[ConfigActor],args), "config")
    configActor ! LoadConfig
  }

}

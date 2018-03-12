package upmc.akka.leader

import java.util
import java.util.Date

import akka.actor._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

abstract class Tick
case class CheckerTick () extends Tick

class CheckerActor (val id:Int, val terminaux:List[Terminal], electionActor:ActorRef) extends Actor {

  var time : Int = 2000 // 200
  val father = context.parent

  var nodesAlive:List[Int] = List()
  var datesForChecking:List[Date] = List()
  var lastDate:Date = null
  var electionStart: Boolean = false;

  var leader : Int = -1

  val scheduler : Scheduler = context.system.scheduler

  def receive = {

    // Initialisation
    case Start => {

      scheduler.schedule(2000 milliseconds, time milliseconds,self,CheckerTick)
      //datesForChecking = initializeDateForCheckingList(5,time)
      //self ! CheckerTick
    }

    // A chaque fois qu'on recoit un Beat : on met a jour la liste des nodes
    case IsAlive (nodeId) => {
      if (!nodesAlive.contains(nodeId)){
        nodesAlive = nodesAlive ::: List(nodeId)
      }
    }

    case IsAliveLeader (nodeId) => {
      if (leader < 0) {
        leader = nodeId
        electionActor ! Reset
        electionStart = false
      }
      if (!nodesAlive.contains(nodeId)){
        nodesAlive = nodesAlive ::: List(nodeId)
      }
    }

    // A chaque fois qu'on recoit un CheckerTick : on verifie qui est mort ou pas
    // Objectif : lancer l'election si le leader est mort
    case CheckerTick => {
      //father ! Message("I AM Node("+id+") and the leader is Node("+leader+") election status : "+electionStart)
      if (!nodesAlive.contains(leader) && id != leader) {
        if (!electionStart) {
          electionActor ! StartWithNodeList(nodesAlive)
          electionStart = true
        }
      }
      if (!electionStart) {
        leader = -1
        lastDate = new Date(time)
        nodesAlive = Nil
      }
    }

  }

  def initializeDateForCheckingList (interval: Int, time: Int) : List[Date] =
    time match {
      case 0 => Nil
      case t => {
        new Date(t) :: initializeDateForCheckingList(interval,time-interval)
      }
    }

}

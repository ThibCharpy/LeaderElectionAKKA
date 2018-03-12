package upmc.akka.leader

import java.util.Date

import akka.actor._

abstract class NodeStatus
case class Passive () extends NodeStatus
case class Candidate () extends NodeStatus
case class Dummy () extends NodeStatus
case class Waiting () extends NodeStatus
case class Leader () extends NodeStatus

abstract class LeaderAlgoMessage
case class Initiate () extends LeaderAlgoMessage
case class ALG (list:List[Int], nodeId:Int) extends LeaderAlgoMessage
case class AVS (list:List[Int], nodeId:Int) extends LeaderAlgoMessage
case class AVSRSP (list:List[Int], nodeId:Int) extends LeaderAlgoMessage

case class StartWithNodeList (list:List[Int])
case class Reset()

class ElectionActor (val id:Int, val terminaux:List[Terminal]) extends Actor {

  val father = context.parent
  var nodesAlive:List[Int] = List(id)

  var candSucc:Int = -1
  var candPred:Int = -1
  var status:NodeStatus = Passive ()

  var initiateDone:Boolean = false

  def receive = {

    // Initialisation

    case Reset => {
      //candPred = -1
      //candSucc = -1
      nodesAlive = Nil
      status = Passive()
    }

    case Start => {
      terminaux.foreach(t => {
        nodesAlive:::List(t.id)
      })
      self ! Initiate
    }

    case StartWithNodeList (list) => {
      father ! Message("ELECTION START ! status:"+status)
      if (list.isEmpty) {
        this.nodesAlive = this.nodesAlive:::List(id)
      }
      else {
        this.nodesAlive = list
      }
      if (status == Passive()) {
        father ! Message("START WITH : " + nodesAlive.toString() + " at : " + new Date(System.currentTimeMillis()) + ".")
        self ! Initiate
      }else{
        father ! Message("I am Dummy ..")
      }
    }

    case Initiate => {
      status = Candidate()
      candSucc = -1
      candPred = -1
      var nextIndex = nodesAlive.last
      val next = terminaux.filter(t => t.id == nextIndex).head
      val remote = context.actorSelection("akka.tcp://LeaderSystem" + next.id + "@" + next.ip + ":" + next.port + "/user/Node")
      father ! Message("INITIATE Node(" + id + ").")
      remote ! ALG(nodesAlive, id)
      father ! Message("Send ALG(" + id + ") to Node(" + nextIndex + ") at : " + new Date(System.currentTimeMillis()) + " to " + remote + ".")
    }

    case ALG (list, init) =>
      status match {
        case Passive() => {
          status = Dummy()
          var nextIndex = -1
          if (list.head == id) {
            nextIndex = init
          } else {
            nextIndex = list.head
          }
          val next = terminaux.filter(t => t.id == nextIndex).head
          val remote = context.actorSelection("akka.tcp://LeaderSystem" + next.id + "@" + next.ip + ":" + next.port + "/user/Node")
          father ! Message("nodesalive : "+nodesAlive.toString())
          father ! Message("list : "+list.toString())
          father ! Message("ALG : I was Passive so set my status to Dummy and forward ALG(" + init + ") to Node(" + nextIndex + ") at : "+new Date(System.currentTimeMillis())+".")
          remote ! ALG(list, init)
        }
        case Candidate() => {
          candPred = init
          if (id > init) {
            if (candSucc < 0) { // case AVS(k) not receive
              status = Waiting()
              val next = terminaux.filter(t => t.id == init).head
              val remote = context.actorSelection("akka.tcp://LeaderSystem" + next.id + "@" + next.ip + ":" + next.port + "/user/Node")
              father ! Message("ALG : I am Candidate ! so set my status to Waiting and send AVS("+id+") to Node("+init+") at : "+new Date(System.currentTimeMillis())+".")
              remote ! AVS(nodesAlive, id)
            } else { // case already receive AVS(k) where k == candsucc
              val kTerminal = terminaux.filter(t => t.id == candSucc).head
              val kRemote = context.actorSelection("akka.tcp://LeaderSystem" + kTerminal.id + "@" + kTerminal.ip + ":" + kTerminal.port + "/user/Node")
              father ! Message("ALG : I am Candidate but already receive AVS("+candSucc+")\n" +
                "so set my status to Dummy and send AVSRSP("+candPred+") to Node("+candSucc+") at : "+new Date(System.currentTimeMillis())+".")
              kRemote ! AVSRSP(list,candPred)
              status = Dummy()
            }
          }
          if (id == init) { // No other candidates
            status = Leader()
            father ! Message("ALG : No candidates against me so I am Node("+id+") and I am the new Leader at : "+new Date(System.currentTimeMillis())+".")
            father ! LeaderChanged(id)
          }
        }
        case Dummy() => {
          var nextIndex = list.head
          val next = terminaux.filter( t => t.id == nextIndex).head
          val remote = context.actorSelection("akka.tcp://LeaderSystem" + next.id + "@" + next.ip + ":" + next.port + "/user/Node")
          remote ! ALG(list,init)
        }
        case Waiting() => father ! Message("WAITING..")
        case Leader() => father ! LeaderChanged(id)
      }

    case AVS (list, j) =>
      status match {
        case Candidate() => {
          if (0 < candPred) {
            val jTerminal = terminaux.filter(t => t.id == j).head
            val jRemote = context.actorSelection("akka.tcp://LeaderSystem" + jTerminal.id + "@" + jTerminal.ip + ":" + jTerminal.port + "/user/Node")
            father ! Message("AVS : I am Candidate but already know my pred Node("+candPred+") so I will not be electe..\n" +
              "then set my status to Dummy and send an AVSRSP("+candPred+") to j which is Node("+j+") at : "+new Date(System.currentTimeMillis())+".")
            jRemote ! AVSRSP(nodesAlive, candPred)
            status = Dummy()
          } else {
            father ! Message("AVS : I am Candidate and does not know my pred Node so just set my succ Node as Node("+j+") at : "+new Date(System.currentTimeMillis())+".")
            candSucc = j
          }
        }
        case Waiting () => {
          father ! Message("AVS : I am Waiting... so just set my succ Node as Node("+j+") at : "+new Date(System.currentTimeMillis())+".")
          candSucc = j
        }
      }

    case AVSRSP (list, k) =>
      status match {
        case Waiting() => {
          if (id == k) {
            father ! Message("AVSRSP : I am Waiting... but i am alone to wait so I am the new leader and I am Node("+id+") at : "+new Date(System.currentTimeMillis())+".")
            status = Leader()
            father ! LeaderChanged(id)
          } else {
            candPred = k
            if (candSucc < 0) {
              if (k < id) {
                status = Waiting()
                val kTerminal = terminaux.filter(t => t.id == k).head
                val kRemote = context.actorSelection("akka.tcp://LeaderSystem" + kTerminal.id + "@" + kTerminal.ip + ":" + kTerminal.port + "/user/Node")
                father ! Message("AVSRSP : I am Candidate but I am Waiting... so set my status to Waiting and send AVS(" + id + ") to Node(" + k + ")at : "+new Date(System.currentTimeMillis())+".")
                kRemote ! AVS(nodesAlive, id)
              }
            } else {
              status = Dummy()
              val kTerminal = terminaux.filter(t => t.id == candSucc).head
              val kRemote = context.actorSelection("akka.tcp://LeaderSystem" + kTerminal.id + "@" + kTerminal.ip + ":" + kTerminal.port + "/user/Node")
              father ! Message("AVSRSP : I am Candidate but I am Waiting... and already receive AVS("+candSucc+")\n" +
                "so set my status to Dummy and send AVSRSP("+k+") to Node("+candSucc+") at : "+new Date(System.currentTimeMillis())+".")
              kRemote ! AVSRSP(list,k)
            }
          }
        }
      }

  }

}

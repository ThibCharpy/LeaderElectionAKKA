package upmc.akka.leader

import akka.actor._

case class Start ()

sealed trait SyncMessage
case class Sync (nodes:List[Int]) extends SyncMessage
case class SyncForOneNode (nodeId:Int, nodes:List[Int]) extends SyncMessage

sealed trait AliveMessage
case class IsAlive (id:Int) extends AliveMessage
case class IsAliveLeader (id:Int) extends AliveMessage

class Node (val id:Int, val terminaux:List[Terminal]) extends Actor {

  // Les differents acteurs du systeme
  val electionActor = context.actorOf(Props(new ElectionActor(this.id, terminaux)), name = "electionActor")
  val checkerActor = context.actorOf(Props(new CheckerActor(this.id, terminaux, electionActor)), name = "checkerActor")
  val beatActor = context.actorOf(Props(new BeatActor(this.id)), name = "beatActor")
  val displayActor = context.actorOf(Props[DisplayActor], name = "displayActor")

  var allNodes:List[ActorSelection] = List()

  def receive = {

    // Initialisation
    case Start => {
      displayActor ! Message ("Node " + this.id + " is created")
      checkerActor ! Start
      beatActor ! Start

      // Initilisation des autres remote, pour communiquer avec eux
      terminaux.foreach(n => {
        if (n.id != id) {
          val remote = context.actorSelection("akka.tcp://LeaderSystem" + n.id + "@" + n.ip + ":" + n.port + "/user/Node")
          // Mise a jour de la liste des nodes
          this.allNodes = this.allNodes:::List(remote)
          displayActor ! Message("Node("+id+"): "+n.toString+" ("+remote.toString()+").")
        }
      })
    }

    // Envoi de messages (format texte)
    case Message (content) => {
      displayActor ! Message ("Node("+id+"): "+content)
    }

    case BeatLeader (nodeId) => {
      allNodes.foreach(node => {
        //displayActor ! Message("IsAliveLeader send to Node ("+node.toString()+").")
        node ! IsAliveLeader(nodeId)
      })
      checkerActor ! IsAliveLeader(nodeId)
    }

    case Beat (nodeId) => allNodes.foreach( node => {
      //displayActor ! Message("IsAlive send to Node ("+node.toString()+").")
      node ! IsAlive(nodeId)
    })

    // Messages venant des autres nodes : pour nous dire qui est encore en vie ou mort
    case IsAlive (id) => {
      //displayActor ! Message ("Node("+id+"): IsAlive from Node("+id+").")
      checkerActor ! IsAlive(id)
    }

    case IsAliveLeader (id) => {
      //displayActor ! Message ("Node("+id+"): IsAlive from Leader Node("+id+").")
      checkerActor ! IsAliveLeader(id)
    }

    // Message indiquant que le leader a change
    case LeaderChanged (nodeId) => beatActor ! LeaderChanged(nodeId)

    // Relaye les messages destinés à l'acteur d'éléction
    case ALG(list,init) => electionActor ! ALG (list,init)
    case AVS(list,j) => electionActor ! AVS (list,j)
    case AVSRSP(list,k) => electionActor ! AVSRSP (list,k)

  }

}

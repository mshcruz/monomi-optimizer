import scala.collection.mutable._

object Onions {
  final val DET = 0x1
  final val OPE = 0x1 << 1
  final val HOM = 0x1 << 2
  final val SWP = 0x1 << 3

  def str(o: Int): String = o match {
    case DET => "DET"
    case OPE => "OPE"
    case HOM => "HOM"
    case SWP => "SWP"
  }

  sealed abstract trait OnionSetType
  case object Projection extends OnionSetType
  case object Filter extends OnionSetType 
  case object GroupBy extends OnionSetType
  case object OrderBy extends OnionSetType
}

class OnionSet(val tpe: Onions.OnionSetType) {
  val opts = new HashMap[(String, Column), Int] // int is Onions bitmask
}

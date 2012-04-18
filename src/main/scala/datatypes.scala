abstract trait DataType
case class IntType(size: Int) extends DataType
case class DecimalType(scale: Int, precision: Int) extends DataType
case class FixedLenString(len: Int) extends DataType
case class VariableLenString(max: Int) extends DataType
case object DateType extends DataType
case object UnknownType extends DataType // temporary placeholder 
trait Node {
  val ctx: Context

  def copyWithContext(ctx: Context): Node
  def sql: String
}

case class SelectStmt(projections: Seq[SqlProj], 
                      relations: Option[Seq[SqlRelation]],
                      filter: Option[SqlExpr],
                      groupBy: Option[SqlGroupBy],
                      orderBy: Option[SqlOrderBy],
                      limit: Option[Int], ctx: Context = null) extends Node {
  def copyWithContext(c: Context) = copy(ctx = c)
  def sql =
    Seq(Some("select"), 
        Some(projections.map(_.sql).mkString(", ")),
        relations.map(x => "from " + x.map(_.sql).mkString(", ")),
        filter.map(x => "where " + x.sql),
        groupBy.map(_.sql),
        orderBy.map(_.sql),
        limit.map(x => "limit " + x.toString)).flatten.mkString(" ")
}

trait SqlProj extends Node
case class ExprProj(expr: SqlExpr, alias: Option[String], ctx: Context = null) extends SqlProj {
  def copyWithContext(c: Context) = copy(ctx = c)
  def sql = Seq(Some(expr.sql), alias).flatten.mkString(" as ")
}
case class StarProj(ctx: Context = null) extends SqlProj {
  def copyWithContext(c: Context) = copy(ctx = c)
  def sql = "*"
}

trait SqlExpr extends Node {
  def isLiteral: Boolean = false
  def getPrecomputableRelation: Option[String] = {
    if (!canGatherFields) None
    val f = gatherFields
    if (!f.filter(_.isInstanceOf[ExprColumn]).isEmpty) None

    def extractRelation(c: Column): String = c match {
      case TableColumn(_, _, relation) => relation
      case AliasedColumn(_, orig) => extractRelation(orig)
    }

    val rxs = f.map(extractRelation).toSet
    if (rxs.size == 1) Some(rxs.head) else None
  }
  def canGatherFields: Boolean
  def gatherFields: Seq[Column]
}

trait Binop extends SqlExpr {
  val lhs: SqlExpr
  val rhs: SqlExpr

  val opStr: String

  override def isLiteral = lhs.isLiteral && rhs.isLiteral
  def canGatherFields = lhs.canGatherFields && rhs.canGatherFields
  def gatherFields = lhs.gatherFields ++ rhs.gatherFields

  def copyWithChildren(lhs: SqlExpr, rhs: SqlExpr): Binop

  def sql = Seq("(" + lhs.sql + ")", opStr, "(" + rhs.sql + ")") mkString " "
}

case class Or(lhs: SqlExpr, rhs: SqlExpr, ctx: Context = null) extends Binop {
  val opStr = "or"
  def copyWithContext(c: Context) = copy(ctx = c)
  def copyWithChildren(lhs: SqlExpr, rhs: SqlExpr) = copy(lhs = lhs, rhs = rhs)
}
case class And(lhs: SqlExpr, rhs: SqlExpr, ctx: Context = null) extends Binop {
  val opStr = "and"
  def copyWithContext(c: Context) = copy(ctx = c)
  def copyWithChildren(lhs: SqlExpr, rhs: SqlExpr) = copy(lhs = lhs, rhs = rhs)
}

trait EqualityLike extends Binop
case class Eq(lhs: SqlExpr, rhs: SqlExpr, ctx: Context = null) extends EqualityLike {
  val opStr = "="
  def copyWithContext(c: Context) = copy(ctx = c)
  def copyWithChildren(lhs: SqlExpr, rhs: SqlExpr) = copy(lhs = lhs, rhs = rhs)
}
case class Neq(lhs: SqlExpr, rhs: SqlExpr, ctx: Context = null) extends EqualityLike {
  val opStr = "<>"
  def copyWithContext(c: Context) = copy(ctx = c)
  def copyWithChildren(lhs: SqlExpr, rhs: SqlExpr) = copy(lhs = lhs, rhs = rhs)
}

trait InequalityLike extends Binop
case class Ge(lhs: SqlExpr, rhs: SqlExpr, ctx: Context = null) extends InequalityLike {
  val opStr = "<="
  def copyWithContext(c: Context) = copy(ctx = c)
  def copyWithChildren(lhs: SqlExpr, rhs: SqlExpr) = copy(lhs = lhs, rhs = rhs)
}
case class Gt(lhs: SqlExpr, rhs: SqlExpr, ctx: Context = null) extends InequalityLike {
  val opStr = "<"
  def copyWithContext(c: Context) = copy(ctx = c)
  def copyWithChildren(lhs: SqlExpr, rhs: SqlExpr) = copy(lhs = lhs, rhs = rhs)
}
case class Le(lhs: SqlExpr, rhs: SqlExpr, ctx: Context = null) extends InequalityLike {
  val opStr = ">="
  def copyWithContext(c: Context) = copy(ctx = c)
  def copyWithChildren(lhs: SqlExpr, rhs: SqlExpr) = copy(lhs = lhs, rhs = rhs)
}
case class Lt(lhs: SqlExpr, rhs: SqlExpr, ctx: Context = null) extends InequalityLike {
  val opStr = ">"
  def copyWithContext(c: Context) = copy(ctx = c)
  def copyWithChildren(lhs: SqlExpr, rhs: SqlExpr) = copy(lhs = lhs, rhs = rhs)
}

case class In(elem: SqlExpr, set: Either[Seq[SqlExpr], SelectStmt], negate: Boolean, ctx: Context = null) extends SqlExpr {
  def copyWithContext(c: Context) = copy(ctx = c)
  override def isLiteral = 
    elem.isLiteral && 
    set.left.toOption.map(_.filter(e => !e.isLiteral).isEmpty).getOrElse(false)
  def canGatherFields = 
    elem.canGatherFields && 
    set.left.toOption.map(_.foldLeft(true)(_&&_.canGatherFields)).getOrElse(false)
  def gatherFields =
    elem.gatherFields ++ set.left.get.flatMap(_.gatherFields)
  def sql = Seq(elem, "in", "(", set.fold(_.map(_.sql).mkString(", "), _.sql), ")") mkString " " 
}
case class Like(lhs: SqlExpr, rhs: SqlExpr, negate: Boolean, ctx: Context = null) extends Binop {
  val opStr = if (negate) "not like" else "like"
  def copyWithContext(c: Context) = copy(ctx = c)
  def copyWithChildren(lhs: SqlExpr, rhs: SqlExpr) = copy(lhs = lhs, rhs = rhs)
}

case class Plus(lhs: SqlExpr, rhs: SqlExpr, ctx: Context = null) extends Binop {
  val opStr = "+"
  def copyWithContext(c: Context) = copy(ctx = c)
  def copyWithChildren(lhs: SqlExpr, rhs: SqlExpr) = copy(lhs = lhs, rhs = rhs)
}
case class Minus(lhs: SqlExpr, rhs: SqlExpr, ctx: Context = null) extends Binop {
  val opStr = "-"
  def copyWithContext(c: Context) = copy(ctx = c)
  def copyWithChildren(lhs: SqlExpr, rhs: SqlExpr) = copy(lhs = lhs, rhs = rhs)
}

case class Mult(lhs: SqlExpr, rhs: SqlExpr, ctx: Context = null) extends Binop {
  val opStr = "*"
  def copyWithContext(c: Context) = copy(ctx = c)
  def copyWithChildren(lhs: SqlExpr, rhs: SqlExpr) = copy(lhs = lhs, rhs = rhs)
}
case class Div(lhs: SqlExpr, rhs: SqlExpr, ctx: Context = null) extends Binop {
  val opStr = "/"
  def copyWithContext(c: Context) = copy(ctx = c)
  def copyWithChildren(lhs: SqlExpr, rhs: SqlExpr) = copy(lhs = lhs, rhs = rhs)
}

trait Unop extends SqlExpr {
  val expr: SqlExpr
  val opStr: String
  override def isLiteral = expr.isLiteral
  def canGatherFields = expr.canGatherFields 
  def gatherFields = expr.gatherFields
  def sql = Seq(opStr, "(", expr.sql, ")") mkString " "
}

case class Not(expr: SqlExpr, ctx: Context = null) extends Unop {
  val opStr = "not"
  def copyWithContext(c: Context) = copy(ctx = c)
}
case class Exists(select: SelectStmt, ctx: Context = null) extends SqlExpr {
  def copyWithContext(c: Context) = copy(ctx = c)
  def canGatherFields = false
  def gatherFields = throw new RuntimeException("not possible")
  def sql = Seq("exists", "(", select.sql, ")") mkString " "
}

case class FieldIdent(qualifier: Option[String], name: String, symbol: Column = null, ctx: Context = null) extends SqlExpr {
  def copyWithContext(c: Context) = copy(ctx = c)
  def canGatherFields = true
  def gatherFields = Seq(symbol)
  def sql = Seq(qualifier, Some(name)).flatten.mkString(".")
}
case class Subselect(subquery: SelectStmt, ctx: Context = null) extends SqlExpr {
  def copyWithContext(c: Context) = copy(ctx = c)
  def canGatherFields = false
  def gatherFields = throw new RuntimeException("not possible")
  def sql = "(" + subquery + ")"
}

trait SqlAgg extends SqlExpr {
  def canGatherFields = false
  def gatherFields = throw new RuntimeException("not possible")
}
case class CountStar(ctx: Context = null) extends SqlAgg {
  def copyWithContext(c: Context) = copy(ctx = c)
  def sql = "count(*)"
}
case class CountExpr(expr: SqlExpr, distinct: Boolean, ctx: Context = null) extends SqlAgg {
  def copyWithContext(c: Context) = copy(ctx = c)
  def sql = Seq(Some("count("), if (distinct) Some("distinct ") else None, Some(expr.sql), Some(")")).flatten.mkString("")
}
case class Sum(expr: SqlExpr, distinct: Boolean, ctx: Context = null) extends SqlAgg {
  def copyWithContext(c: Context) = copy(ctx = c)
  def sql = Seq(Some("sum("), if (distinct) Some("distinct ") else None, Some(expr.sql), Some(")")).flatten.mkString("")
}
case class Avg(expr: SqlExpr, distinct: Boolean, ctx: Context = null) extends SqlAgg {
  def copyWithContext(c: Context) = copy(ctx = c)
  def sql = Seq(Some("avg("), if (distinct) Some("distinct ") else None, Some(expr.sql), Some(")")).flatten.mkString("")
}
case class Min(expr: SqlExpr, ctx: Context = null) extends SqlAgg {
  def copyWithContext(c: Context) = copy(ctx = c)
  def sql = "min(" + expr.sql + ")"
}
case class Max(expr: SqlExpr, ctx: Context = null) extends SqlAgg {
  def copyWithContext(c: Context) = copy(ctx = c)
  def sql = "max(" + expr.sql + ")"
}

trait SqlFunction extends SqlExpr {
  val name: String
  val args: Seq[SqlExpr]
  override def isLiteral = args.foldLeft(true)(_ && _.isLiteral) 
  def canGatherFields = args.foldLeft(true)(_ && _.canGatherFields) 
  def gatherFields = args.flatMap(_.gatherFields) 
  def sql = Seq(name, "(", args.map(_.sql) mkString ", ", ")") mkString ""
}
case class FunctionCall(name: String, args: Seq[SqlExpr], ctx: Context = null) extends SqlFunction {
  def copyWithContext(c: Context) = copy(ctx = c)
}

sealed abstract trait ExtractType
case object YEAR extends ExtractType
case object MONTH extends ExtractType
case object DAY extends ExtractType

case class Extract(expr: SqlExpr, what: ExtractType, ctx: Context = null) extends SqlFunction {
  val name = "extract"
  val args = Seq(expr)
  def copyWithContext(c: Context) = copy(ctx = c)
}

case class Substring(expr: SqlExpr, from: Int, length: Option[Int], ctx: Context = null) extends SqlFunction {
  val name = "substring"
  val args = Seq(expr)
  def copyWithContext(c: Context) = copy(ctx = c)
}

case class CaseExprCase(cond: SqlExpr, expr: SqlExpr, ctx: Context = null) extends Node {
  def copyWithContext(c: Context) = copy(ctx = c)
  def sql = Seq("when", cond.sql, "then", expr.sql) mkString " "
}
case class CaseExpr(expr: SqlExpr, cases: Seq[CaseExprCase], default: Option[SqlExpr], ctx: Context = null) extends SqlExpr {
  def copyWithContext(c: Context) = copy(ctx = c)
  override def isLiteral = 
    expr.isLiteral && 
    cases.filter(x => !x.cond.isLiteral || !x.expr.isLiteral).isEmpty
  def canGatherFields =
    expr.canGatherFields &&
    cases.filter(x => !x.cond.canGatherFields || !x.expr.canGatherFields).isEmpty
  def gatherFields =
    expr.gatherFields ++
    cases.flatMap(x => x.cond.gatherFields ++ x.expr.gatherFields)
  def sql = Seq(Some("case"), Some(expr.sql), Some(cases.map(_.sql) mkString " "), default.map(_.sql), Some("end")).flatten.mkString(" ")
}
case class CaseWhenExpr(cases: Seq[CaseExprCase], default: Option[SqlExpr], ctx: Context = null) extends SqlExpr {
  def copyWithContext(c: Context) = copy(ctx = c)
  override def isLiteral = 
    cases.filter(x => !x.cond.isLiteral || !x.expr.isLiteral).isEmpty
  def canGatherFields =
    cases.filter(x => !x.cond.canGatherFields || !x.expr.canGatherFields).isEmpty
  def gatherFields =
    cases.flatMap(x => x.cond.gatherFields ++ x.expr.gatherFields)
  def sql = Seq(Some("case"), Some(cases.map(_.sql) mkString " "), default.map(_.sql), Some("end")).flatten.mkString(" ")
}

case class UnaryPlus(expr: SqlExpr, ctx: Context = null) extends Unop {
  val opStr = "+"
  def copyWithContext(c: Context) = copy(ctx = c)
}
case class UnaryMinus(expr: SqlExpr, ctx: Context = null) extends Unop {
  val opStr = "-"
  def copyWithContext(c: Context) = copy(ctx = c)
}

trait LiteralExpr extends SqlExpr {
  override def isLiteral = true
  def canGatherFields = true 
  def gatherFields = Seq.empty
}
case class IntLiteral(v: Long, ctx: Context = null) extends LiteralExpr {
  def copyWithContext(c: Context) = copy(ctx = c)
  def sql = v.toString
}
case class FloatLiteral(v: Double, ctx: Context = null) extends LiteralExpr {
  def copyWithContext(c: Context) = copy(ctx = c)
  def sql = v.toString
}
case class StringLiteral(v: String, ctx: Context = null) extends LiteralExpr {
  def copyWithContext(c: Context) = copy(ctx = c)
  def sql = "\"" + v.toString + "\"" // TODO: escape...
}
case class NullLiteral(ctx: Context = null) extends LiteralExpr {
  def copyWithContext(c: Context) = copy(ctx = c)
  def sql = "null"
}
case class DateLiteral(d: String, ctx: Context = null) extends LiteralExpr {
  def copyWithContext(c: Context) = copy(ctx = c)
  def sql = Seq("date", "\"" + d + "\"") mkString " "
}
case class IntervalLiteral(e: String, unit: ExtractType, ctx: Context = null) extends LiteralExpr {
  def copyWithContext(c: Context) = copy(ctx = c)
  def sql = Seq("interval", "\"" + e + "\"", unit.toString) mkString " " 
}

trait SqlRelation extends Node
case class TableRelationAST(name: String, alias: Option[String], ctx: Context = null) extends SqlRelation {
  def copyWithContext(c: Context) = copy(ctx = c)
  def sql = Seq(Some(name), alias).flatten.mkString(" ")
}
case class SubqueryRelationAST(subquery: SelectStmt, alias: String, ctx: Context = null) extends SqlRelation {
  def copyWithContext(c: Context) = copy(ctx = c)
  def sql = Seq("(", subquery.sql, ")", alias) mkString " "
}

sealed abstract trait JoinType
case class LeftJoin(outer: Boolean) extends JoinType
case class RightJoin(outer: Boolean) extends JoinType
case object InnerJoin extends JoinType

case class JoinRelation(left: SqlRelation, right: SqlRelation, tpe: JoinType, clause: SqlExpr, ctx: Context = null) extends SqlRelation {
  def copyWithContext(c: Context) = copy(ctx = c)
  def sql = Seq(left.sql, "join", right.sql, "on", clause.sql) mkString " "
}

sealed abstract trait OrderType
case object ASC extends OrderType
case object DESC extends OrderType

case class SqlGroupBy(keys: Seq[String], having: Option[SqlExpr], ctx: Context = null) extends Node {
  def copyWithContext(c: Context) = copy(ctx = c)
  def sql = Seq(Some("group by"), Some(keys mkString ", "), having.map(e => "having " + e.sql)).flatten.mkString(" ")
}
case class SqlOrderBy(keys: Seq[(FieldIdent, OrderType)], ctx: Context = null) extends Node {
  def copyWithContext(c: Context) = copy(ctx = c)
  def sql = Seq("order by", keys map (x => x._1.sql + " " + x._2.toString) mkString ", ") mkString " "
}

package ru.circumflex.orm

import org.slf4j.LoggerFactory
import ORM._
import ru.circumflex.core.WrapperModel

// ## Common interfaces

/**
 * Simple interface for objects capable to render themselves into SQL statements.
 */
trait SQLable {
  def toSql: String
  override def toString = toSql
}

/**
 * Simple interface for expressions with JDBC-style parameters
 */
trait ParameterizedExpression extends SQLable {
  /**
   * The parameters associated with this expression. The order is important.
   */
  def parameters: Seq[Any]

  /**
   * Render this query by replacing parameter placeholders with actual values.
   */
  def toInlineSql: String = parameters.foldLeft(toSql)((sql, p) =>
    sql.replaceFirst("\\?", typeConverter.toString(p)))

}

/**
 * Simple interface for database objects capable to render themselves into DDL
 * CREATE and DROP statements.
 */
trait SchemaObject {
  /**
   * SQL statement to create this database object.
   */
  def sqlCreate: String

  /**
   * SQL statement to drop this database object.
   */
  def sqlDrop: String

  /**
   * SQL object name. It is used to uniquely identify this object
   * during schema creation by `DDLExport` to avoid duplicates.
   * Object names are case-insensitive (e.g. `MY_TABLE` and `my_table` are
   * considered equal).
   */
  def objectName: String

  override def hashCode = objectName.toLowerCase.hashCode

  override def equals(obj: Any) = obj match {
    case so: SchemaObject => so.objectName.equalsIgnoreCase(this.objectName)
    case _ => false
  }

  override def toString = objectName
}

/**
 * *Value holder* is designed to be an extensible atomic data carrier unit
 * of record. It is subclassed by 'Field' and 'Association'.
 */
trait ValueHolder[R <: Record[R], T]
    extends WrapperModel {

  // An internally stored value.
  protected var _value: T = _

  /**
   * An enclosing record.
   */
  def record: R

  /**
   * A name by of this value holder.
   */
  def name: String

  // This way the value will be unwrapped by FTL engine.
  def item = getValue

  // Accessors and mutators.

  def getValue(): T = _value
  def apply(): T = getValue

  def setValue(newValue: T): this.type = {
    _value = newValue
    return this
  }
  def :=(newValue: T): this.type = setValue(newValue)
  def update(newValue: T): this.type = setValue(newValue)

  // Equality methods.

  override def equals(that: Any) = that match {
    case vh: ValueHolder[R, T] => vh.getValue == this.getValue
    case _ => false
  }

  override def hashCode = if (this.getValue == null) 0 else this.getValue.hashCode

  /**
   * We cannot use `equals` and `hashCode` to test value holders for equality,
   * because this methods check the underlying value. Instead, we use this method
   * to differentiate between value holders.
   */
  def sameAs(that: Any) = that match {
    case vh: ValueHolder[R, T] =>
      vh.record.relation == this.record.relation &&
        vh.name == this.name
    case _ => false
  }

  /**
   * Return a `String` representation of internal value.
   */
  def toString(default: String) = if (getValue == null) default else getValue.toString

  override def toString = toString("")
}

// ## JDBC utilities

/**
 * Helper constructions that automatically close such JDBC objects as
 * `ResultSet`s and `PreparedStatement`s.
 */
object JDBC {
  protected val sqlLog = LoggerFactory.getLogger("ru.circumflex.orm")

  def autoClose[A <: {def close(): Unit}, B](obj: A)
                                            (actions: A => B)
                                            (errors: Throwable => B): B =
    try {
      return actions(obj)
    } catch {
      case e => return errors(e)
    } finally {
      obj.close
    }

  def auto[A <: {def close(): Unit}, B](obj: A)
                                       (actions: A => B): B =
    autoClose(obj)(actions)(throw _)
}

// ## Exceptions

/**
 * The most generic exception class. 
 */
class ORMException(msg: String, cause: Throwable) extends Exception(msg, cause) {
  def this(msg: String) = this(msg, null)
  def this(cause: Throwable) = this(null, cause)
}
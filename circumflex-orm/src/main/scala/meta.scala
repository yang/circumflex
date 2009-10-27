package ru.circumflex.orm

/**
 * Provides base functionality for generating domain model schema,
 * as well as validating, quering, inserting, deleting and updating
 * domain model objects (a.k.a. <code>records</code>).
 * In general there should be only one table instance per record class
 * (a singleton object, or, more conveniantly, the companion object).
 */
abstract class Table(val schema: String,
                     val name: String) {
  /**
   * Configuration object is used for all persistence-related stuff.
   * Override it if you want to use your own configuration implementation.
   * @return DefaultConfiguration by default
   */
  def configuration: Configuration = DefaultConfiguration

}

abstract class Column(val table: Table,
                      val name: String,
                      val sqlType: String,
                      val nullable: Boolean,
                      val misc: String) {
  
}



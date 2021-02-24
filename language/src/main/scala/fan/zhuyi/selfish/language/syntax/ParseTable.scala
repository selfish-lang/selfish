package fan.zhuyi.selfish.language.syntax

import com.oracle.truffle.api.nodes.Node
import fan.zhuyi.selfish.language.syntax.Parser.{GeneralResult, ParseResult}

import scala.collection.mutable


class ParseTable {

  trait ParseKey

  case class ParseClass[T](offset: Int) extends ParseKey;


  private val map: mutable.HashMap[ParseKey, GeneralResult] = mutable.HashMap.empty;

  def find[T <: Node](offset: Int): Option[ParseResult[T]] = map.get(ParseClass[T](offset))
    .map(_.left.map(_.asInstanceOf[T]))

  def cache[T <: Node](offset: Int, tree: ParseResult[T]): Unit = map.put(ParseClass[T](offset), tree)

}
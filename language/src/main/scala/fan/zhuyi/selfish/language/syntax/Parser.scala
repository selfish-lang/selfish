package fan.zhuyi.selfish.language.syntax

import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.source.{Source, SourceSection}
import fan.zhuyi.selfish.language.node._
import fan.zhuyi.selfish.language.syntax.Parser._

import java.awt.event.KeyEvent
import java.nio.charset.{Charset, CharsetEncoder}
import scala.collection.mutable
import scala.util.control.Breaks.{break, breakable}

object Parser {
  type GeneralResult = Either[Node, Error];
  type ParseResult[T <: Node] = Either[T, Error];
  type SubParseResult[T] = Either[T, Error];

  val ASCII: CharsetEncoder = Charset.forName("US-ASCII").newEncoder();

  final def isPrintableChar(c: Int): Boolean = {
    val block = Character.UnicodeBlock.of(c)
    (!Character.isISOControl(c)) &&
      c != KeyEvent.CHAR_UNDEFINED &&
      block != null &&
      (block != Character.UnicodeBlock.SPECIALS)
  }

  sealed trait CharInfo;

  final case object Tilde extends CharInfo;

  final case object Wildcard extends CharInfo;

  final case class Valid(x: Int) extends CharInfo;

  final case object Invalid extends CharInfo;

  final case object EndingHint extends CharInfo;

  final def checkCodePoint(c: Int): CharInfo = {
    c match {
      case '~' => Tilde
      case '*' => Wildcard
      case '!' | '%' | '@' | '+' | '-' | ',' | '/' | '\\' | '_' => Valid(c)
      case c if (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') => Valid(c)
      case c if Character.charCount(c) == 1 && ASCII.canEncode(Character.toChars(c)) => Invalid
      case c if isPrintableChar(c) => Valid(c)
    }
  }

}

class Parser(source: Source) {
  var offset: Int = 0
  var sequence: CharSequence = _
  val table: ParseTable = new ParseTable()

  private[this] def createParseError(message: String): Error = {
    new Error(s"[syntax error] ${source.getName}:${this.source.getLineNumber(offset)}:${this.source.getColumnNumber(offset)}: ${message}")
  }

  private[this] def withContext[R <: Node](action: => Either[R, Error]): ParseResult[R] = {
    val beforeOffset = this.offset;
    eatWhiteSpace()
    val afterOffset = this.offset;
    this.table.find[R](offset) match {
      case Some(Left(node)) =>
        this.offset = node.getSourceSection.getCharEndIndex
        Left(node)
      case Some(right) =>
        right
      case None =>
        val result = action match {
          case Right(error) =>
            this.offset = beforeOffset
            Right(error)
          case x => x
        }
        table.cache(afterOffset, result)
        result
    }

  }

  def topChar: Option[Char] = {
    if (sequence == null) {
      sequence = source.getCharacters
    }
    if (offset >= sequence.length()) {
      return None
    }
    Some(sequence.charAt(offset))
  }


  final case class CachedCodePoint(offset: Int, value: Option[Int]);
  var codePointCache: CachedCodePoint = _;

  def topCodePoint: Option[Int] = {
    if (codePointCache != null && codePointCache.offset == offset) {
      return codePointCache.value;
    }
    val result: Option[Int] = topChar match {
      case None => None
      case Some(c) if c.isHighSurrogate && offset + 1 < sequence.length() =>
        sequence.charAt(offset + 1) match {
          case d if d.isLowSurrogate => Some(Character.toCodePoint(c, d))
          case _ => Some(c)
        }
      case Some(c) => Some(c)
    }
    codePointCache = CachedCodePoint(offset, result)
    result
  }

  def moveNextCP(): Unit = {
    offset += topCodePoint.map(Character.charCount).getOrElse(0);
  }

  def moveNext(): Unit = {
    offset += 1
  }

  def moveNextLine(): Unit = {
    if (offset < sequence.length()) {
      val line = source.getLineNumber(offset)
      offset = source.getLineStartOffset(line) + source.getLineLength(line)
    }
  }


  def eatWhiteSpace(): Unit = {
    while (topChar.exists(_.isWhitespace) || topChar.contains('#')) {
      if (topChar.contains('#')) {
        moveNextLine();
      }
      moveNext()
    }
  }

  def parseBareword: ParseResult[BarewordNode] = {
    val start = offset;
    val builder = new StringBuilder
    var codepoint = topCodePoint.map(checkCodePoint)
    while (codepoint match {
      case None => false
      case Some(Valid(x)) => builder.addAll(Character.toString(x)); true
      case Some(Wildcard) => builder.addOne('*'); true
      case Some(Tilde) if builder.isEmpty => builder.addOne('~'); true
      case Some(EndingHint) => false
      case _ =>
        return Right(createParseError(s"invalid bareword character: ${Character.toString(topCodePoint.get)}"))
    }) {
      moveNextCP()
      codepoint = topCodePoint.map(checkCodePoint)
    }
    if (builder.isEmpty) {
      Right(createParseError("empty bareword"))
    } else {
      Left(BarewordNodeGen.create(source.createSection(start, this.offset - start), builder.toString()))
    }
  }

  private[this] def parseSingleQuoted: SubParseResult[StringLiteralNode] = {
    val start = this.offset - 1
    var foundQuote = false;
    var currentChar = topChar;
    val builder = new StringBuilder
    while (currentChar.isDefined && (!foundQuote || currentChar.contains('\''))) {
      if (currentChar.contains('\'')) {
        if (foundQuote) {
          builder.addOne('\'')
          foundQuote = false
        } else {
          foundQuote = true;
        }
      } else {
        builder.addOne(currentChar.get)
      }
      moveNext()
      currentChar = topChar
    }
    if (!foundQuote) Right(createParseError("single quoted string is not closed"))
    else Left(new StringLiteralNode(source.createSection(start, this.offset - start), builder.toString))
  }

  private sealed trait EscapeMode;

  private final case object NoEscape extends EscapeMode;

  private final case object EscapeStart extends EscapeMode;

  private final case class EscapeOct(count: Int, value: Int) extends EscapeMode;

  private final case class EscapeHex(count: Int, value: Int) extends EscapeMode;

  private final case class EscapeSmall(count: Int, value: Int) extends EscapeMode;

  private final case class EscapeLarge(count: Int, value: Int) extends EscapeMode;

  private sealed trait StringState {
    def builder: mutable.StringBuilder

    def submitNode(section: SourceSection): StringState = {
      val lit = builder.toString()
      builder.clear()
      this match {
        case PureLiteral(builder) =>
          val buffer = mutable.ArrayBuffer.empty[ExpressionNode];
          if (lit.nonEmpty) {
            buffer += new StringLiteralNode(section, lit)
          }
          Interpolation(buffer, builder)
        case Interpolation(seq, _) =>
          seq += new StringLiteralNode(section, lit)
          this
      }
    }

    def finish(section: SourceSection): StringNode = {
      this match {
        case PureLiteral(builder) =>
          new StringLiteralNode(section, builder.toString())
        case Interpolation(seq, builder) =>
          if (!builder.isEmpty) {
            seq += new StringLiteralNode(section, builder.toString())
          }
          new StringInterpolationNode(section, seq.toArray)
      }
    }

    def addExpr(expr: ExpressionNode): Unit = {
      this match {
        case PureLiteral(_) => ()
        case Interpolation(seq, _) =>
          seq += expr
      }
    }

  }

  private final case class PureLiteral(builder: StringBuilder) extends StringState

  private final case class Interpolation(seq: mutable.ArrayBuffer[ExpressionNode], builder: StringBuilder) extends StringState

  private def testHex(char: Char): Int = {
    if (char - '0' >= 0 && char - '0' <= 9) return char - '0';
    if (char - 'a' >= 0 && char - 'a' <= 5) return char - 'a' + 10;
    if (char - 'A' >= 0 && char - 'A' <= 5) return char - 'A' + 10;
    -1
  }

  private[this] def parseParenExpression: ParseResult[ExpressionNode] = withContext {
    var currentChar = topChar;
    var level = 1;
    if (!currentChar.contains('(')) {
      return Right(createParseError(s"expected left parenthesis for expression"))
    }
    moveNext();
    currentChar = topChar;
    while (level != 0 && currentChar.isDefined) {
      // this is of cuz not correct, just for current test
      currentChar.get match {
        case '(' => level += 1
        case ')' => level -= 1
        case _ => ()
      }
      moveNext();
      currentChar = topChar;
    }
    if (level == 0) {
      Left(null)
    } else {
      Right(createParseError("expect right parenthesis for expression"))
    }
  }

  private[this] def parseStringWith(stringType: String, closeCond: Int): SubParseResult[StringNode] = {
    var escaping: EscapeMode = NoEscape;
    var foundQuotes = 0
    var state: StringState = PureLiteral(new StringBuilder)
    var currentChar = topChar;
    var noMove = false;
    val start = this.offset - closeCond;
    while (currentChar.isDefined && foundQuotes != closeCond) {
      breakable {
        if (escaping == NoEscape && currentChar.contains('"')) {
          foundQuotes += 1;
          break;
        } else {
          while (foundQuotes > 0) {
            state.builder.addOne('"');
            foundQuotes -= 1;
          }
        }
        escaping match {
          case NoEscape =>
            if (currentChar.contains('\\')) {
              escaping = EscapeStart
            } else if (currentChar.contains('$')) {
              moveNext()
              assert(topChar.contains('('))
              state = state.submitNode(source.createSection(start, this.offset - start));
              parseParenExpression match {
                case Left(tree) => state.addExpr(tree)
                case Right(error) => return Right(error)
              }
              currentChar = topChar
              noMove = true
            } else {
              state.builder.addOne(currentChar.get);
            }
          case EscapeStart =>
            currentChar.get match {
              case 'a' =>
                state.builder.addOne('\u0007')
              case 'b' =>
                state.builder.addOne('\u0010')
              case 'f' =>
                state.builder.addOne('\u000c')
              case 'n' =>
                state.builder.addOne('\u000a')
              case 'r' =>
                state.builder.addOne('\u000d')
              case 't' =>
                state.builder.addOne('\u0009')
              case 'v' =>
                state.builder.addOne('\u000b')
              case '\\' =>
                state.builder.addOne('\\')
              case '"' =>
                state.builder.addOne('"')
              case '$' =>
                state.builder.addOne('$')
              case 'x' =>
                escaping = EscapeHex(0, 0)
                break
              case 'u' =>
                escaping = EscapeSmall(0, 0)
                break
              case 'U' =>
                escaping = EscapeLarge(0, 0)
                break
              case digit if digit.isDigit && digit < '8' =>
                escaping = EscapeOct(1, digit.asDigit)
              case unknown =>
                return Right(createParseError(s"unknown escape character: '${unknown}'"))
            }
            escaping = NoEscape
          case EscapeOct(3, x) =>
            noMove = true;
            state.builder.addAll(Character.toString(x));
            escaping = NoEscape
          case EscapeOct(i, x) =>
            currentChar.get match {
              case digit if digit.isDigit && digit < '8' =>
                escaping = EscapeOct(i + 1, x * 8 + digit.asDigit)
              case invalid =>
                return Right(createParseError(s"expect 3 octal digits, found: ${invalid}"))
            }
          case EscapeHex(2, x) =>
            noMove = true;
            state.builder.addAll(Character.toString(x));
            escaping = NoEscape
          case EscapeHex(i, x) =>
            testHex(currentChar.get) match {
              case -1 =>
                return Right(createParseError(s"expect 2 hex digits, found: ${currentChar.get}"))
              case y =>
                escaping = EscapeHex(i + 1, x * 16 + y)
            }
          case EscapeSmall(4, x) =>
            noMove = true;
            state.builder.addAll(Character.toString(x));
            escaping = NoEscape
          case EscapeSmall(i, x) =>
            testHex(currentChar.get) match {
              case -1 =>
                return Right(createParseError(s"expect 4 hex digits, found: ${currentChar.get}"))
              case y =>
                escaping = EscapeSmall(i + 1, x * 16 + y)
            }
          case EscapeLarge(8, x) =>
            noMove = true;
            state.builder.addAll(Character.toString(x));
            escaping = NoEscape
          case EscapeLarge(i, x) =>
            testHex(currentChar.get) match {
              case -1 =>
                return Right(createParseError(s"expect 8 hex digits, found: ${currentChar.get}"))
              case y =>
                escaping = EscapeLarge(i + 1, x * 16 + y)
            }
        }
      }
      if (!noMove) {
        moveNext()
        currentChar = topChar;
      }
      noMove = false;
    }
    if (foundQuotes == closeCond) {
      Left(state.finish(source.createSection(start, this.offset - start)))
    } else {
      Right(createParseError(s"${stringType} is not closed"))
    }
  }

  def parseString: ParseResult[StringNode] = withContext {
    var count = 0;
    if (topChar.contains('\'')) {
      moveNext()
      parseSingleQuoted
    } else {
      while (topChar.contains('"')) {
        count = count + 1;
        moveNext()
      }
      count match {
        case 2 => Left(new StringLiteralNode(source.createSection(offset - 2, 2), ""))
        case 6 => Left(new StringLiteralNode(source.createSection(offset - 6, 6), ""))
        case 3 => parseStringWith("heredoc", 3)
        case 1 => parseStringWith("double quoted string", 1)
        case _ => Right(createParseError("expected double quoted string, single quoted string or heredoc"))
      }
    }
  }
}



package com.sohoffice.doc.extract

import scala.collection.Seq
import scala.tools.nsc.doc
import scala.tools.nsc.doc.Universe
import scala.tools.nsc.doc.base.comment._
import scala.tools.nsc.doc.model._

trait DocExtractOutput {

  import DocExtractOutput._

  def generate(tpl: DocTemplateEntity)(implicit universe: doc.Universe): Seq[String] =
    generate(tpl, new JavaResourceFileFormatter)

  def generate(tpl: DocTemplateEntity, collector: Formatter)(implicit universe: doc.Universe): Seq[String] = {
    implicit val impCollector: Formatter = collector
    generateForType(tpl) ++ generateForValues(tpl) ++ generateForMembers(tpl) ++ generateForClassParameters(tpl)
  }

  protected def generateForType(tpl: DocTemplateEntity)(implicit collector: Formatter): Seq[String] = {
    // we don't output object here. It will be covered by members, either as members of package or another object.
    if (!tpl.isObject && acceptType(tpl)) {
      entry(tpl).toSeq
    } else {
      Nil
    }
  }

  protected def generateForMembers(tpl: DocTemplateEntity)(implicit collector: Formatter, universe: Universe): Seq[String] = {
    tpl.members.filter(m => acceptMember(tpl, m)).flatMap(m =>
      if (m.isType) { // type will be traversed later, we should skip it now
        Nil
      } else {
        entry(tpl, m).toSeq
      })
  }

  protected def generateForValues(tpl: DocTemplateEntity)(implicit collector: Formatter, universe: Universe): Seq[String] = {
    tpl.values.filter(v => acceptVal(tpl, v)).flatMap { v =>
      entry(tpl, v).toSeq
    }
  }

  protected def generateForClassParameters(tpl: DocTemplateEntity)(implicit collector: Formatter, universe: doc.Universe): Seq[String] = {
    if (tpl.isClass) {
      tpl.valueParams.flatten.filter(v => acceptParam(tpl, v)).flatMap { v =>
        entry(tpl, v)
      }
    } else {
      Nil
    }
  }

  def acceptType(tpl: DocTemplateEntity): Boolean = true

  def acceptMember(tpl: DocTemplateEntity, member: MemberEntity): Boolean = true

  def acceptParam(tpl: DocTemplateEntity, pe: ParameterEntity): Boolean = true

  /**
    * I believe values are duplicated with members, so we don't really need it.
    */
  def acceptVal(tpl: DocTemplateEntity, v: Val): Boolean = false

  /**
    * Extract document of a type
    *
    * @param tpl
    * @param collector
    * @return
    */
  def entry(tpl: DocTemplateEntity)(implicit collector: Formatter): Option[String] = {
    val typeComment = commentToText(tpl.comment).trim
    if (typeComment.nonEmpty && acceptType(tpl)) {
      Some(collector.collect(tpl.toString(), null, typeComment))
    } else {
      None
    }
  }

  /**
    * Extract document of a type member
    *
    * @param tpl
    * @param member
    * @param collector
    * @return
    */
  def entry(tpl: DocTemplateEntity, member: MemberEntity)(implicit collector: Formatter): Option[String] = {
    val text = commentToText(member.comment)
    if (text.nonEmpty) {
      val memberName = if (member.isDef) {
        member.signatureCompat.takeWhile(ch => ch != ':')
      } else {
        member.name
      }
      Some(collector.collect(tpl.toString(), memberName, text))
    } else {
      None
    }
  }

  /**
    * Extract document of a class parameter or case class value
    *
    * @param tpl
    * @param v
    * @param collector
    * @return
    */
  def entry(tpl: DocTemplateEntity, v: ParameterEntity)(implicit collector: Formatter): Option[String] = {
    val opt = tpl.comment flatMap { comment =>
      v match {
        case vp: ValueParam =>
          comment.valueParams.get(vp.name)
        case tp: TypeParam =>
          comment.typeParams.get(tp.name)
      }
    }
    opt flatMap { body =>
      val text = DocExtractOutput.bodyToText(body)
      if (text != null && text.nonEmpty) {
        Some(collector.collect(tpl.toString(), v.name, text))
      } else {
        None
      }
    }
  }

  def entry(tpl: DocTemplateEntity, v: Val)(implicit collector: Formatter): Option[String] = {
    val text = commentToText(v.comment)
    if (text != null && text.nonEmpty) {
      Some(collector.collect(tpl.toString(), v.name, text))
    } else {
      None
    }
  }
}

object DocExtractOutput {

  /**
    * A default implementation that outputs everything
    */
  lazy val ALL = new DocExtractOutput {}

  /**
    * Format the documentable element into a string
    * The result will be passed to Writer to serialize
    */
  trait Formatter {
    /**
      * Do collect
      */
    def collect(className: String, element: String, comment: String): String
  }

  class JavaResourceFileFormatter extends Formatter {
    override def collect(className: String, element: String, comment: String): String = {
      val commentPart = comment.lines.map(_.trim).mkString("\\n\\\n")
      if (element == null || element.isEmpty) {
        s"$className = $commentPart"
      } else {
        s"$className.$element = $commentPart"
      }
    }
  }

  def bodyToText(body: Body): String = {
    def summaryInBlock(block: Block): Seq[String] = block match {
      case Title(text, _) => summaryInInline(text)
      case Paragraph(text) => summaryInInline(text)
      case UnorderedList(items) => items flatMap summaryInBlock
      case OrderedList(items, _) => items flatMap summaryInBlock
      case DefinitionList(items) => items.values.toSeq flatMap summaryInBlock
      case _ => Nil
    }

    def summaryInInline(inline: Inline): Seq[String] = inline match {
      case Summary(text) => summaryInInline(text)
      case Chain(items) => items flatMap summaryInInline
      case Italic(text) => summaryInInline(text)
      case Bold(text) => summaryInInline(text)
      case Underline(text) => summaryInInline(text)
      case Superscript(text) => summaryInInline(text)
      case Subscript(text) => summaryInInline(text)
      case Link(_, title) => summaryInInline(title)
      case Text(text) => Seq(text)
      case _ => Nil
    }

    val blockTexts = body.blocks map {
      summaryInBlock
    } map {
      _.mkString(" ")
    }

    blockTexts.mkString("\n").trim
  }

  def commentToText(cmt: Option[Comment]) = {
    cmt.map(x => DocExtractOutput.bodyToText(x.body)).getOrElse("")
  }

}

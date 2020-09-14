package es.weso.rdf.operations

import cats.syntax.show._
import es.weso.rdf.jena.RDFAsJenaModel
import es.weso.rdf.nodes._
import es.weso.rdf.triples.RDFTriple
import org.scalatest._
import org.scalatest.matchers.should._
import org.scalatest.funspec._
import es.weso.utils.IOUtils._
import cats.effect.IO
import es.weso.rdf.RDFReader
// import org.scalatest.matchers.should.Matchers._
import es.weso.utils.internal.CollectionCompat._

class GraphTest extends AnyFunSpec with Matchers with EitherValues {

  def ex: IRI = IRI("http://example.org/")
  def iri(s: String): IRI = ex + s
  def bnode(s: String): BNode = BNode(s)
  def int(n: Int): IntegerLiteral = IntegerLiteral(n,n.toString)

  describe("Graph Traverse") {

    shouldTraverse(
      iri("x"),
      """|prefix : <http://example.org/>
        |:x :p :y .
      """.stripMargin,
      LazyList(iri("x"), iri("y"))
    )
    shouldTraverse(
      iri("x"),
      """|prefix : <http://example.org/>
         |:x :p :x .
      """.stripMargin,
      LazyList(iri("x"))
    )
    shouldTraverse(
      iri("x"),
      """|prefix : <http://example.org/>
         |:x :p :x, :y .
         |:y :p :z, :x .
      """.stripMargin,
      LazyList(iri("x"), iri("y"), iri("z"))
    )
    shouldTraverse(
      iri("x"),
      """|prefix : <http://example.org/>
         |:x :p :x, :y .
         |:y :p :z, :x .
         |:r :q :x .
      """.stripMargin,
      LazyList(iri("x"), iri("y"), iri("z"))
    )
    shouldTraverse(
      iri("x"),
      """|prefix : <http://example.org/>
         |:x :p :x, :y ;
         |   :q :t .
         |:y :p :z, :x .
         |:r :q :x .
      """.stripMargin,
      LazyList(iri("x"), iri("y"), iri("z"), iri("t"))
    )

    shouldTraverse(
      iri("x"),
      """|prefix : <http://example.org/>
         |:x :p _:1, _:2 ;
         |   :q 1 .
         |_:1 :p :y, :z .
         |:r :q :x .
      """.stripMargin,
      LazyList(
        iri("x"), iri("y"), iri("z"),
        bnode("1"), bnode("2"),
        int(1)
      ),
      true
    ) 

    def shouldTraverse(node: RDFNode,
                       str: String,
                       expected: LazyList[RDFNode],
                       withLog: Boolean = false): Unit = {
      it(s"shouldTraverse(${node.show} in graph\n${str}\n and return\n$expected") {
        val r = for {
          rdf <- RDFAsJenaModel.fromChars(str, "TURTLE", None)
          _ <- showLog(rdf,"RDF", withLog)
          traversed <- stream2io(Graph.traverse(node, rdf))
          _ <- IO { pprint.log(traversed.toList)}
        } yield traversed
        r.attempt.unsafeRunSync.fold(
          e => fail(s"Error: $e"),
          t => {
          t.toList should contain theSameElementsAs expected.toList
        })
      }
    }
  } 

  describe("Graph Traverse") {
    shouldTraverseWithArcs(
      iri("x"),
      """|prefix : <http://example.org/>
         |:x :p :x, :y .
         |:z :p :x, :y .
      """.stripMargin,
      List(
        RDFTriple(iri("x"), iri("p"),iri("y")),
        RDFTriple(iri("x"), iri("p"),iri("x"))
      )
    )

    describe("Graph Traverse") {
      shouldTraverseWithArcs(
        iri("x"),
        """|prefix : <http://example.org/>
           |:x :p :x, :y .
           |:y :q :r .
           |:z :p :x, :y .
      """.stripMargin,
        List(
          RDFTriple(iri("x"), iri("p"), iri("y")),
          RDFTriple(iri("x"), iri("p"), iri("x")),
          RDFTriple(iri("y"), iri("q"), iri("r"))
        )
      )
    }


    def shouldTraverseWithArcs(node: RDFNode,
                               str: String,
                               expected: List[RDFTriple]): Unit = {
      it(s"shouldTraverseWithArcs(${node.show} in graph ${str}) and return $expected") {
        val r = for {
          rdf <- RDFAsJenaModel.fromChars(str, "TURTLE", None)
          triples <- stream2io(Graph.traverseWithArcs(node,rdf))
        } yield triples
        r.attempt.unsafeRunSync.fold(
          e => fail(s"Error: $e"),
          triples => triples.toList should contain theSameElementsAs expected
        )
      }
    } 
  }

  def showLog(rdf: RDFReader, tag: String, withLog: Boolean): IO[Unit] = if (withLog)
    for {
      str <- rdf.serialize("N-TRIPLES")
      _ <- IO { pprint.log(str,tag) }
    } yield ()
  else IO(())

}
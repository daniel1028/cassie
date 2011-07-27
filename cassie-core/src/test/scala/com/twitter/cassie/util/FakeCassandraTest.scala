package com.twitter.cassie.tests.util

import org.scalatest.matchers.MustMatchers
import org.mockito.Mockito.when
import org.scalatest._
import java.net.{SocketAddress, InetSocketAddress}
import org.apache.cassandra.finagle.thrift

import com.twitter.cassie._
import com.twitter.cassie.codecs._
import com.twitter.logging.Logger
import scala.collection.JavaConversions._
import com.twitter.conversions.time._
import scala.collection.mutable.ListBuffer
import com.twitter.cassie.connection.CCluster

class FakeCassandraTest extends Spec with MustMatchers with BeforeAndAfterAll with BeforeAndAfterEach {
  val port = 1359
  def factory() = new FakeCassandra(port)
  var server: FakeCassandra = null
  var client: Cluster = null
  var connections = new ListBuffer[Keyspace]

  implicit val stringCodec = Utf8Codec

  override protected def beforeAll() {
    server = factory()
    server.start()
    Thread.sleep(100)
    client = new Cluster(Set("localhost"), port)
  }

  def keyspace() = {
    val keyspace = client.mapHostsEvery(0.seconds).keyspace("foo").connect()
    connections.append(keyspace)
    keyspace
  }

  override protected def afterAll() {
    server.stop()
    connections.map(_.close)
  }

  override protected def beforeEach() {
    server.reset()
  }

  describe("a fake cassandra") {
    it("should be able to connect to an arbitrary columnfamily and read and write") {
      val cf = keyspace().columnFamily[String, String, String]("bar", Utf8Codec, Utf8Codec, Utf8Codec)
      cf.insert("k", Column("b", "c")).get()
      cf.getRow("k").get().size must equal(1)
      cf.getColumn("k", "b").get().size must equal(1)
    }

    it("should be able to batch mutate") {
      val cf = keyspace().columnFamily[String, String, String]("bar", Utf8Codec,Utf8Codec, Utf8Codec)
      var batch = cf.batch
      batch.insert("k", Column("b", "c"))
      batch.insert("k", Column("d", "e"))
      batch.execute().get()
      cf.getRow("k").get().size() must equal(2)

      batch = cf.batch
      batch.removeColumn("k", "b")
      batch.removeColumn("k", "d")
      batch.execute().get()
      cf.getRow("k").get().size() must equal(0)
    }

    it("should handle counters") {
      var cf = keyspace().counterColumnFamily("counters", Utf8Codec, Utf8Codec)
      cf.add("1", CounterColumn("2", 3))()
      cf.getColumn("1", "2")() must equal(Some(CounterColumn("2", 3)))
      cf.add("1", CounterColumn("2", 4))()
      cf.getColumn("1", "2")() must equal(Some(CounterColumn("2", 7)))
    }
  }
}

package com.twitter.finagle.service

import com.twitter.conversions.time._
import com.twitter.finagle.util.Rng
import org.junit.runner.RunWith
import org.scalacheck.Gen
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.scalatest.prop.GeneratorDrivenPropertyChecks

@RunWith(classOf[JUnitRunner])
class BackoffTest extends FunSuite
  with GeneratorDrivenPropertyChecks
{
  test("exponential") {
    val backoffs = Backoff.exponential(1.seconds, 2) take 10
    assert(backoffs.force.toSeq === (0 until 10 map { i => (1 << i).seconds }))
  }

  test("exponential with upper limit") {
    val backoffs = (Backoff.exponential(1.seconds, 2) take 5) ++ Backoff.const(32.seconds)
    assert((backoffs take 10).force.toSeq === (0 until 10 map {
      i => math.min(1 << i, 32).seconds
    }))
  }

  test("exponential with maximum") {
    val backoffs = Backoff.exponential(1.millis, 2, 5.millis).take(5)
    assert(backoffs.force.toSeq.map(_.inMillis) == Seq(1, 2, 4, 5, 5))
  }

  test("exponentialJittered") {
    forAll { seed: Long =>
      val rng = Rng(seed)
      val backoffs = Backoff.exponentialJittered(5.millis, 120.millis, rng)
        .take(10).force.toSeq.map(_.inMillis)

      // 5, then randos up to: 10, 20, 40, 80, 120, 120, 120...
      assert(5 == backoffs.head)
      val maxBackoffs = Seq(10, 20, 40, 80, 120, 120, 120, 120, 120)
      backoffs.tail.zip(maxBackoffs)
        .foreach { case (b, m) => assert(b <= m) }
    }
  }

  private[this] val decorrelatedGen = for {
    startMs <- Gen.choose(1L, 1000L)
    maxMs <- Gen.choose(startMs, startMs * 2)
    seed <- Gen.choose(Long.MinValue, Long.MaxValue)
  } yield (startMs, maxMs, seed)

  test("decorrelatedJittered") {
    forAll(decorrelatedGen) { case (startMs: Long, maxMs: Long, seed: Long) =>
      val rng = Rng(seed)
      val backoffs = Backoff.decorrelatedJittered(startMs.millis, maxMs.millis, rng)
        .take(10).force.toSeq

      // 5ms and then randos between 5ms and 3x the previous value (capped at `maximum`)
      assert(startMs.millis == backoffs.head)
      var prev = startMs.millis
      backoffs.tail.foreach { b =>
        assert(b >= startMs.millis)
        assert(b <= prev * 3)
        assert(b <= maxMs.millis)
        prev = b
      }
    }
  }

  test("linear") {
    val backoffs = Backoff.linear(2.seconds, 10.seconds) take 10
    assert(backoffs.head === 2.seconds)
    assert(backoffs.tail.force.toSeq === (1 until 10 map { i => 2.seconds + 10.seconds * i }))
  }

  test("linear with maximum") {
    val backoffs = Backoff.linear(1.millis, 2.millis, 6.millis).take(5)
    assert(backoffs.force.toSeq.map(_.inMillis) == Seq(1, 3, 5, 6, 6))
  }

  test("const") {
    val backoffs = Backoff.const(10.seconds) take 10
    assert(backoffs.force.toSeq === (0 until 10 map { _ => 10.seconds}))
  }

}

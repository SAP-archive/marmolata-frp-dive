import com.sap.marmolata.react.api.cats.FilterableSyntax
import com.sap.marmolata.react.api.{ReactiveLibrary, ReactiveLibraryUsage}
import com.sap.marmolata.react.react.core.{ReactiveLibrary, ReactiveLibraryUsage}

/**
  *
  * == Overview ==
  * This library provides reactive programming primitives to be able to do reactive functional programming.
  *
  * == Usage ==
  * To use this library, include the following import statements in any file:
  *
  * {{{
  *   import reactive.library._
  *   import reactive.library.syntax._
  * }}}
  *
  * The central concepts of this library are [[ReactiveLibrary#Signal Signal]] and [[ReactiveLibrary#Event Event]]
  *
  * === Signal ===
  * A signal is a time-varying value, i.e. for every point in time, a Signal[A] has a value of type A.
  * Signals can represent any kind of values and can be composed. In the context of marmolata,
  * a Signal may represent e.g. the current value of an input field or the currently displayed sql query in a table.
  *
  * Signals can be created with the [[ReactiveLibrary#Var Var]] and [[ReactiveLibrary.SignalCompanionObject#Const Signal.Const]] constructors.
  * Var creates a reactive variable
  * that can be changed by the [[ReactiveLibrary.VarTrait#:= :=]] function, while Const creates a Signal that's never changed.
  *
  * === Example ===
  *
  * {{{
  *   import reactive.library._
  *   import reactive.library.syntax._
  *
  *   val var1 = Var(5)
  *   var1.observe(x => println(s"new value of var1: x"))
  *   > new value of var1: 5
  *
  *   var1 := 10
  *   > new value of var1: 10
  *
  *   var1 := 15
  *   > new value of var1: 15
  * }}}
  *
  * As you can see here, [[ReactiveLibrary.Observable#observe observe]] can be used to do side effects whenever the value of a signal changes.
  * Note, that it should be avoid to use observe and instead build new Signals and Events out of older ones via methods like
  * [[http://typelevel.org/cats/api/index.html#cats.Functor$$Ops@map[B](f:A=>B):F[B] map]],
  * [[http://typelevel.org/cats/api/index.html#cats.Cartesian$$Ops@product[B](fb:F[B]):F[(A,B)] product]],
  * [[http://typelevel.org/cats/api/index.html#cats.Cartesian$$Ops@product[B](fb:F[B]):F[(A,B)] map2]],
  * [[ReactiveLibraryUsage#SignalExtensions#triggerWhen[B](e:ReactiveLibraryUsage\.this\.Event[B]):ReactiveLibraryUsage\.this\.Event[A]* triggerWhen]],
  * [[ReactiveLibraryUsage#SignalExtensions#changeWhen changeWhen]]
  * and give these Signals back to the Marmolata platform.
  *
  * {{{
  *   import reactive.library._
  *   import reactive.library.signal._
  *
  *   val v1 = Var(5)
  *   val v2 = Var(7)
  *   // combine v1 and v2 and build a tuple
  *   val v: Signal[(Int, Int)] = v1 product v2
  *   v.observe(x => println(s"v now has value dollarx")
  *   > v now has value (5, 7)
  *   v1 := 10
  *   > v now has value (10, 7)
  *   v2 := 17
  *   > v now has value (10, 17)
  *   v2 := 17
  *   v2 := 20
  *   > v now has value (10, 20)
  *
  *   val w = v1.map(_ * 10)
  *   w.observe(x => println("w now has value dollarx"))
  *   > w now has value 10
  *   v := 7
  *   > v now has value (7, 20)
  *   > w now has value 14
  * }}}
  *
  * By using these primitives, it's ensured that Signals don't get updated to intermediate values. Consider the following example:
  *
  * {{{
  *   import reactive.library._
  *   import reactive.library.signal._
  *
  *   val v = Var(0)
  *   val w = v.map(_ + 3)
  *   val z = v.map(2 * _)
  *   val r = w.map2(z)((x, y) => x + y)
  *
  *   r.observe(x => s"r now has value dollarx")
  *   > r now has value 3
  *   v := 2
  *   > r now has value 9
  *   v := 10
  *   > r now has value 33
  * }}}
  *
  * Note, that v, w, z and r get updated atomically. So, there's no intermediate state when w is already updated but z isn't yet updated.
  *
  * === Events ===
  * [[ReactiveLibrary#Event Event[A] ]] represent the entirety of specific points in time when some event happens.
  * This could e.g. be the event representing Button clicks or the event representing tablre reloads. An event can have associated data, e.g.
  * the mouse position of a Button click or the associated data of a table reload. But often, scala.Unit is used.
  *
  * Events can be created with the [[ReactiveLibrary.EventSourceCompanionObject#apply EventSource constructor]] or created from other events by methods like
  * [[FilterableSyntax.MergeableObs#merge merge]], [[FilterableSyntax.FilterableObs#filter filter]],
  * [[http://typelevel.org/cats/api/index.html#cats.Functor$$Ops@map[B](f:A=>B):F[B] map]],
  * [[FilterableSyntax.FilterableObs#mapPartial mapPartial]],
  * [[ReactiveLibraryUsage#SignalExtensions#toEvent toEvent]],
  * [[ReactiveLibraryUsage#SignalExtensions#triggerWhen[B](e:ReactiveLibraryUsage\.this\.Event[B]):ReactiveLibraryUsage\.this\.Event[A]* triggerWhen]],
  * [[ReactiveLibraryUsage#EventExtensions#mergeEither mergeEither]].
  *
  * === Example ===
  *
  * {{{
  *   import reactive.library._
  *   import reactive.library.syntax._
  *
  *   val e = EventSource[Int]
  *   val f = EventSource[Int]
  *   val eMergeFilteredF = e merge f.filter(_ % 2 == 0)
  *   val eMergeFilteredFSignal = eMergeFilteredF.toSignal(0)
  *
  *   e.observe(x => "e is triggered: dollarx")
  *   f.observe(x => "f is triggered: dollarx")
  *   eMergeFilteredF.observe(x => "eMergeFilteredF is triggered: dollarx")
  *   eMergeFilteredFSignal.observe(x => "eMergeFilteredFSignal changed to: dollarx")
  *   > eMergeFilteredFSignal changed to: 0
  *
  *   e emit 10
  *   > e is triggered: 10
  *   > eMergeFilteredF is triggered: 10
  *   > eMergeFileredFSignal is changed to: 10
  *
  *   f emit 5
  *   > f is triggered: 5
  *
  *   f emit 20
  *   > f is triggered: 20
  *   > eMergeFilteredF is triggered: 20
  *   > eMergeFilteredFSignal is changed to: 20
  *
  *   e emit 20
  *   > e is triggered: 20
  *   > eMergeFilteredF is triggered: 20
  *
  * }}}
  *
  */
package object react {}

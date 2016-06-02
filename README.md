Reactive Library For Scala and Scala.js
====

This library provides reactive programming primitives. Currently, this gives the programmer an API with a clear semantics which is then wrapped to other reactive libraries. Currently, the scalarx backend is the best provided backend. An other possibility is the metarx backend. The monix backend is only a proof-of-concept and doesn't work at all.

Usage
-----
You can look at the tests (core/shared/src/test/scala/ReactiveLibraryTests.scala) for easy usages.

This library provides the basic concepts `Event` and `Signal` as described in Scala.React.

- Signal[A] is a time-varying value, i.e. it can be seen as a function Time -> Signal which is only changed at discrete times (We don't support continuously changed Signals). 
- Event[A] is a function `f: Time -> Option[A]` which produces signals at distinct times (i.e. for every finite interval I, f^(-1)({Some(x) | x in A}) subset I is finite. Producing multiple events at a single point in time is probably undefined behaviour (is this even possible?)

We can use functions like `map`, `produce` to generate new Signals and Events out of old ones. When we want to generate side effects, `observe` should be used (please don't use map when doing a side effect, it's not guaranteed to be executed only once!).

Influences
----------

This project was heavily influenced by the following projects and papers:

- [MetaRx](https://github.com/MetaStack-pl/MetaRx): The reactive library we used previously. This provides a delta-based implementation to reactive primitives which e.g. gives support for efficient maps because only the changes are propagated (I guess). This isn't really our use-case though (and structural sharing of Maps can still be used even without the reactive library supporting it). Indeed, we had problems with unclear separation of events and signals, ReadStateChannel always becoming a ReadChannel and no variance annotations at either ReadChannel/ReadStateChannel etc.

- [Scala.Rx](https://github.com/lihaoyi/scala.rx): This project only provides Signals and no Events. Otherwise, it looks quite good. In this library, we go through some length to simulate Events with these Signals. This project also handles Space-leak problems which arise naturally when using flatMap by macro-expressions which keep track of owners of Signals and destroy them in the right place. This has not been done in this length by this project.

- [Deprecating the Observer Pattern with Scala.React](https://infoscience.epfl.ch/record/176887/files/DeprecatingObservers2012.pdf): This paper was the motivation for this library. A clear separation between Events and Signals was adopted. The imperative notion of how to generate these Events/Signals also looks promising, but has not yet been incorporated. It may be possible to do this with monads (i.e. for-notation) instead of CPS.

- [Elm: Concurrent FRP for Functional GUIs](http://elm-lang.org/papers/concurrent-frp.pdf): This paper was further evidence that it's a bad idea to provide a monadic interface for Signals/Events. The space leaks that arise when using it wrongly (i.e. nearly every use case) are thus avoided. Instead of Scala.Rx's approach to take care of these space leaks by special macros, we thus take care of it by simply not allowing it. Elm goes to even more length with this approach by only allowing Signals to be generated statically, i.e. at compile time. The whole graph of Events is thus available at compile time and can be optimized etc. We don't go this far with our approach - it's also probably also not possible easily with Scala - but see this as the best use case to produce Signals/Events early on and don't change them anymore afterwards.

- [Cats](http://typelevel.org/cats/): Although we decided not to expose a monadic interface for Signals, Signals are still Functors and Applicatives naturally, so it was useful to use cats, a library which brings these concepts to Scala. By this, we can use functions like `map` and `product` on Signals and at least `map` on Events (Events are currently also Applicatives, but probably they shouldn't be). 

ReactiveComparisions
=

This project started after a handful of libraries was looked at and it was decided that none of them exactly fits our needs. For historical reasons, the comparision is shown here:

Comparision of Reactive Scala.js Libraries

|                          | MetaRx                                                           | Scala.React                    | monix                                                                                                                                        | Scala.Rx                                                                                                                                                   | RxScala                    | Binding.scala                                              | Widok         |
| ------------------------ | -------------------------------------                            | -------------------            | --------------                                                                                                                               | ----------                                                                                                                                                 | --------                   | -------                                                    | ----          |
| maintainence             | unclear                                                          | none, hard to install(globals) | probably good, Typesafe incubator, versions 1.2 and 2.0                                                                                      | okayish, written by Li Haoyi (author of Scala.js) but maintainence has gone there, so lacking, e.g. no zip (no real problem to add it)                     | not available for Scala.js | Thouhtworks, Yang Bo, pretty much chinese information only |               |
| performance              | works for now                                                    | probably good                  | unknown                                                                                                                                      |                                                                                                                                                            |                            | unknown, full featured with html etc.                      | full featured |
| event, signal            | no variance, always returns events instead of signals            | good                           | only events, back preassure, zip, flatMap etc. have different semantics, but can use e.g. combineLatest, don't seem to use graph correctness | only signals, like in paper, don't have to use monads (for syntax), Rx's can be called multiple times (so have to be pure), does proper exception handling |                            |                                                            |               |
| DataFlow (imperativity)  | no                                                               | good                           |                                                                                                                                              | no                                                                                                                                                         |                            |                                                            |               |
| Reactive (Observable)    | by attaching functions                                           | good                           |                                                                                                                                              | yes                                                                                                                                                        |                            |                                                            |               |
| disposable               | leaks memory when not calling dispose for every map/flatMap etc. | good                           |                                                                                                                                              | yes                                                                                                                                                        |                            |                                                            |               |
| documentation            | could be better                                                  | by a paper (good)              | has api documentation, work in progress                                                                                                      | by owners, garbage collection                                                                                                                              |                            |                                                            |               |

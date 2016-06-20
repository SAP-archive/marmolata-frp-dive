package object reactive extends react.ReactiveObject {
  val library: react.ReactiveDeclaration = new reactive.selfrx.SelfRxImpl with react.ReactiveDeclaration
}
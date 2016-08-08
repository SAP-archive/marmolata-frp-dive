package object reactive extends react.core.ReactiveObject {
  val library: react.core.ReactiveDeclaration = new react.impls.selfrx.SelfRxImpl with react.core.ReactiveDeclaration
}
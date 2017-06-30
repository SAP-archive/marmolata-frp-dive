package object reactive extends com.sap.marmolata.react.react.core.ReactiveObject {
  val library: com.sap.marmolata.react.react.core.ReactiveDeclaration = new com.sap.marmolata.react.react.impls.selfrx.SelfRxImpl with com.sap.marmolata.react.react.core.ReactiveDeclaration
}
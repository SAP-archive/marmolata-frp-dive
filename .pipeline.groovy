def dockerImage = "${env.DOCKER_SCALA_OPENJDK}"
def dockerHome = "${env.DOCKER_DEFAULT_WS}"

[
  build: { script ->
    script.executeDocker(dockerImage: dockerImage, dockerWorkspace: dockerHome) {
      script.sh 'sbt -no-colors clean scalastyle compile doc'
    }
  },

  test: { script ->
    script.executeDocker(dockerImage: dockerImage, dockerWorkspace: dockerHome) {
      script.sh """
        sbt -no-colors coverage scalarxJVM/test scalarxJVM/coverageReport
        sbt -no-colors coverage selfrxJVM/test selfrxJVM/coverageReport
        sbt -no-colors metarxJVM/test:compile
        sbt -no-colors metarxJS/test:compile
        sbt -no-colors coverage metarxJVM/test metarxJVM/coverageReport || true
        sbt -no-colors coverageReport
        sbt -no-colors coverageAggregate
        sbt -no-colors scalarxJS/test selfrxJS/test
      """
    }
  }
]


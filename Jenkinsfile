#!/usr/bin/env groovy
@Library('pipeline-lib') _

def pipelineRepo = 'git@github.wdf.sap.corp:Marmolata/Marmolata.Pipelines.git'
node {
    checkout([$class: 'GitSCM', branches: [[name: 'master']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[url: pipelineRepo]]])
    load 'marmolata-pipeline.groovy'
}

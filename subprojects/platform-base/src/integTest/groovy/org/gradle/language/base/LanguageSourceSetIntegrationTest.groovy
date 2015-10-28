/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.language.base

import org.gradle.api.reporting.model.ModelReportOutput
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class LanguageSourceSetIntegrationTest extends AbstractIntegrationSpec {

    def "can not create a top level LSS when the language base plugin has not been applied"() {
        buildFile.text = """
        class Rules extends RuleSource {
            @Model
            void lss(JavaSourceSet javaSource) {
            }
        }
        apply plugin: Rules
        """

        when:
        fails "model"

        then:
        failureCauseContains("Declaration of model rule Rules#lss is invalid.")
        failureCauseContains("A model element of type: 'org.gradle.language.java.JavaSourceSet' can not be constructed.")
    }

    def "can create a top level LSS with a rule"() {
        buildScript """
        apply plugin: 'java-lang'

        ${addPrintSourceDirTask()}

        class Rules extends RuleSource {
            @Model
            void lss(JavaSourceSet javaSource) {
                javaSource.source.srcDir("src/main/lss")
            }
        }
        apply plugin: Rules
        """

        expect:
        succeeds("model", "printSourceDirs")
        output.contains("src/main/lss")
    }

    def "can create a top level LSS via the model DSL"() {
        buildFile.text = """
        apply plugin: 'java-lang'

        model {
            lss(JavaSourceSet)
        }
        """

        when:
        succeeds "model"

        then:
        def modelNode = ModelReportOutput.from(output).modelNode
        modelNode.lss.@creator[0] == "model.lss @ build.gradle line 5, column 13"
        modelNode.lss.@type[0] == "org.gradle.language.java.JavaSourceSet"
        modelNode.lss.@nodeValue[0] == "Java source 'lss:lss'"
    }


    def "can create a LSS as property of a managed type"() {
        buildFile << """
        apply plugin: 'java-lang'

        @Managed
        interface BuildType {
            //Readonly
            JavaSourceSet getSources()

            //Read/write
            JavaSourceSet getInputs()
            void setInputs(JavaSourceSet sources)
        }

        class Rules extends RuleSource {
            @Model
            void buildType(BuildType buildType) { }
        }

        apply plugin: Rules
        """

        expect:
        succeeds "model"
        def buildType = ModelReportOutput.from(output).modelNode.buildType

        buildType.inputs.@type[0] == 'org.gradle.language.java.JavaSourceSet'
        buildType.inputs.@nodeValue[0] == "Java source 'inputs:inputs'"
        buildType.inputs.@creator[0] == 'Rules#buildType'

        buildType.sources.@type[0] == 'org.gradle.language.java.JavaSourceSet'
        buildType.sources.@nodeValue[0] == "Java source 'sources:sources'"
        buildType.sources.@creator[0] == 'Rules#buildType'
    }

    def "An LSS can be an element of managed collections"() {
        buildFile << """
        apply plugin: 'java-lang'

        @Managed
        interface BuildType {
            ModelMap<JavaSourceSet> getComponentSources()
            ModelSet<JavaSourceSet> getTestSources()
        }

        class Rules extends RuleSource {
            @Model
            void buildType(BuildType buildType) { }

            @Mutate
            void addSources(BuildType buildType){
                buildType.componentSources.create("componentA")
                buildType.testSources.create({})
            }
        }

        apply plugin: Rules
        """

        expect:
        succeeds "model"
        def buildType = ModelReportOutput.from(output).modelNode.buildType

        buildType.componentSources.@type[0] == 'org.gradle.model.ModelMap<org.gradle.language.java.JavaSourceSet>'
        buildType.componentSources.@creator[0] == 'Rules#buildType'
        buildType.componentSources.componentA.@type[0] == 'org.gradle.language.java.JavaSourceSet'
        buildType.componentSources.componentA.@nodeValue[0] == "Java source 'componentA:componentA'"
        buildType.componentSources.componentA.@creator[0] == 'Rules#addSources > create(componentA)'

        buildType.testSources.@type[0] == 'org.gradle.model.ModelSet<org.gradle.language.java.JavaSourceSet>'
        buildType.testSources.@creator[0] == 'Rules#buildType'
        buildType.testSources."0".@type[0] == 'org.gradle.language.java.JavaSourceSet'
        buildType.testSources."0".@nodeValue[0] == "Java source '0:0'"
        buildType.testSources."0".@creator[0] == 'Rules#addSources > create()'
    }


    private String addPrintSourceDirTask() {
        """
class PrintSourceDirectoryRules extends RuleSource {
    @Mutate void printTask(ModelMap<Task> tasks, LanguageSourceSet lss) {
        tasks.create("printSourceDirs") {
          doLast {
            println ("source dirs: \${lss.source.getSrcDirs()}")
          }
      }
    }
}
apply plugin: PrintSourceDirectoryRules
"""
    }

}

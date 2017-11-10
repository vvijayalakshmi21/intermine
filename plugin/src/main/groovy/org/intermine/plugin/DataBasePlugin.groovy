package org.intermine.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.util.PatternSet

class DataBasePlugin implements Plugin<Project> {
    // TODO pass these into plugin
    String bioVersion = "2.0.0-SNAPSHOT"
    String antVersion = "2.0.0-SNAPSHOT"
    DBConfig config;
    String buildResourcesMainDir
    SourceSetContainer sourceSets
    public final static String TASK_GROUP = "InterMine"
    void apply(Project project) {

      project.task('initConfig') {
        config = project.extensions.create('dbConfig', DBConfig)
        sourceSets = (SourceSetContainer) project.getProperties().get("sourceSets");
        buildResourcesMainDir = sourceSets.getByName("main").getOutput().resourcesDir;

      }

        project.configurations {
            bioCore
            mergeSource
            imtasks
        }

        project.dependencies {
            bioCore group : "org.intermine", name: "bio-core", version: bioVersion, transitive: false
            mergeSource group : "org.intermine", name: "ant-tasks", version: antVersion
            imtasks group: "org.intermine", name: "intermine-im-tasks", version: bioVersion
        }

      project.task('copyDefaultProperties') {
            description "Copies default.intermine.integrate.properties file into resources output"
            dependsOn 'initConfig', 'processResources'

            doLast {
                FileTree fileTree = project.zipTree(project.configurations.getByName("commonResources").singleFile)
                PatternSet patternSet = new PatternSet();
                patternSet.include("default.intermine.integrate.properties");
                File file = fileTree.matching(patternSet).singleFile
                String defaultIMProperties = buildResourcesMainDir + File.separator + "default.intermine.properties"
                file.renameTo(defaultIMProperties)
                file.createNewFile()
            }
        }

        project.task('copyGenomicModel') {
            dependsOn 'initConfig', 'processResources'

            doLast {
                FileTree fileTree = project.zipTree(project.configurations.getByName("bioCore").singleFile)
                PatternSet patternSet = new PatternSet();
                patternSet.include("core.xml");
                File file = fileTree.matching(patternSet).singleFile
                String modelFilePath = buildResourcesMainDir + File.separator + config.modelName + "_model.xml"
                file.renameTo(modelFilePath)
                file.createNewFile()
            }
        }

        project.task('createSoModel') {
            group TASK_GROUP
            description "Reads SO OBO files and writes so_additions.xml"
            dependsOn 'initConfig', 'processResources'

            doLast {
                def ant = new AntBuilder()
                ant.taskdef(name: "createSoModel", classname: "org.intermine.bio.task.SOToModelTask") {
                    classpath {
                        pathelement(path: project.configurations.getByName("so").asPath)
                    }
                }
                ant.createSoModel(soTermListFile: config.soTermListFilePath, outputFile: config.soAdditionFilePath)
            }
        }

        project.task('mergeModels') {
            group TASK_GROUP
            description "Merges defferent source model files into an intermine XML model"
            dependsOn 'initConfig', 'copyGenomicModel', 'copyModelProperties', 'createSoModel'

            doLast {
                def ant = new AntBuilder()
                String projectXmlFilePath = project.getParent().getProjectDir().getAbsolutePath() + File.separator +  "project.xml"
                String modelFilePath = buildResourcesMainDir + File.separator + config.modelName + "_model.xml"
                ant.taskdef(name: "mergeSourceModels", classname: "org.intermine.task.MergeSourceModelsTask") {
                    classpath {
                        pathelement(path: project.configurations.getByName("mergeSource").asPath)
                        dirset(dir: project.getBuildDir().getAbsolutePath())
                    }
                }
                ant.mergeSourceModels(projectXmlPath: projectXmlFilePath,
                        modelFilePath: modelFilePath,
                        extraModelsStart: config.extraModelsStart,
                        extraModelsEnd: config.extraModelsEnd)

                def obj = new org.intermine.task.project.ProjectXmlBinding()


            }



            //obj.doSomething()

            //def projectXmlBinding = new org.intermine.task.project.ProjectXmlBinding()
//            Project imProject = ProjectXmlBinding().unmarshall(projectXmlFilePath);
//
//            Collection<Source> sources = imProject.getSources().values();

//            for (Source source: sources) {
//                  project.dependencies.add("mergeSource", [group: "org.intermine", name: source.getType(), version: bioVersion])
//            }

//            project.dependencies.add("mergeSource", [group: "org.intermine", name: "uniprot", version: bioVersion])
//            project.dependencies.add("mergeSource", [group: "org.intermine", name: "fasta", version: bioVersion])
//            project.dependencies.add("mergeSource", [group: "org.intermine", name: "go-annotation", version: bioVersion])
        }

        project.task('generateModel') {
            group TASK_GROUP
            description "Merges defferent source model files into an intermine XML model"
            dependsOn 'initConfig', 'mergeModels'

            doLast {
                def ant = new AntBuilder()

                String destination = sourceSets.getByName("main").getJava().srcDirs
                ant.taskdef(name: "modelOutputTask", classname: "org.intermine.task.ModelOutputTask") {
                    classpath {
                        pathelement(path: project.configurations.getByName("compile").asPath)
                        dirset(dir: project.getBuildDir().getAbsolutePath())
                    }
                }
                ant.modelOutputTask(model: config.modelName, destDir: destination, type: "java")
            }
        }

        project.getTasks().getByName("compileJava").dependsOn(project.getTasks().getByName("generateModel"))

        project.task('buildDB') {
            group TASK_GROUP
            description "Build the database for the webapp"
            dependsOn 'initConfig', 'copyDefaultProperties', 'jar'

            doLast {

                def ant = new AntBuilder()

                //create schema file
                String schemaFile = config.objectStoreName + "-schema.xml"
                String destination = project.getBuildDir().getAbsolutePath() + File.separator + schemaFile
                ant.taskdef(name: "torque", classname: "org.intermine.objectstore.intermine.TorqueModelOutputTask") {
                    classpath {
                        dirset(dir: project.getBuildDir().getAbsolutePath())
                        pathelement(path: project.configurations.getByName("compile").asPath)
                    }
                }
                ant.torque(osname: config.objectStoreName, destFile:destination)

                //create db tables
                String tempDirectory = project.getBuildDir().getAbsolutePath() + File.separator + "tmp"
                ant.taskdef(name: "buildDB", classname: "org.intermine.task.BuildDbTask") {
                    classpath {
                        pathelement(path: project.buildDir.getAbsolutePath())//to read the schema
                        pathelement(path: project.getBuildDir().getAbsolutePath() + File.separator + "libs" + File.separator + "dbmodel.jar")
                        pathelement(path: project.configurations.getByName("compile").asPath)
                    }
                }
                ant.buildDB(osname: config.objectStoreName, model: config.modelName,
                        schemafile: schemaFile, tempDir: tempDirectory)

                //store metadata into db
                ant.taskdef(name: 'insertModel', classname: 'org.intermine.task.StoreMetadataTask') {
                    classpath {
                        pathelement(path: project.configurations.getByName("compile").asPath)
                        dirset(dir: buildResourcesMainDir) // intermine.properties
                    }
                }
                ant.insertModel(osname: config.objectStoreName, modelName: config.modelName)
            }
        }
    }
}


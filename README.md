<div align="center">

<img src="https://i.imgur.com/LvJD97T.png" alt="logo" style="width:25%">

# Ymir

<div align="center" >
  <img align="center" src="https://img.shields.io/badge/version-1.0.0 (abcdef)-blue">
  <img align="center" src="https://img.shields.io/badge/minecraft-forge 1.7.10-blue">
  <img align="center" src="https://img.shields.io/badge/maintainer-Zeldown-orange">
  <img align="center" src="https://img.shields.io/maintenance/yes/9999">
</div>

<br>

Introducing Ymir
<br><br>
Minecraft Forge World Managment

[Gradle](#gradle) • [Build](#build) • [Publish](#publish)

</div>

## Gradle

To include Ymir in your project using Gradle, add the following dependency to your `build.gradle` file:

```gradle
repositories {
    maven {
        name = "forge"
        url = "http://repository.palagitium.dev/artifactory/Paladium-Dev"
        credentials {
            username = getRepositoryUser()
            password = getRepositoryPassword()
        }
    }
}

def getRepositoryUser() {
    if(System.getenv('MAVEN_REPO_USER') != null) {
        return System.getenv('MAVEN_REPO_USER')
    }else {
        return project.findProperty('MAVEN_REPO_USER')
    }

    return ""
}

def getRepositoryPassword() {
    if(System.getenv('MAVEN_REPO_PASS') != null) {
        return System.getenv('MAVEN_REPO_PASS')
    }else {
        return project.findProperty('MAVEN_REPO_PASS')
    }

    return ""
}

configurations.all {
   resolutionStrategy {
   	cacheDynamicVersionsFor 60, 'seconds'
   }
}

dependencies {
    compile "fr.paladium:ymir:+:dev"
}
```


## Build

```sh
gradlew build         # Build le projet dans le dossier Ymir/build/libs.
```

## Publish

```sh
gradlew publish       # Met en ligne le projet sur repository.palagitium.dev
```